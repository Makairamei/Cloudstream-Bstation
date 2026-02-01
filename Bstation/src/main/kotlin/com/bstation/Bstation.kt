package com.bstation

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element

@Suppress("DEPRECATION")
class Bstation : MainAPI() {
    override var mainUrl = "https://www.bilibili.tv"
    override var name = "Bstation"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private val apiUrl = "https://api.bilibili.tv"

    private val cookies = mapOf(
        "SESSDATA" to "77adc14d%2C1784135329%2C49214%2A110091",
        "bili_jct" to "b9cd1b814e7484becba8917728142c21",
        "DedeUserID" to "1709563281",
        "buvid3" to "1d09ce3a-0767-40d7-b74a-cb7be2294d8064620infoc",
        "buvid4" to "EDD5D20E-2881-5FC4-ACF3-38407A33613880170-026011701-uQai4h5eTsQ9YIdcmk0IhA%3D%3D",
        "bstar-web-lang" to "id"
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36",
        "Referer" to "https://www.bilibili.tv/",
        "Origin" to "https://www.bilibili.tv"
    )

    override val mainPage = mainPageOf(
        "anime" to "Anime",
        "trending" to "Trending"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Fallback to HTML parsing as API is unstable
        val targetUrl = if(request.data == "trending") "$mainUrl/id/trending" else "$mainUrl/id/anime"
        
        val document = app.get(targetUrl, headers = headers, cookies = cookies).document
        
        val items = document.select("a[href*='/play/']").mapNotNull { element ->
             val href = element.attr("href")
             // Extract ID from /play/2288932 or /play/2288932?param=...
             val id = href.substringAfter("/play/").substringBefore("?").filter { it.isDigit() }
             if (id.isEmpty()) return@mapNotNull null
             
             // Extract Title
             var title = element.text()
             if (title.isBlank()) {
                 title = element.select("h3, p, div[class*='title']").text()
             }
             if (title.isBlank()) title = "Unknown Title"

             // Extract Poster
             var poster = element.select("img").attr("src")
             if (poster.isEmpty()) poster = element.select("img").attr("data-src")
             if (poster.isEmpty()) poster = element.select("img").attr("alt") // sometimes src is blocked, fallback? No.
             
             newAnimeSearchResponse(title, id, TvType.Anime) {
                 this.posterUrl = poster
             }
        }.distinctBy { it.url } // Dedup

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Try V2 Search API
        val url = "$apiUrl/intl/gateway/web/v2/search_result?keyword=$query&s_locale=id_ID&limit=20"
        return try {
            val res = app.get(url, headers = headers, cookies = cookies).parsedSafe<SearchResult>()
            res?.data?.modules?.flatMap { module ->
                module.data?.items?.mapNotNull { item ->
                    val title = item.title ?: return@mapNotNull null
                    val id = item.seasonId ?: return@mapNotNull null
                    newAnimeSearchResponse(title, id, TvType.Anime) {
                        this.posterUrl = item.cover
                    }
                } ?: emptyList()
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val seasonId = url.substringAfterLast("/").filter { it.isDigit() }.ifEmpty { url }
        val seasonApiUrl = "$apiUrl/intl/gateway/v2/ogv/view/app/season?season_id=$seasonId&platform=web&s_locale=id_ID"
        val res = app.get(seasonApiUrl, headers = headers, cookies = cookies).parsedSafe<SeasonResult>()
            ?: throw ErrorLoadingException("Failed to load")

        val result = res.result ?: throw ErrorLoadingException("No result")
        val title = result.title ?: "Unknown"
        val poster = result.cover
        val description = result.evaluate

        val episodes = mutableListOf<Episode>()
        
        result.modules?.forEach { module ->
            module.data?.episodes?.forEach { ep ->
                episodes.add(newEpisode(LoadData(ep.id.toString(), seasonId).toJson()) {
                    this.name = ep.title ?: "Episode ${ep.index}"
                    this.episode = ep.index?.toIntOrNull()
                    this.posterUrl = ep.cover
                })
            }
        }

        result.episodes?.forEach { ep ->
            if (episodes.none { parseJson<LoadData>(it.data).epId == ep.id.toString() }) {
                episodes.add(newEpisode(LoadData(ep.id.toString(), seasonId).toJson()) {
                    this.name = ep.title ?: "Episode ${ep.index}"
                    this.episode = ep.index?.toIntOrNull()
                    this.posterUrl = ep.cover
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        val epId = loadData.epId
        
        // FLV Strategy: fnval=0 forces legacy format with merged audio+video
        val playUrl = "$apiUrl/intl/gateway/v2/ogv/playurl?ep_id=$epId&platform=web&qn=80&fnval=0&s_locale=id_ID"
        val res = app.get(playUrl, headers = headers, cookies = cookies).parsedSafe<PlayResult>()
        val playResult = res?.result ?: res?.data ?: return false

        // PRIORITY 1: Handle legacy durl format (Merged Audio+Video)
        playResult.durl?.forEach { durl ->
            val videoUrl = durl.url ?: return@forEach
            callback.invoke(
                newExtractorLink(this.name, "$name MP4", videoUrl, INFER_TYPE) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P720.value
                }
            )
        }

        // PRIORITY 2: Handle DASH video streams (Fallback, may lack audio)
        val streams = playResult.videoInfo?.streamList ?: playResult.dash?.video ?: emptyList()
        
        streams.forEach { stream ->
            val quality = stream.streamInfo?.displayDesc ?: "${stream.height ?: 0}p"
            val videoUrl = stream.dashVideo?.baseUrl ?: stream.baseUrl ?: return@forEach
            
            callback.invoke(
                newExtractorLink(this.name, "$name $quality (No Audio)", videoUrl, INFER_TYPE) {
                    this.referer = "$mainUrl/"
                    this.quality = getQualityFromName(quality)
                }
            )
        }

        // Fetch subtitles
        try {
            val subApiUrl = "$apiUrl/intl/gateway/v2/ogv/view/app/season?ep_id=$epId&platform=web&s_locale=id_ID"
            val subRes = app.get(subApiUrl, headers = headers, cookies = cookies).parsedSafe<SeasonResult>()
            
            val allEps = mutableListOf<EpisodeData>()
            subRes?.result?.episodes?.let { allEps.addAll(it) }
            subRes?.result?.modules?.forEach { m -> m.data?.episodes?.let { allEps.addAll(it) } }
            
            allEps.find { it.id.toString() == epId }?.subtitles?.forEach { sub ->
                val lang = sub.lang ?: sub.title ?: "Unknown"
                val sUrl = sub.url ?: return@forEach
                subtitleCallback.invoke(SubtitleFile(lang, sUrl))
            }
        } catch (_: Exception) { }

        return true
    }

    data class LoadData(val epId: String, val seasonId: String)

    // Search Response Classes
    data class SearchResult(@JsonProperty("data") val data: SearchData?)
    data class SearchData(@JsonProperty("modules") val modules: List<Module>?)
    data class Module(@JsonProperty("data") val data: ModuleData?)
    data class ModuleData(
        @JsonProperty("items") val items: List<SearchItem>?,
        @JsonProperty("episodes") val episodes: List<EpisodeData>?
    )
    data class SearchItem(
        @JsonProperty("title") val title: String?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("season_id") val seasonId: String?
    )

    // Season Response Classes
    data class SeasonResult(@JsonProperty("result") val result: SeasonData?)
    data class SeasonData(
        @JsonProperty("title") val title: String?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("evaluate") val evaluate: String?,
        @JsonProperty("modules") val modules: List<Module>?,
        @JsonProperty("episodes") val episodes: List<EpisodeData>?
    )
    data class EpisodeData(
        @JsonProperty("id") val id: Long?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("index") val index: String?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("subtitles") val subtitles: List<SubtitleData>?
    )
    data class SubtitleData(
        @JsonProperty("lang") val lang: String?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("url") val url: String?
    )

    // Play Response Classes
    data class PlayResult(
        @JsonProperty("result") val result: PlayData?,
        @JsonProperty("data") val data: PlayData?
    )
    data class PlayData(
        @JsonProperty("video_info") val videoInfo: VideoInfo?,
        @JsonProperty("dash") val dash: DashData?,
        @JsonProperty("durl") val durl: List<DurlData>?
    )
    data class VideoInfo(@JsonProperty("stream_list") val streamList: List<StreamData>?)
    data class DashData(
        @JsonProperty("video") val video: List<StreamData>?,
        @JsonProperty("audio") val audio: List<StreamData>?
    )
    data class StreamData(
        @JsonProperty("base_url") val baseUrl: String?,
        @JsonProperty("dash_video") val dashVideo: BaseUrlData?,
        @JsonProperty("height") val height: Int?,
        @JsonProperty("stream_info") val streamInfo: StreamInfo?
    )
    data class BaseUrlData(@JsonProperty("base_url") val baseUrl: String?)
    data class StreamInfo(@JsonProperty("display_desc") val displayDesc: String?)
    data class DurlData(@JsonProperty("url") val url: String?)
}
