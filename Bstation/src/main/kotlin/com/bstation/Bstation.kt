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
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    private val apiUrl = "https://api.bilibili.tv"
    private val biliintlApiUrl = "https://api.biliintl.com"
    private val subtitleProxyUrl = "https://bstation-subtitle.cf1-e6a.workers.dev"

    // Complete cookies from fresh browser export (2026-02-03)
    private val cookieString = "SESSDATA=d3e2b1e9,1785599046,be897*210091; bili_jct=c354fd55e047c9b7daddc250b5004972; DedeUserID=1709563281; DedeUserID__ckMd5=4568e91a427e5c0dd0403fdd96efae6f; mid=1709563281; buvid3=f165a4d3-71ca-42fb-aa5a-956c0eae673a44290infoc; buvid4=193EE759-E26D-6A17-9E3A-F6515909D4AF56972-026020223-VQcoVjaTucTuIONkEeQ0RA==; joy_jct=c354fd55e047c9b7daddc250b5004972; bstar-web-lang=id; bsource=search_google"
    
    // Keep empty cookies map for compatibility
    private val cookies = mapOf<String, String>()

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36",
        "Referer" to "https://www.bilibili.tv/",
        "Origin" to "https://www.bilibili.tv",
        "Cookie" to cookieString
    )

    override val mainPage = mainPageOf(
        "timeline" to "Jadwal Rilis",
        "trending" to "Sedang Tren",
        "popular" to "Anime Populer",
        "recommend" to "Rekomendasi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = mutableListOf<SearchResponse>()
        
        when (request.data) {
            "timeline" -> {
                // Use Timeline API for release schedule
                val timelineUrl = "$apiUrl/intl/gateway/web/v2/ogv/timeline?s_locale=id_ID&platform=web"
                val res = app.get(timelineUrl, headers = headers, cookies = cookies).parsedSafe<TimelineResult>()
                
                res?.data?.items?.forEach { day ->
                    day.cards?.forEach { card ->
                        val title = card.title ?: return@forEach
                        val seasonId = card.seasonId ?: return@forEach
                        val cover = card.cover
                        
                        items.add(newAnimeSearchResponse(title, seasonId, TvType.Anime) {
                            this.posterUrl = cover
                        })
                    }
                }
            }
            "trending" -> {
                // Use Search API with trending keywords
                val searchUrl = "$apiUrl/intl/gateway/web/v2/search_result?keyword=2024&s_locale=id_ID&limit=30"
                val res = app.get(searchUrl, headers = headers, cookies = cookies).parsedSafe<SearchResult>()
                
                res?.data?.modules?.forEach { module ->
                    module.data?.items?.forEach { item ->
                        val title = item.title ?: return@forEach
                        val seasonId = item.seasonId ?: return@forEach
                        
                        items.add(newAnimeSearchResponse(title, seasonId, TvType.Anime) {
                            this.posterUrl = item.cover ?: item.poster ?: item.horizontalCover
                        })
                    }
                }
            }
            "popular" -> {
                // Use Search API for popular anime
                val searchUrl = "$apiUrl/intl/gateway/web/v2/search_result?keyword=anime&s_locale=id_ID&limit=30"
                val res = app.get(searchUrl, headers = headers, cookies = cookies).parsedSafe<SearchResult>()
                
                res?.data?.modules?.forEach { module ->
                    module.data?.items?.forEach { item ->
                        val title = item.title ?: return@forEach
                        val seasonId = item.seasonId ?: return@forEach
                        
                        items.add(newAnimeSearchResponse(title, seasonId, TvType.Anime) {
                            this.posterUrl = item.cover ?: item.poster ?: item.horizontalCover
                        })
                    }
                }
            }
            "recommend" -> {
                // Use Search API with action/romance keywords for recommendations
                val searchUrl = "$apiUrl/intl/gateway/web/v2/search_result?keyword=action&s_locale=id_ID&limit=30"
                val res = app.get(searchUrl, headers = headers, cookies = cookies).parsedSafe<SearchResult>()
                
                res?.data?.modules?.forEach { module ->
                    module.data?.items?.forEach { item ->
                        val title = item.title ?: return@forEach
                        val seasonId = item.seasonId ?: return@forEach
                        
                        items.add(newAnimeSearchResponse(title, seasonId, TvType.Anime) {
                            this.posterUrl = item.cover ?: item.poster ?: item.horizontalCover
                        })
                    }
                }
            }
        }
        
        return newHomePageResponse(request.name, items.distinctBy { it.url })
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
                // Use index_show first, then try title if it's a number, then use list index
                val epNum = (ep.index?.toIntOrNull()) ?: (ep.title?.toIntOrNull())
                val epName = if (ep.title?.toIntOrNull() != null) "Episode ${ep.title}" else (ep.title ?: "Episode ${epNum ?: "?"}")
                
                episodes.add(newEpisode(LoadData(ep.id.toString(), seasonId).toJson()) {
                    this.name = epName
                    this.episode = epNum ?: (episodes.size + 1)
                    this.posterUrl = ep.cover
                })
            }
        }

        result.episodes?.forEach { ep ->
            if (episodes.none { parseJson<LoadData>(it.data).epId == ep.id.toString() }) {
                val epNum = (ep.index?.toIntOrNull()) ?: (ep.title?.toIntOrNull())
                val epName = if (ep.title?.toIntOrNull() != null) "Episode ${ep.title}" else (ep.title ?: "Episode ${epNum ?: "?"}")
                
                episodes.add(newEpisode(LoadData(ep.id.toString(), seasonId).toJson()) {
                    this.name = epName
                    this.episode = epNum ?: (episodes.size + 1)
                    this.posterUrl = ep.cover
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
            this.posterUrl = poster
            this.plot = description
        }
    }
    @OptIn(com.lagradost.cloudstream3.Prerelease::class)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = try {
            parseJson<LoadData>(data)
        } catch (e: Exception) {
            LoadData(data, "")
        }
        val epId = loadData.epId
        
        var foundLinks = false
        
        // PRIMARY: Use bilibili.tv API (more reliable)
        try {
            val primaryUrl = "$apiUrl/intl/gateway/v2/ogv/playurl?ep_id=$epId&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID"
            val primaryRes = app.get(primaryUrl, headers = headers, cookies = cookies).parsedSafe<OldPlayResult>()
            val videoInfo = primaryRes?.data?.videoInfo
            
            if (videoInfo != null) {
                // Get audio URL
                val audioUrl = videoInfo.dashAudio?.firstOrNull()?.baseUrl
                
                val addedQualities = mutableSetOf<String>()

                // 1. Prioritize Muxed (durl) streams (Audio+Video guaranteed)
                primaryRes.data?.durl?.forEach { durl ->
                    val videoUrl = durl.url ?: return@forEach
                    
                    callback.invoke(
                        newExtractorLink(this.name, "$name Direct", videoUrl, INFER_TYPE) {
                            this.referer = "https://www.bilibili.tv/"
                            this.quality = Qualities.Unknown.value
                            this.headers = this@Bstation.headers
                        }
                    )
                    foundLinks = true
                }
                
                // 2. Process streamList (DASH) - Add Audio for Pre-release
                videoInfo.streamList?.forEach { stream ->
                    val videoUrl = stream.dashVideo?.baseUrl ?: stream.baseUrl ?: return@forEach
                    val quality = stream.streamInfo?.displayDesc ?: "Unknown"

                    // Strict Deduplication
                    if (addedQualities.contains(quality)) return@forEach
                    addedQualities.add(quality)

                    // Try to merge audio (Pre-release feature)
                    try {
                        if (!audioUrl.isNullOrEmpty()) {
                            val audioFiles = listOf(newAudioFile(audioUrl) {})
                            callback.invoke(
                                newExtractorLink(this.name, "$name $quality", videoUrl, INFER_TYPE) {
                                    this.referer = "https://www.bilibili.tv/"
                                    this.quality = getQualityFromName(quality)
                                    this.headers = this@Bstation.headers
                                    this.audioTracks = audioFiles
                                }
                            )
                            foundLinks = true
                            return@forEach
                        }
                    } catch (_: Throwable) {
                        // Pre-release feature not available, fallback to video-only
                    }

                    // Fallback: Video without audio merging
                    callback.invoke(
                        newExtractorLink(this.name, "$name $quality", videoUrl, INFER_TYPE) {
                            this.referer = "https://www.bilibili.tv/"
                            this.quality = getQualityFromName(quality)
                            this.headers = this@Bstation.headers
                        }
                    )
                    foundLinks = true
                }
            }
        } catch (_: Exception) {}
        
        // FALLBACK: Use biliintl.com API if primary failed
        if (!foundLinks) {
            try {
                val fallbackUrl = "$biliintlApiUrl/intl/gateway/web/playurl?ep_id=$epId&s_locale=id_ID&platform=android&qn=64"
                val fallbackRes = app.get(fallbackUrl, headers = headers, cookies = cookies).parsedSafe<BiliIntlPlayResult>()
                val playurl = fallbackRes?.data?.playurl
                
                if (playurl != null) {
                    val videos = playurl.video ?: emptyList()
                    val audioUrl = playurl.audioResource?.maxByOrNull { it.bandwidth ?: 0 }?.url
                    
                    for (videoItem in videos) {
                        val videoResource = videoItem.videoResource ?: continue
                        val videoUrl = videoResource.url
                        if (videoUrl.isNullOrEmpty()) continue
                        
                        val quality = videoItem.streamInfo?.descWords ?: "${videoResource.height ?: 0}P"
                        
                        // Stable Video Link (No Audio/Prerelease logic)
                        callback.invoke(
                            newExtractorLink(this.name, "$name $quality", videoUrl, INFER_TYPE) {
                                this.referer = "https://www.bilibili.tv/"
                                this.quality = getQualityFromName(quality)
                                this.headers = this@Bstation.headers
                            }
                        )
                        foundLinks = true
                    }
                }
            } catch (_: Exception) {}
        }

        // Fetch subtitles
        try {
            val subApiUrl = "$apiUrl/intl/gateway/v2/ogv/view/app/episode?ep_id=$epId&platform=web&s_locale=id_ID"
            val subRes = app.get(subApiUrl, headers = headers, cookies = cookies).parsedSafe<EpisodeResult>()
            
            subRes?.data?.subtitles?.forEach { sub ->
                val subUrl = sub.url ?: return@forEach
                val subTitle = sub.title ?: sub.lang ?: "Unknown"
                
                // Use Subtitle Proxy to convert JSON to VTT
                val proxyUrl = "$subtitleProxyUrl/?url=${java.net.URLEncoder.encode(subUrl, "UTF-8")}"
                subtitleCallback.invoke(SubtitleFile(subTitle, proxyUrl))
            }
        } catch (_: Exception) {}

        return foundLinks
    }

    // Helper function to convert Bstation JSON subtitle to WebVTT format
    private fun convertJsonToVtt(jsonSub: BiliSubtitleJson?): String {
        if (jsonSub?.body.isNullOrEmpty()) return ""
        
        val sb = StringBuilder()
        sb.appendLine("WEBVTT")
        sb.appendLine()
        
        jsonSub.body?.forEachIndexed { index, entry ->
            val fromTime = formatVttTime(entry.from ?: 0.0)
            val toTime = formatVttTime(entry.to ?: 0.0)
            val content = entry.content ?: ""
            
            if (content.isNotEmpty()) {
                sb.appendLine("${index + 1}")
                sb.appendLine("$fromTime --> $toTime")
                sb.appendLine(content)
                sb.appendLine()
            }
        }
        
        return sb.toString()
    }
    
    // Helper function to convert Bstation JSON subtitle to SRT format
    private fun convertJsonToSrt(jsonSub: BiliSubtitleJson?): String {
        if (jsonSub?.body.isNullOrEmpty()) return ""
        
        val sb = StringBuilder()
        
        jsonSub.body?.forEachIndexed { index, entry ->
            val fromTime = formatSrtTime(entry.from ?: 0.0)
            val toTime = formatSrtTime(entry.to ?: 0.0)
            val content = entry.content ?: ""
            
            if (content.isNotEmpty()) {
                sb.appendLine("${index + 1}")
                sb.appendLine("$fromTime --> $toTime")
                sb.appendLine(content)
                sb.appendLine()
            }
        }
        
        return sb.toString()
    }
    
    // Format seconds to VTT timestamp (HH:MM:SS.mmm) - note: VTT uses dot
    private fun formatVttTime(seconds: Double): String {
        val totalSeconds = seconds.toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        val millis = ((seconds - totalSeconds) * 1000).toInt()
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, secs, millis)
    }
    
    // Format seconds to SRT timestamp (HH:MM:SS,mmm) - note: SRT uses comma
    private fun formatSrtTime(seconds: Double): String {
        val totalSeconds = seconds.toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        val millis = ((seconds - totalSeconds) * 1000).toInt()
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, secs, millis)
    }

    // Bstation JSON Subtitle format
    data class BiliSubtitleJson(
        @JsonProperty("body") val body: List<BiliSubtitleEntry>?
    )
    data class BiliSubtitleEntry(
        @JsonProperty("from") val from: Double?,
        @JsonProperty("to") val to: Double?,
        @JsonProperty("content") val content: String?
    )

    // Data Classes
    data class LoadData(val epId: String, val seasonId: String)
    
    // Timeline API
    data class TimelineResult(@JsonProperty("data") val data: TimelineData?)
    data class TimelineData(@JsonProperty("items") val items: List<TimelineDay>?)
    data class TimelineDay(@JsonProperty("cards") val cards: List<TimelineCard>?)
    data class TimelineCard(
        @JsonProperty("title") val title: String?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("season_id") val seasonId: String?
    )
    
    // Search API
    data class SearchResult(@JsonProperty("data") val data: SearchData?)
    data class SearchData(@JsonProperty("modules") val modules: List<SearchModule>?)
    data class SearchModule(@JsonProperty("data") val data: SearchModuleData?)
    data class SearchModuleData(@JsonProperty("items") val items: List<SearchItem>?)
    data class SearchItem(
        @JsonProperty("title") val title: String?,
        @JsonProperty("season_id") val seasonId: String?,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("horizontal_cover") val horizontalCover: String?
    )

    // Season API
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

    // Episode API (for subtitles)
    data class EpisodeResult(@JsonProperty("data") val data: EpisodeApiData?)
    data class EpisodeApiData(@JsonProperty("subtitles") val subtitles: List<SubtitleData>?)

    // Old Play API (bilibili.tv) - Uses 'data' not 'result'
    data class OldPlayResult(@JsonProperty("data") val data: OldPlayData?)
    data class OldPlayData(
        @JsonProperty("video_info") val videoInfo: OldVideoInfo?,
        @JsonProperty("durl") val durl: List<OldDurl>?
    )
    data class OldVideoInfo(
        @JsonProperty("stream_list") val streamList: List<OldStream>?,
        @JsonProperty("dash_audio") val dashAudio: List<OldDashAudio>?
    )
    data class OldDashAudio(@JsonProperty("base_url") val baseUrl: String?)
    data class OldStream(
        @JsonProperty("stream_info") val streamInfo: OldStreamInfo?,
        @JsonProperty("dash_video") val dashVideo: OldDashVideo?,
        @JsonProperty("base_url") val baseUrl: String?
    )
    data class OldStreamInfo(@JsonProperty("display_desc") val displayDesc: String?)
    data class OldDashVideo(@JsonProperty("base_url") val baseUrl: String?)
    data class OldDurl(@JsonProperty("url") val url: String?)

    // BiliIntl Play Response Classes
    data class BiliIntlPlayResult(
        @JsonProperty("code") val code: Int?,
        @JsonProperty("data") val data: BiliIntlData?
    )
    data class BiliIntlData(
        @JsonProperty("playurl") val playurl: BiliIntlPlayurl?
    )
    data class BiliIntlPlayurl(
        @JsonProperty("duration") val duration: Long?,
        @JsonProperty("video") val video: List<BiliIntlVideo>?,
        @JsonProperty("audio_resource") val audioResource: List<BiliIntlAudio>?
    )
    data class BiliIntlVideo(
        @JsonProperty("video_resource") val videoResource: BiliIntlVideoResource?,
        @JsonProperty("stream_info") val streamInfo: BiliIntlStreamInfo?,
        @JsonProperty("audio_quality") val audioQuality: Int?
    )
    data class BiliIntlVideoResource(
        @JsonProperty("url") val url: String?,
        @JsonProperty("bandwidth") val bandwidth: Int?,
        @JsonProperty("codecs") val codecs: String?,
        @JsonProperty("width") val width: Int?,
        @JsonProperty("height") val height: Int?
    )
    data class BiliIntlStreamInfo(
        @JsonProperty("quality") val quality: Int?,
        @JsonProperty("desc_words") val descWords: String?
    )
    data class BiliIntlAudio(
        @JsonProperty("url") val url: String?,
        @JsonProperty("bandwidth") val bandwidth: Int?,
        @JsonProperty("codecs") val codecs: String?,
        @JsonProperty("quality") val quality: Int?
    )
}

