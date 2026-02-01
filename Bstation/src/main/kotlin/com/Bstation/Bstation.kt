package com.Bstation

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addEpisodes
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class Bstation : MainAPI() {
    override var mainUrl = "https://www.bilibili.tv"
    override var name = "Bstation"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.TvSeries,
        TvType.Movie
    )

    // Hardcoded cookies from user's api_server/bstation_module/cookies.txt
    private val cookies = mapOf(
        "SESSDATA" to "77adc14d%2C1784135329%2C49214%2A110091",
        "bili_jct" to "b9cd1b814e7484becba8917728142c21",
        "DedeUserID" to "1709563281",
        "bstar-web-lang" to "id"
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36",
        "Referer" to "https://www.bilibili.tv/",
        "Origin" to "https://www.bilibili.tv"
    )

    // API Endpoints
    private val SEARCH_API = "https://api.bilibili.tv/intl/gateway/v2/ogv/search/resource"
    private val SEASON_API = "https://api.bilibili.tv/intl/gateway/v2/ogv/view/app/season"
    private val PLAY_API = "https://api.bilibili.tv/intl/gateway/v2/ogv/playurl"

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$SEARCH_API?keyword=$query&s_locale=id_ID&limit=20"
        val res = app.get(url, headers = headers, cookies = cookies).parsedSafe<BstationSearchResponse>()
        
        val results = ArrayList<SearchResponse>()
        res?.data?.modules?.forEach { module ->
            module.data?.items?.forEach { item ->
                val title = item.title ?: "Unknown"
                val cover = item.cover
                val id = item.seasonId ?: ""
                
                val href = "$mainUrl/id/play/$id"
                
                if (id.isNotEmpty()) {
                    results.add(newAnimeSearchResponse(title, href, TvType.Anime) {
                        this.posterUrl = cover
                    })
                }
            }
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val seasonId = url.split("/").lastOrNull { it.all { char -> char.isDigit() } } 
            ?: return null
        
        val apiUrl = "$SEASON_API?season_id=$seasonId&platform=web&s_locale=id_ID"
        val res = app.get(apiUrl, headers = headers, cookies = cookies).parsedSafe<BstationSeasonResponse>()
            ?: throw ErrorLoadingException("Failed to load season data")

        val result = res.result ?: return null
        val title = result.title ?: "Unknown Title"
        val cover = result.cover
        val desc = result.evaluate
        val cast = result.actor?.map { Actor(it.name ?: "", "") }
        
        val episodes = ArrayList<Episode>()
        
        result.modules?.forEach { module ->
            module.data?.episodes?.forEach { ep ->
                val epId = ep.id
                val epTitle = ep.title ?: "Episode ${ep.index}"
                val epNum = ep.index?.toIntOrNull()
                val epCover = ep.cover
                
                episodes.add(newEpisode(epId.toString()) {
                    this.name = epTitle
                    this.episode = epNum
                    this.posterUrl = epCover
                })
            }
        }

        result.episodes?.forEach { ep ->
            val epId = ep.id
            val epTitle = ep.title ?: "Episode ${ep.index}"
            val epNum = ep.index?.toIntOrNull()
            val epCover = ep.cover
            
            if (episodes.none { it.data == epId.toString() }) {
                 episodes.add(newEpisode(epId.toString()) {
                    this.name = epTitle
                    this.episode = epNum
                    this.posterUrl = epCover
                })
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = cover
            this.plot = desc
            this.actors = cast
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val epId = data
        val playUrl = "$PLAY_API?ep_id=$epId&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID"
        
        val res = app.get(playUrl, headers = headers, cookies = cookies).parsedSafe<BstationPlayResponse>()
        val result = res?.result ?: res?.data
        
        if (result == null) return false

        val dashVideo = result.videoInfo?.streamList ?: result.dash?.video ?: emptyList()

        dashVideo.forEach { video ->
            val qualityStr = video.streamInfo?.displayDesc ?: "${video.height}p"
            val qualityInt = when {
                qualityStr.contains("1080") -> Qualities.P1080.value
                qualityStr.contains("720") -> Qualities.P720.value
                qualityStr.contains("480") -> Qualities.P480.value
                qualityStr.contains("360") -> Qualities.P360.value
                else -> Qualities.Unknown.value
            }
            
            val videoUrl = video.dashVideo?.baseUrl ?: video.baseUrl ?: ""
            if (videoUrl.isNotEmpty()) {
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        "Bstation $qualityStr",
                        videoUrl,
                        headers["Referer"] ?: "",
                        qualityInt
                    )
                )
            }
        }

        result.durl?.forEach { durl ->
             callback.invoke(
                ExtractorLink(
                    this.name,
                    "Bstation (Legacy)",
                    durl.url ?: "",
                    headers["Referer"] ?: "",
                    Qualities.Unknown.value
                )
            )
        }
        
        try {
            val subUrl = "$SEASON_API?ep_id=$epId&platform=web&s_locale=id_ID"
            val subRes = app.get(subUrl, headers = headers, cookies = cookies).parsedSafe<BstationSeasonResponse>()
            val allEps = ArrayList<BstationEpisode>()
            subRes?.result?.episodes?.let { allEps.addAll(it) }
            subRes?.result?.modules?.forEach { m -> m.data?.episodes?.let { allEps.addAll(it) } }
            
            val thisEp = allEps.find { it.id.toString() == epId }
            thisEp?.subtitles?.forEach { sub ->
                val lang = sub.lang ?: sub.title ?: "Unknown"
                val sUrl = sub.url ?: return@forEach
               subtitleCallback.invoke(
                   SubtitleFile(lang, sUrl)
               )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return true
    }

    // JSON Data Classes with JsonProperty annotations
    data class BstationSearchResponse(
        @JsonProperty("data") val data: BstationSearchData?
    )
    data class BstationSearchData(
        @JsonProperty("modules") val modules: List<BstationModule>?
    )
    data class BstationModule(
        @JsonProperty("data") val data: BstationModuleData?
    )
    data class BstationModuleData(
        @JsonProperty("items") val items: List<BstationSearchItem>?, 
        @JsonProperty("episodes") val episodes: List<BstationEpisode>?
    )
    data class BstationSearchItem(
        @JsonProperty("title") val title: String?, 
        @JsonProperty("cover") val cover: String?, 
        @JsonProperty("season_id") val seasonId: String?
    )

    data class BstationSeasonResponse(
        @JsonProperty("result") val result: BstationSeasonResult?
    )
    data class BstationSeasonResult(
        @JsonProperty("title") val title: String?, 
        @JsonProperty("cover") val cover: String?, 
        @JsonProperty("evaluate") val evaluate: String?, 
        @JsonProperty("actor") val actor: List<BstationActor>?,
        @JsonProperty("modules") val modules: List<BstationModule>?,
        @JsonProperty("episodes") val episodes: List<BstationEpisode>?
    )
    data class BstationActor(
        @JsonProperty("name") val name: String?
    )
    data class BstationEpisode(
        @JsonProperty("id") val id: Long?, 
        @JsonProperty("title") val title: String?, 
        @JsonProperty("index") val index: String?, 
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("subtitles") val subtitles: List<BstationSubtitle>?
    )
    data class BstationSubtitle(
        @JsonProperty("lang") val lang: String?, 
        @JsonProperty("title") val title: String?, 
        @JsonProperty("url") val url: String?
    )

    data class BstationPlayResponse(
        @JsonProperty("result") val result: BstationPlayResult?, 
        @JsonProperty("data") val data: BstationPlayResult?
    )
    data class BstationPlayResult(
        @JsonProperty("video_info") val videoInfo: BstationVideoInfo?, 
        @JsonProperty("dash") val dash: BstationDash?,
        @JsonProperty("durl") val durl: List<BstationDurl>?
    )
    data class BstationVideoInfo(
        @JsonProperty("stream_list") val streamList: List<BstationStream>?, 
        @JsonProperty("dash_audio") val dashAudio: List<BstationStream>?
    )
    data class BstationDash(
        @JsonProperty("video") val video: List<BstationStream>?, 
        @JsonProperty("audio") val audio: List<BstationStream>?
    )
    data class BstationStream(
        @JsonProperty("base_url") val baseUrl: String?, 
        @JsonProperty("dash_video") val dashVideo: BstationBaseUrl?, 
        @JsonProperty("height") val height: Int?,
        @JsonProperty("stream_info") val streamInfo: BstationStreamInfo?
    )
    data class BstationBaseUrl(
        @JsonProperty("base_url") val baseUrl: String?
    )
    data class BstationStreamInfo(
        @JsonProperty("display_desc") val displayDesc: String?
    )
    data class BstationDurl(
        @JsonProperty("url") val url: String?
    )
}
