package it.dogior.hadEnough

import android.util.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.addPoster
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import it.dogior.hadEnough.extractors.MaxStreamExtractor
import it.dogior.hadEnough.extractors.StreamTapeExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class OnlineSerieTV : MainAPI() {
    override var mainUrl = "https://onlineserietv.com"
    override var name = "OnlineSerieTV"
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries,
        TvType.Cartoon, TvType.Anime, TvType.AnimeMovie, TvType.Documentary
    )
    override var lang = "it"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        mainUrl to "Top 10 Film",
        mainUrl to "Top 10 Serie TV",
        "$mainUrl/movies/" to "Film",
        "$mainUrl/serie-tv/" to "Serie TV",
        "$mainUrl/serie-tv-generi/animazione/" to "Cartoni e Anime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(request.data).document
        val searchResponses = getItems(request.name, response)
        return newHomePageResponse(HomePageList(request.name, searchResponses), false)
    }

    private suspend fun getItems(section: String, page: Document): List<SearchResponse> {
        val searchResponses = when (section) {
            "Film", "Serie TV" -> {
                val itemGrid = page.selectFirst(".wp-block-uagb-post-grid")!!
                val items = itemGrid.select(".uagb-post__inner-wrap")
                items.map {
                    val itemTag = it.select(".uagb-post__title > a")
                    val title = itemTag.text().trim()
                    val url = itemTag.attr("href")
                    val poster = it.select(".uagb-post__image > a > img").attr("src")

                    newTvSeriesSearchResponse(title, url) {
                        this.posterUrl = poster
                    }
                }
            }

            "Top 10 Film", "Top 10 Serie TV" -> {
                val sidebar = page.selectFirst(".sidebar_right")!!
                val bothTop10 = sidebar.select(".links")
                val currentTop10 = if (section == "Top 10 Film") {
                    bothTop10.last()
                } else {
                    bothTop10.first()
                }
                val items = currentTop10?.select(".scrolling > li")
                if (items != null) {
                    items.amap {
                        val title = it.select("a").text().trim()
                        val url = it.select("a").attr("href")

                        val showPage = app.get(url).document
                        val poster = showPage.select(".imgs > img:nth-child(1)").attr("src")
                        newTvSeriesSearchResponse(title, url) {
                            this.posterUrl = poster
                        }
                    }
                } else {
                    emptyList()
                }
            }

            "Cartoni e Anime" -> {
                val itemGrid = page.selectFirst("#box_movies")!!
                val items = itemGrid.select(".movie")
                items.map {
                    it.toSearchResponse()
                }
            }

            else -> {
                Log.d("OnlineSerieTV", "Unknown section: $section")
                emptyList()
            }
        }
        return searchResponses
    }


    private fun Element.toSearchResponse(): SearchResponse {
        val title = this.select("h2").text().trim()
        val url = this.select("a").attr("href")
        val poster = this.select("img").attr("src")
        return newTvSeriesSearchResponse(title, url) {
            this.posterUrl = poster
        }
    }

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/?s=$query")
        val page = response.document
        val itemGrid = page.selectFirst("#box_movies")!!
        val items = itemGrid.select(".movie")
        val searchResponses = items.map {
            it.toSearchResponse()
        }
        return searchResponses
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url).document
        val dati = response.selectFirst(".headingder")!!
        val poster = dati.select(".imgs > img").attr("src").replace(Regex("""-\d+x\d+"""), "")
        val title = dati.select(".dataplus > div:nth-child(1) > h1").text().trim()
        val rating = dati.select(".stars > span:nth-child(3)").text().trim().removeSuffix("/10")
        val genres = dati.select(".stars > span:nth-child(6) > i:nth-child(1)").text().trim()
        val year = dati.select(".stars > span:nth-child(8) > i:nth-child(1)").text().trim()
        val duration = dati.select(".stars > span:nth-child(10) > i:nth-child(1)").text()
            .removeSuffix(" minuti")
        val isMovie = url.contains("/film/")

        return if (isMovie) {
            val streamUrl = response.select("#hostlinks").select("a").map { it.attr("href") }
            val plot = response.select(".post > p:nth-child(16)").text().trim()
            newMovieLoadResponse(title, url, TvType.Movie, streamUrl) {
                addPoster(poster)
                addRating(rating)
                this.duration = duration.toIntOrNull()
                this.year = year.toIntOrNull()
                this.tags = genres.split(",")
                this.plot = plot
            }
        } else {
            val episodes = getEpisodes(response)
            val plot = response.select(".post > p:nth-child(17)").text().trim()
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                addPoster(poster)
                addRating(rating)
                this.year = year.toIntOrNull()
                this.tags = genres.split(",")
                this.plot = plot
            }
        }
    }

    private fun getEpisodes(page: Document): List<Episode> {
        val table = page.selectFirst("#hostlinks > table:nth-child(1)")!!
        var season: Int? = 1
        val rows = table.select("tr")
        val episodes: List<Episode> = rows.mapNotNull {
            if (it.childrenSize() == 0) {
                null
            } else if (it.childrenSize() == 1) {
                val seasonText =
                    it.select("td:nth-child(1)").text().substringBefore("- Episodi disponibi")
                season = Regex("""\d+""").find(seasonText)?.value?.toInt()
                null
            } else {
                val title = it.select("td:nth-child(1)").text()
                val links = it.select("a").map { a -> "\"${a.attr("href")}\"" }
                Episode("$links").apply {
//                    name = title
                    this.season = season
                    this.episode = title.substringAfter("x").substringBefore(" ").toIntOrNull()
                }
            }
        }
        return episodes
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        Log.d("OnlineSerieTV:Links", "Data: $data")
        val links = parseJson<List<String>>(data)
        links.forEach {
            if (it.contains("uprot")) {
                val url = bypassUprot(it)
                Log.d("OnlineSerieTV:Links", "Bypassed Url: $url")
                if (url != null) {
                    if (url.contains("streamtape")) {
                        StreamTapeExtractor().getUrl(url, null, subtitleCallback, callback)
                    } else {
                        MaxStreamExtractor().getUrl(url, null, subtitleCallback, callback)
                    }
                    loadExtractor(url, subtitleCallback, callback)
                }
            }
        }
        return true
    }

    private suspend fun bypassUprot(link: String): String? {
        val updatedLink = if ("msf" in link) link.replace("msf", "mse") else link

        // Generate headers (replace with your own method to generate fake headers)
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        )

        // Make the HTTP request
        val response = app.get(updatedLink, headers = headers, timeout = 10_000)

        // Parse the HTML using Jsoup
        val document = response.document
        Log.d("Uprot", document.toString())//.select("a").toString())
        val maxstreamUrl = document.selectFirst("a")?.attr("href")

        return maxstreamUrl
    }
}