package com.bstation

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson

class Bstation : MainAPI() {
    override var mainUrl = "https://www.bilibili.tv"
    override var name = "Bstation"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val apiUrl = "https://api.bilibili.tv"

    private val cookies = mapOf(
        "SESSDATA" to "77adc14d%2C1784135329%2C49214%2A110091",
        "bili_jct" to "b9cd1b814e7484becba8917728142c21",
        "DedeUserID" to "1709563281",
        "bstar-web-lang" to "id"
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Referer" to "https://www.bilibili.tv/"
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/intl/gateway/v2/ogv/search/resource?keyword=$query&s_locale=id_ID&limit=20"
        val res = app.get(url, headers = headers, cookies = cookies).parsedSafe<SearchResult>()
        
        return res?.data?.modules?.flatMap { module ->
            module.data?.items?.mapNotNull { item ->
                val title = item.title ?: return@mapNotNull null
                val id = item.seasonId ?: return@mapNotNull null
                newMovieSearchResponse(title, id, TvType.TvSeries) {
                    this.posterUrl = item.cover
                }
            } ?: emptyList()
        } ?: emptyList()
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

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        val epId = loadData.epId
        
        val playUrl = "$apiUrl/intl/gateway/v2/ogv/playurl?ep_id=$epId&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID"
        val res = app.get(playUrl, headers = headers, cookies = cookies).parsedSafe<PlayResult>()
        val playResult = res?.result ?: res?.data ?: return false

        val streams = playResult.videoInfo?.streamList ?: playResult.dash?.video ?: emptyList()
        
        streams.forEach { stream ->
            val quality = stream.streamInfo?.displayDesc ?: "${stream.height ?: 0}p"
            val videoUrl = stream.dashVideo?.baseUrl ?: stream.baseUrl ?: return@forEach
            
            callback.invoke(
                ExtractorLink(
                    this.name,
                    "$name $quality",
                    videoUrl,
                    "$mainUrl/",
                    getQualityFromName(quality),
                    false
                )
            )
        }

        playResult.durl?.forEach { durl ->
            val videoUrl = durl.url ?: return@forEach
            callback.invoke(
                ExtractorLink(
                    this.name,
                    "$name",
                    videoUrl,
                    "$mainUrl/",
                    Qualities.Unknown.value,
                    false
                )
            )
        }

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
