package com.bstation

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element
import android.util.Base64

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
        val targetUrl = if(request.data == "trending") "$mainUrl/id/trending" else "$mainUrl/id/anime"
        
        val document = app.get(targetUrl, headers = headers, cookies = cookies).document
        
        val items = document.select("a[href*='/play/']").mapNotNull { element ->
             val href = element.attr("href")
             val id = href.substringAfter("/play/").substringBefore("?").filter { it.isDigit() }
             if (id.isEmpty()) return@mapNotNull null
             
             var title = element.text()
             if (title.isBlank()) {
                 title = element.select("h3, p, div[class*='title']").text()
             }
             if (title.isBlank()) title = "Unknown Title"

             var poster = element.select("img").attr("src")
             if (poster.isEmpty()) poster = element.select("img").attr("data-src")
             
             newAnimeSearchResponse(title, id, TvType.Anime) {
                 this.posterUrl = poster
             }
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        val epId = loadData.epId
        
        // Request DASH streams
        val playUrl = "$apiUrl/intl/gateway/v2/ogv/playurl?ep_id=$epId&platform=web&qn=64&s_locale=id_ID"
        val res = app.get(playUrl, headers = headers, cookies = cookies).parsedSafe<PlayResult>()
        val playResult = res?.result ?: res?.data ?: return false

        // Get video streams
        val videoInfo = playResult.videoInfo
        val streamList = videoInfo?.streamList ?: emptyList()
        val dashAudio = videoInfo?.dashAudio ?: emptyList()
        val duration = videoInfo?.timelength ?: 0

        // Get first available audio URL
        val audioUrl = dashAudio.firstOrNull()?.baseUrl

        // Process each quality
        for (stream in streamList) {
            val quality = stream.streamInfo?.displayDesc ?: continue
            val videoUrl = stream.dashVideo?.baseUrl
            if (videoUrl.isNullOrEmpty()) continue

            // If we have both video and audio, create a DASH manifest
            if (audioUrl != null) {
                val manifest = generateDashManifest(videoUrl, audioUrl, duration)
                val manifestDataUri = "data:application/dash+xml;base64," + 
                    Base64.encodeToString(manifest.toByteArray(), Base64.NO_WRAP)
                
                callback.invoke(
                    newExtractorLink(this.name, "$name $quality", manifestDataUri, INFER_TYPE) {
                        this.referer = "$mainUrl/"
                        this.quality = getQualityFromName(quality)
                    }
                )
            } else {
                // Fallback: video only
                callback.invoke(
                    newExtractorLink(this.name, "$name $quality (No Audio)", videoUrl, INFER_TYPE) {
                        this.referer = "$mainUrl/"
                        this.quality = getQualityFromName(quality)
                    }
                )
            }
        }

        // Fetch subtitles
        try {
            val subApiUrl = "$apiUrl/intl/gateway/v2/ogv/view/app/season?ep_id=$epId&platform=web&s_locale=id_ID"
            val subRes = app.get(subApiUrl, headers = headers, cookies = cookies).parsedSafe<SeasonResult>()
            
            val allEps = mutableListOf<EpisodeData>()
            subRes?.result?.episodes?.let { allEps.addAll(it) }
            subRes?.result?.modules?.forEach { m -> m.data?.episodes?.let { allEps.addAll(it) } }
            
            val currentEp = allEps.find { it.id.toString() == epId }
            currentEp?.subtitles?.forEach { sub ->
                val subUrl = sub.url ?: return@forEach
                subtitleCallback.invoke(
                    SubtitleFile(sub.title ?: sub.lang ?: "Unknown", subUrl)
                )
            }
        } catch (_: Exception) {}

        return true
    }

    private fun generateDashManifest(videoUrl: String, audioUrl: String, durationMs: Long): String {
        val durationSec = durationMs / 1000
        val hours = durationSec / 3600
        val minutes = (durationSec % 3600) / 60
        val seconds = durationSec % 60
        val durationStr = "PT${hours}H${minutes}M${seconds}S"

        return """<?xml version="1.0" encoding="UTF-8"?>
<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="$durationStr" minBufferTime="PT1.5S" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011">
  <Period duration="$durationStr">
    <AdaptationSet mimeType="video/mp4" contentType="video">
      <Representation id="video" bandwidth="500000">
        <BaseURL>$videoUrl</BaseURL>
      </Representation>
    </AdaptationSet>
    <AdaptationSet mimeType="audio/mp4" contentType="audio" lang="und">
      <Representation id="audio" bandwidth="128000">
        <BaseURL>$audioUrl</BaseURL>
      </Representation>
    </AdaptationSet>
  </Period>
</MPD>"""
    }

    // Data Classes
    data class LoadData(val epId: String, val seasonId: String)
    
    data class SearchResult(@JsonProperty("data") val data: SearchData?)
    data class SearchData(@JsonProperty("modules") val modules: List<SearchModule>?)
    data class SearchModule(@JsonProperty("data") val data: SearchModuleData?)
    data class SearchModuleData(@JsonProperty("items") val items: List<SearchItem>?)
    data class SearchItem(
        @JsonProperty("title") val title: String?,
        @JsonProperty("season_id") val seasonId: String?,
        @JsonProperty("cover") val cover: String?
    )

    data class SeasonResult(@JsonProperty("result") val result: SeasonData?)
    data class SeasonData(
        @JsonProperty("title") val title: String?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("evaluate") val evaluate: String?,
        @JsonProperty("episodes") val episodes: List<EpisodeData>?,
        @JsonProperty("modules") val modules: List<ModuleData>?
    )
    data class ModuleData(@JsonProperty("data") val data: ModuleEpisodes?)
    data class ModuleEpisodes(@JsonProperty("episodes") val episodes: List<EpisodeData>?)
    data class EpisodeData(
        @JsonProperty("id") val id: Long?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("index_show") val index: String?,
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
    data class VideoInfo(
        @JsonProperty("stream_list") val streamList: List<StreamData>?,
        @JsonProperty("dash_audio") val dashAudio: List<DashAudioData>?,
        @JsonProperty("timelength") val timelength: Long?
    )
    data class DashData(
        @JsonProperty("video") val video: List<StreamData>?,
        @JsonProperty("audio") val audio: List<StreamData>?
    )
    data class StreamData(
        @JsonProperty("base_url") val baseUrl: String?,
        @JsonProperty("dash_video") val dashVideo: DashVideoData?,
        @JsonProperty("height") val height: Int?,
        @JsonProperty("stream_info") val streamInfo: StreamInfo?
    )
    data class DashVideoData(
        @JsonProperty("base_url") val baseUrl: String?,
        @JsonProperty("audio_id") val audioId: Int?
    )
    data class DashAudioData(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("base_url") val baseUrl: String?,
        @JsonProperty("bandwidth") val bandwidth: Int?
    )
    data class StreamInfo(@JsonProperty("display_desc") val displayDesc: String?)
    data class DurlData(@JsonProperty("url") val url: String?)
}
