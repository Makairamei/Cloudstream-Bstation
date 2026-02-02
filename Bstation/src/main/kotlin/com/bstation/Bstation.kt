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

    private val cookies = mapOf(
        "SESSDATA" to "a97adc61%2C1785509852%2Cdd028%2A210091",
        "bili_jct" to "bca5203c3f1cda514530500a8ca0fc10",
        "DedeUserID" to "1709563281",
        "buvid3" to "ddbadfe4-0540-43ce-b22d-5644f59fded314589infoc",
        "buvid4" to "59AD9169-1B99-2A66-E0D3-9A6E759A1FB782033-026011921-q8GoWR2UlMAMBjSSRABQgw%3D%3D",
        "bstar-web-lang" to "en"
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36",
        "Referer" to "https://www.bilibili.tv/",
        "Origin" to "https://www.bilibili.tv"
    )

    override val mainPage = mainPageOf(
        "timeline" to "Jadwal Rilis",
        "search" to "Anime Populer"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = mutableListOf<SearchResponse>()
        
        if (request.data == "timeline") {
            // Use Timeline API
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
        } else {
            // Use Search API for popular anime
            val searchUrl = "$apiUrl/intl/gateway/web/v2/search_result?keyword=anime&s_locale=id_ID&limit=20"
            val res = app.get(searchUrl, headers = headers, cookies = cookies).parsedSafe<SearchResult>()
            
            res?.data?.modules?.forEach { module ->
                module.data?.items?.forEach { item ->
                    val title = item.title ?: return@forEach
                    val seasonId = item.seasonId ?: return@forEach
                    
                    items.add(newAnimeSearchResponse(title, seasonId, TvType.Anime) {
                        this.posterUrl = item.cover
                    })
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
        val loadData = try {
            parseJson<LoadData>(data)
        } catch (e: Exception) {
            // If data is just episode ID string
            LoadData(data, "")
        }
        val epId = loadData.epId
        
        // Use biliintl.com API which returns valid URLs for 480p and below
        val playUrl = "$biliintlApiUrl/intl/gateway/web/playurl?ep_id=$epId&s_locale=id_ID&platform=android&qn=64"
        val res = app.get(playUrl, headers = headers, cookies = cookies).parsedSafe<BiliIntlPlayResult>()
        val playurl = res?.data?.playurl
        
        if (playurl == null) {
            // Fallback to old API
            val oldPlayUrl = "$apiUrl/intl/gateway/v2/ogv/playurl?ep_id=$epId&platform=web&qn=64&type=mp4&tf=0&s_locale=id_ID"
            val oldRes = app.get(oldPlayUrl, headers = headers, cookies = cookies).parsedSafe<OldPlayResult>()
            
            oldRes?.result?.videoInfo?.streamList?.forEach { stream ->
                val videoUrl = stream.dashVideo?.baseUrl ?: stream.baseUrl ?: return@forEach
                val quality = stream.streamInfo?.displayDesc ?: "Unknown"
                
                callback.invoke(
                    newExtractorLink(this.name, "$name $quality", videoUrl, INFER_TYPE) {
                        this.referer = "$mainUrl/"
                        this.quality = getQualityFromName(quality)
                        this.headers = this@Bstation.headers
                    }
                )
            }
            
            oldRes?.result?.durl?.forEach { durl ->
                val videoUrl = durl.url ?: return@forEach
                callback.invoke(
                    newExtractorLink(this.name, "$name Default", videoUrl, INFER_TYPE) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.Unknown.value
                        this.headers = this@Bstation.headers
                    }
                )
            }
            
            return true
        }

        // Process video streams from biliintl API
        val videos = playurl.video ?: emptyList()
        
        for (videoItem in videos) {
            val videoResource = videoItem.videoResource ?: continue
            val videoUrl = videoResource.url
            if (videoUrl.isNullOrEmpty()) continue // Skip locked qualities
            
            val quality = videoItem.streamInfo?.descWords ?: "${videoResource.height ?: 0}P"

            callback.invoke(
                newExtractorLink(this.name, "$name $quality", videoUrl, INFER_TYPE) {
                    this.referer = "$mainUrl/"
                    this.quality = getQualityFromName(quality)
                    this.headers = this@Bstation.headers
                }
            )
        }

        // Fetch subtitles from Episode API (dedicated endpoint for subtitles)
        try {
            val subApiUrl = "$apiUrl/intl/gateway/v2/ogv/view/app/episode?ep_id=$epId&platform=web&s_locale=id_ID"
            val subRes = app.get(subApiUrl, headers = headers, cookies = cookies).parsedSafe<EpisodeResult>()
            
            subRes?.data?.subtitles?.forEach { sub ->
                val subUrl = sub.url ?: return@forEach
                subtitleCallback.invoke(
                    SubtitleFile(sub.title ?: sub.lang ?: "Unknown", subUrl)
                )
            }
        } catch (_: Exception) {}

        return true
    }

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
        @JsonProperty("cover") val cover: String?
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

    // Old Play API (fallback)
    data class OldPlayResult(@JsonProperty("result") val result: OldPlayData?)
    data class OldPlayData(
        @JsonProperty("video_info") val videoInfo: OldVideoInfo?,
        @JsonProperty("durl") val durl: List<OldDurl>?
    )
    data class OldVideoInfo(@JsonProperty("stream_list") val streamList: List<OldStream>?)
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
