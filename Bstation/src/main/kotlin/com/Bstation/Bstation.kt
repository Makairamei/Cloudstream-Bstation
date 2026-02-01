package com.bstation

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.LoadResponse.Companion.addEpisodes
import org.jsoup.nodes.Element
import java.net.URI

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

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

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
                val id = item.season_id ?: ""
                
                // Construct URL as "https://www.bilibili.tv/en/play/<season_id>"
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
        // Extract Season ID from URL: https://www.bilibili.tv/id/play/1049909 or .../play/<id>
        val seasonId = url.split("/").lastOrNull { it.all { char -> char.isDigit() } } 
            ?: return null
        
        // Fetch Season Details
        val apiUrl = "$SEASON_API?season_id=$seasonId&platform=web&s_locale=id_ID"
        val res = app.get(apiUrl, headers = headers, cookies = cookies).parsedSafe<BstationSeasonResponse>()
            ?: throw ErrorLoadingException("Failed to load season data")

        val result = res.result ?: return null
        val title = result.title ?: "Unknown Title"
        val cover = result.cover
        val desc = result.evaluate
        val cast = result.actor?.map { Actor(it.name ?: "", "") }
        
        val episodes = ArrayList<Episode>()
        
        // Modules usually contain episodes
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
                    // Storing data for loadLinks
                    this.data = epId.toString() 
                })
            }
        }

        // If 'episodes' key is directly in result (sometimes happens)
        result.episodes?.forEach { ep ->
            val epId = ep.id
            val epTitle = ep.title ?: "Episode ${ep.index}"
            val epNum = ep.index?.toIntOrNull()
            val epCover = ep.cover
            
            // Check if not already added
            if (episodes.none { it.data == epId.toString() }) {
                 episodes.add(newEpisode(epId.toString()) {
                    this.name = epTitle
                    this.episode = epNum
                    this.posterUrl = epCover
                    this.data = epId.toString()
                })
            }
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = cover
            this.plot = desc
            this.actors = cast
            addEpisodes(TvType.Anime, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // data is ep_id
        val epId = data
        val playUrl = "$PLAY_API?ep_id=$epId&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID"
        
        val res = app.get(playUrl, headers = headers, cookies = cookies).parsedSafe<BstationPlayResponse>()
        val result = res?.result ?: res?.data // structure varies slightly?
        
        if (result == null) return false

        // Video Streams
        // Check 'dash' or 'video_info' -> 'stream_list'
        val dashVideo = result.video_info?.stream_list ?: result.dash?.video ?: emptyList()
        val dashAudio = result.video_info?.dash_audio ?: result.dash?.audio ?: emptyList()

        dashVideo.forEach { video ->
            val qualityStr = video.stream_info?.display_desc ?: "${video.height}p"
            val qualityInt = when {
                qualityStr.contains("1080") -> Qualities.P1080.value
                qualityStr.contains("720") -> Qualities.P720.value
                qualityStr.contains("480") -> Qualities.P480.value
                qualityStr.contains("360") -> Qualities.P360.value
                else -> Qualities.Unknown.value
            }
            
            // If we have separate audio, we might need to merge or just use MP4 if available.
            // But usually dash requires MPD. Bstation gives raw base_url for m4s/mp4 segments often.
            // Ideally we use text/xml for MPD or simple direct links if it's mp4.
            // Bstation web often sends .m4s or .mp4
            val videoUrl = video.dash_video?.base_url ?: video.base_url ?: ""
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

        // Standard 'durl' for mp4 (if available) - old format
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
        
        // Subtitles
        // Need to use Season API again for subs? Or Metadata?
        // bstation_api.py uses specific logic for subs (Strategy B)
        // Let's implement basic sub fetch if playurl doesn't return it
        // Note: PlayURL usually doesn't return subs in Bilibili Int.
        // We'll trust that load() might have populated subs? No, load() operates on Season.
        
        // Fetch subs for this ep
         try {
            val subUrl = "$SEASON_API?ep_id=$epId&platform=web&s_locale=id_ID"
            val subRes = app.get(subUrl, headers = headers, cookies = cookies).parsedSafe<BstationSeasonResponse>()
            // Find episode in result
            val allEps = ArrayList<BstationEpisode>()
            subRes?.result?.episodes?.let { allEps.addAll(it) }
            subRes?.result?.modules?.forEach { m -> m.data?.items?.let { msg -> /* items? episodes? */ } }
            // Actually reusing the logic from load() regarding modules
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

    // JSON Data Classes
    data class BstationSearchResponse(val data: BstationSearchData?)
    data class BstationSearchData(val modules: List<BstationModule>?)
    data class BstationModule(val data: BstationModuleData?)
    data class BstationModuleData(val items: List<BstationSearchItem>?, val episodes: List<BstationEpisode>?)
    data class BstationSearchItem(val title: String?, val cover: String?, val season_id: String?)

    data class BstationSeasonResponse(val result: BstationSeasonResult?)
    data class BstationSeasonResult(
        val title: String?, 
        val cover: String?, 
        val evaluate: String?, 
        val actor: List<BstationActor>?,
        val modules: List<BstationModule>?,
        val episodes: List<BstationEpisode>?
    )
    data class BstationActor(val name: String?)
    data class BstationEpisode(
        val id: Long?, 
        val title: String?, 
        val index: String?, 
        val cover: String?,
        val subtitles: List<BstationSubtitle>?
    )
    data class BstationSubtitle(val lang: String?, val title: String?, val url: String?)

    data class BstationPlayResponse(val result: BstationPlayResult?, val data: BstationPlayResult?)
    data class BstationPlayResult(
        val video_info: BstationVideoInfo?, 
        val dash: BstationDash?,
        val durl: List<BstationDurl>?
    )
    data class BstationVideoInfo(val stream_list: List<BstationStream>?, val dash_audio: List<BstationStream>?)
    data class BstationDash(val video: List<BstationStream>?, val audio: List<BstationStream>?)
    data class BstationStream(
        val base_url: String?, 
        val dash_video: BstationBaseUrl?, 
        val height: Int?,
        val stream_info: BstationStreamInfo?
    )
    data class BstationBaseUrl(val base_url: String?)
    data class BstationStreamInfo(val display_desc: String?)
    data class BstationDurl(val url: String?)
}
