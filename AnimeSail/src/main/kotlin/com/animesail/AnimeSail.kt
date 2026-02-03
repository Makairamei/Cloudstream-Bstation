package com.animesail

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class AnimeSail : ParsedHttpSource() {
    override val name = "AnimeSail"
    override val baseUrl = "https://154.26.137.28"
    override val lang = "id"
    override val supportsLatest = true
    
    // Hardcoded cookies to bypass Cloudflare Turnstile (User Provided)
    private val cookies = mapOf(
        "_as_turnstile" to "7ca75f267f4baab5b0a7634d930503312176f854567a9d870f3c371af04eefe4",
        "_as_ipin_ct" to "ID",
        "_as_ipin_tz" to "Asia/Jakarta",
        "_as_ipin_lc" to "en-US"
    )

    override val mainPage = mainPageOf(
        "$baseUrl/rilisan-anime-terbaru/page/" to "Episode Terbaru",
        "$baseUrl/rilisan-donghua-terbaru/page/" to "Donghua Terbaru",
        "$baseUrl/anime/page/" to "Daftar Anime",
        "$baseUrl/movie-terbaru/page/" to "Movie Terbaru"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url, cookies = cookies).document
        
        val home = document.select("article.bs, article.bsz").mapNotNull {
            val title = it.selectFirst("div.tt h2")?.text()?.replace("Subtitle Indonesia", "")?.trim() ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val img = it.selectFirst("img")?.attr("src")
            val epNum = it.selectFirst("div.tt span")?.text()

            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = img
                addQuality(epNum)
            }
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$baseUrl/?s=$query"
        val document = app.get(url, cookies = cookies).document

        return document.select("article.bs").mapNotNull {
            val title = it.selectFirst("div.tt h2")?.text()?.replace("Subtitle Indonesia", "")?.trim() ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val img = it.selectFirst("img")?.attr("src")

            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = img
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, cookies = cookies).document
        
        // Handle "Episode" page -> Redirect to parent Anime page if possible
        // But for consistency, if user clicked an episode, we usually want to load the Anime details
        val isEpisode = url.contains("-episode-")
        if (isEpisode) {
            val animeLink = document.selectFirst(".names a")?.attr("href")
            if (animeLink != null && !animeLink.contains("episode")) {
                 return load(animeLink) 
            }
        }

        val title = document.selectFirst("h1.entry-title")?.text()?.replace("Subtitle Indonesia", "")?.trim() ?: ""
        val poster = document.selectFirst("div.thumb img")?.attr("src")
        val description = document.selectFirst("div.entry-content p")?.text()
        val rating = document.selectFirst(".rating strong")?.text()?.toRatingInt()
        val tags = document.select(".genxed a").map { it.text() }

        val episodes = document.select("div.eplister ul li").mapNotNull {
            val link = it.selectFirst("a") ?: return@mapNotNull null
            val epTitle = link.selectFirst("div.epl-title")?.text() ?: link.text()
            val epUrl = link.attr("href")
            val epNumStr = link.selectFirst("div.epl-num")?.text()
            
            val epNum = epNumStr?.filter { it.isDigit() }?.toIntOrNull() 
                        ?: epTitle.filter { it.isDigit() }.toIntOrNull()

            newEpisode(epUrl) {
                this.name = epTitle
                this.episode = epNum
            }
        }.reversed()
        
        val recommendations = document.select(".relatedpost article").mapNotNull {
            val recTitle = it.selectFirst("h2")?.text()?.replace("Subtitle Indonesia", "")?.trim() ?: return@mapNotNull null
            val recHref = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val recImg = it.selectFirst("img")?.attr("src")
            newAnimeSearchResponse(recTitle, recHref, TvType.Anime) {
               this.posterUrl = recImg
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.rating = rating
            this.tags = tags
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        url: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(url, cookies = cookies).document
        
        // 1. Scan Standard Iframes
        document.select("iframe").forEach { iframe ->
            fixAndLoad(iframe.attr("src"), callback, subtitleCallback)
        }
        
        // 2. Scan Mirrors (Option dropdowns / Li items)
        // Only extract from value/data-src, assume it might be base64
        document.select("ul#mir-list li, .mirror option, .mirror-item").forEach {
             val src = it.attr("data-src").ifEmpty { it.attr("value") }
             if (src.isNotEmpty()) {
                 tryDecodeAndLoad(src, callback, subtitleCallback)
             }
        }
        
        // 3. Scan Base64 in data attributes (data-content, data-default, data-em)
        document.select("[data-content], [data-default], [data-em]").forEach { element ->
             val base64Data = element.attr("data-content").ifEmpty { 
                 element.attr("data-default").ifEmpty { element.attr("data-em") } 
             }
             if (base64Data.isNotEmpty()) {
                 tryDecodeAndLoad(base64Data, callback, subtitleCallback)
             }
        }

        return true
    }

    private suspend fun tryDecodeAndLoad(
        raw: String, 
        callback: (ExtractorLink) -> Unit, 
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        try {
            // Try as plain URL first
            if (raw.startsWith("http")) {
                loadExtractor(raw, callback, subtitleCallback)
                return
            }
            
            // Try decode
            val decoded = decodeBase64(raw)
            
            // If decoded is an iframe tag
            val iframeSrc = Regex("src=\"([^\"]+)\"").find(decoded)?.groupValues?.get(1)
            if (iframeSrc != null) {
                fixAndLoad(iframeSrc, callback, subtitleCallback)
                return
            }
            
            // If decoded is just a URL
            if (decoded.startsWith("http") || decoded.startsWith("//")) {
                fixAndLoad(decoded, callback, subtitleCallback)
            }
        } catch (_: Exception) {}
    }

    private suspend fun fixAndLoad(
        url: String, 
        callback: (ExtractorLink) -> Unit, 
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        var finalSrc = url.trim()
        if (finalSrc.startsWith("//")) finalSrc = "https:$finalSrc"
        if (finalSrc.startsWith("/")) finalSrc = "$baseUrl$finalSrc"
        
        if (finalSrc.startsWith("http")) {
            loadExtractor(finalSrc, "$baseUrl/", subtitleCallback, callback)
        }
    }

    private fun decodeBase64(input: String): String {
        return String(Base64.decode(input, Base64.DEFAULT))
    }
}
