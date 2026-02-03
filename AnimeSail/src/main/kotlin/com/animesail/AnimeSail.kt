package com.animesail

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class AnimeSail : ParsedHttpSource() {
    override val name = "AnimeSail"
    override val baseUrl = "https://154.26.137.28"
    override val lang = "id"
    override val supportsLatest = true
    
    // Cookies required for bypass
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

    override fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
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

    override fun search(query: String): List<SearchResponse> {
        val url = "$baseUrl/?s=$query"
        val document = app.get(url, cookies = cookies).document

        return document.select("article.bs").mapNotNull {
            val title = it.selectFirst("div.tt h2")?.text()?.replace("Subtitle Indonesia", "")?.trim() ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val img = it.selectFirst("img")?.attr("src")
            // Search results might not have ep num in same place, keeping basics
            
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = img
            }
        }
    }

    override fun load(url: String): LoadResponse {
        val document = app.get(url, cookies = cookies).document
        
        // Handle Episode Page -> Redirect logic or Parse as Anime
        // The site structure links to episode pages from "Latest".
        // But we want the Anime Details.
        // Usually there's a "All Episodes" or "Anime Info" link on the episode page.
        // Look for breadcrumbs or "Info" button.
        
        val isEpisode = url.contains("-episode-")
        
        if (isEpisode) {
            // Try to find the anime link from breadcrumbs or info box
            // Common WordPress themes pattern: .gmr-breadcrumb a, or .names a
            // Based on view-source: <div class="names"><a href="...">
            val animeLink = document.selectFirst(".names a")?.attr("href")
            if (animeLink != null && !animeLink.contains("episode")) {
                 return load(animeLink) // Recursive load of the anime page
            }
        }

        val title = document.selectFirst("h1.entry-title")?.text()?.replace("Subtitle Indonesia", "")?.trim() ?: ""
        val poster = document.selectFirst("div.thumb img")?.attr("src")
        val description = document.selectFirst("div.entry-content p")?.text()
        val rating = document.selectFirst(".rating strong")?.text()?.toRatingInt()
        
        // Genre parsing
        val tags = document.select(".genxed a").map { it.text() }

        // Episode List
        // Structure: div.eplister ul li
        // OR: div.bixbox#series-list (sometimes)
        val episodes = document.select("div.eplister ul li").mapNotNull {
            val link = it.selectFirst("a") ?: return@mapNotNull null
            val epTitle = link.selectFirst("div.epl-title")?.text() ?: link.text()
            val epUrl = link.attr("href")
            val epNumStr = link.selectFirst("div.epl-num")?.text()
            
            // Extract number from string if needed
            val epNum = epNumStr?.filter { it.isDigit() }?.toIntOrNull() 
                        ?: epTitle.filter { it.isDigit() }.toIntOrNull()

            newEpisode(epUrl) {
                this.name = epTitle
                this.episode = epNum
            }
        }.reversed()
        
        // Recommendations
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

    override fun loadLinks(
        url: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(url, cookies = cookies).document
        
        // 1. STANDARD IFRAMES (Most common)
        document.select("iframe").forEach { iframe ->
            var src = iframe.attr("src")
            if (src.startsWith("//")) src = "https:$src"
            
            // Log iframe to debug (user can see in logs if needed)
            // System.out.println("Found iframe: $src")
            
            loadExtractor(src, callback, subtitleCallback)
        }
        
        // 2. MIRROR DROPDOWNS (Common in WordPress themes)
        document.select("ul#mir-list li, .mirror option, .mirror-item").forEach {
             var src = it.attr("data-src").ifEmpty { it.attr("value") }
             
             // Decode Base64 if it looks like one (no http/https)
             if (src.isNotEmpty() && !src.startsWith("http")) {
                 try {
                     src = base64Decode(src)
                 } catch (e: Exception) { }
             }
             
             if (src.startsWith("//")) src = "https:$src"
             if (src.startsWith("http")) {
                 loadExtractor(src, callback, subtitleCallback)
             }
        }
        
        // 3. JAVASCRIPT/BASE64 SCANNERS (For hidden players like Blogger/Picasa)
        val html = document.html()
        
        // Regex to find Base64 in data-content or data-default attributes
        val base64Regex = Regex("(?<=data-(content|default)=\")[^\"]+")
        
        base64Regex.findAll(html).forEach { match ->
            try {
                val decoded = base64Decode(match.value)
                val iframeSrc = Regex("src=\"([^\"]+)\"").find(decoded)?.groupValues?.get(1)
                
                if (iframeSrc != null) {
                    var finalSrc = iframeSrc
                    if (finalSrc.startsWith("//")) finalSrc = "https:$finalSrc"
                    
                    // If it's a relative path locally (like /utils/player/...), prepend baseUrl
                    if (finalSrc.startsWith("/")) finalSrc = "$baseUrl$finalSrc"
                    
                    loadExtractor(finalSrc, callback, subtitleCallback)
                }
            } catch (_: Exception) {}
        }

        return true
    }
}
