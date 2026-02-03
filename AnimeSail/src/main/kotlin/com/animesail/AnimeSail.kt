package com.animesail

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class AnimeSail : MainAPI() {
    override var mainUrl = "https://154.26.137.28"
    override var name = "AnimeSail"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.Movie, TvType.TvSeries)

    // Cookies untuk bypass Cloudflare
    private val cookies = mapOf(
        "_as_turnstile" to "7ca75f267f4baab5b0a7634d930503312176f854567a9d870f3c371af04eefe4",
        "_as_ipin_ct" to "ID",
        "_as_ipin_tz" to "Asia/Jakarta",
        "_as_ipin_lc" to "en-US"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Semua",
        "$mainUrl/rilisan-anime-terbaru/" to "Anime",
        "$mainUrl/rilisan-donghua-terbaru/" to "Donghua"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}page/$page/" else request.data
        val document = app.get(url, cookies = cookies).document
        
        val items = document.select("article.bs, article.bsz").mapNotNull { el ->
            val title = el.selectFirst("div.tt h2, .bsx h2")?.text()
                ?.replace("Subtitle Indonesia", "")?.trim() ?: return@mapNotNull null
            val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = el.selectFirst("img")?.attr("src")
            val epText = el.selectFirst("div.tt span, .epx")?.text()
            
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = poster
                if (!epText.isNullOrEmpty()) addSub(epText.filter { it.isDigit() }.toIntOrNull())
            }
        }
        
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url, cookies = cookies).document
        
        return document.select("article.bs").mapNotNull { el ->
            val title = el.selectFirst("div.tt h2")?.text()
                ?.replace("Subtitle Indonesia", "")?.trim() ?: return@mapNotNull null
            val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = el.selectFirst("img")?.attr("src")
            
            newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, cookies = cookies).document
        
        // Jika ini halaman episode, ambil link anime parent
        if (url.contains("-episode-")) {
            val parentLink = document.selectFirst(".nvs.nvsc a, .names a")?.attr("href")
            if (parentLink != null && !parentLink.contains("episode")) {
                return load(parentLink)
            }
        }
        
        val title = document.selectFirst("h1.entry-title, .entry-title")?.text()
            ?.replace("Subtitle Indonesia", "")?.trim() ?: "Unknown"
        val poster = document.selectFirst("div.thumb img, .lm img")?.attr("src")
        val description = document.selectFirst("div.entry-content p, .synp p")?.text()
        val tags = document.select(".genxed a, .spe span:contains(Genre) a").map { it.text() }
        
        val episodes = document.select("div.eplister ul li, .lstepx li").mapNotNull { li ->
            val link = li.selectFirst("a") ?: return@mapNotNull null
            val epUrl = link.attr("href")
            val epTitle = link.selectFirst("div.epl-title, .epl-title")?.text() 
                ?: link.selectFirst(".epl-num")?.text() 
                ?: link.text()
            val epNumStr = link.selectFirst("div.epl-num, .epl-num")?.text()
            val epNum = epNumStr?.filter { it.isDigit() }?.toIntOrNull()
                ?: epTitle.filter { it.isDigit() }.toIntOrNull()
            
            Episode(epUrl, epTitle, episode = epNum)
        }.reversed()
        
        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, cookies = cookies).document
        
        // 1. Scan iframe langsung
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty()) {
                loadVideoUrl(src, callback, subtitleCallback)
            }
        }
        
        // 2. Scan select option dengan data-em (hidden video sources)
        document.select("select.mirror option, .player-selector option").forEach { option ->
            val dataEm = option.attr("data-em")
            val dataDefault = option.attr("data-default")
            val dataContent = option.attr("data-content")
            
            listOf(dataEm, dataDefault, dataContent).forEach { encoded ->
                if (encoded.isNotEmpty()) {
                    tryDecodeAndLoad(encoded, callback, subtitleCallback)
                }
            }
        }
        
        // 3. Scan semua element dengan data attributes
        document.select("[data-em], [data-default], [data-content]").forEach { el ->
            listOf(
                el.attr("data-em"),
                el.attr("data-default"),
                el.attr("data-content")
            ).forEach { encoded ->
                if (encoded.isNotEmpty()) {
                    tryDecodeAndLoad(encoded, callback, subtitleCallback)
                }
            }
        }
        
        return true
    }
    
    private suspend fun tryDecodeAndLoad(
        encoded: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        try {
            // Jika sudah URL langsung
            if (encoded.startsWith("http")) {
                loadVideoUrl(encoded, callback, subtitleCallback)
                return
            }
            
            // Decode Base64
            val decoded = String(Base64.decode(encoded, Base64.DEFAULT))
            
            // Cari src dari iframe tag
            val iframeSrc = Regex("""src=["']([^"']+)["']""").find(decoded)?.groupValues?.get(1)
            if (iframeSrc != null) {
                loadVideoUrl(iframeSrc, callback, subtitleCallback)
                return
            }
            
            // Jika decoded adalah URL langsung
            if (decoded.startsWith("http") || decoded.startsWith("//")) {
                loadVideoUrl(decoded, callback, subtitleCallback)
            }
        } catch (_: Exception) {}
    }
    
    private suspend fun loadVideoUrl(
        url: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        var finalUrl = url.trim()
        if (finalUrl.startsWith("//")) finalUrl = "https:$finalUrl"
        if (finalUrl.startsWith("/")) finalUrl = "$mainUrl$finalUrl"
        
        if (finalUrl.startsWith("http")) {
            loadExtractor(finalUrl, mainUrl, subtitleCallback, callback)
        }
    }
}
