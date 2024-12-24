package it.dogior.hadEnough

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.addPoster
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class CB01(val plugin: CB01Plugin) : MainAPI() {
    override var mainUrl = "https://cb01new.one"
    override var name = "CB01"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "it"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        mainUrl to "Film",
        "$mainUrl/serietv/" to "Serie TV"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val response = app.get(request.data)
        val document = response.document
        val items = document.selectFirst(".sequex-one-columns")!!.select(".post")
        val posts = items.mapNotNull { card ->
            val poster = card.selectFirst("img")?.attr("src")
            val data = card.selectFirst("script")?.data()
            val fixedData = data?.substringAfter("= ")?.substringBefore(";")
            val post = tryParseJson<Post>(fixedData)
            post?.let { it.poster = poster }
            post
        }

        val searchResponses = posts.map {
            if (request.data.contains("serietv")) {
                // TODO: rimuovi tutto quello che c'è dopo il primo - se dopo c'è un numero o la parola stagione
                val title =
                    it.title.replace(Regex("""-?\d+×\d{2}(?:/\d{2})*${'$'}"""), "")
                        .replace("- ITA", "")
                        .replace("- COMPLETA", "").trim()
                newTvSeriesSearchResponse(title, it.permalink, TvType.TvSeries) {
                    addPoster(it.poster)
                }
            } else {
                val quality = if (it.title.contains("HD")) SearchQuality.HD else null
                newMovieSearchResponse(
                    it.title.replace(Regex("""\[HD] \(\d{4}\)${'$'}"""), ""),
                    it.permalink,
                    TvType.Movie
                ) {
                    addPoster(it.poster)
                    this.quality = quality
                }
            }
        }
        val section = HomePageList(request.name, searchResponses, false)
        return newHomePageResponse(section, false)
    }

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf<SearchResponse>()
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val mainContainer = document.selectFirst(".sequex-main-container")!!
        val poster =
            mainContainer.selectFirst(".sequex-featured-img")!!.selectFirst("img")!!.attr("src")
        val banner = mainContainer.selectFirst("#sequex-page-title-img")?.attr("data-img")
        val title = mainContainer.selectFirst("h1")?.text()!!
//        val actionTable = mainContainer.selectFirst("table.cbtable:nth-child(5)")
        val isMovie = !url.contains("serietv")
        val type = if (isMovie) TvType.Movie else TvType.TvSeries
        return if (isMovie) {
            val plot = mainContainer.selectFirst(".ignore-css > p:nth-child(2)")?.text()
                ?.replace("+Info »", "")
            val tags =
                mainContainer.selectFirst(".ignore-css > p:nth-child(1) > strong:nth-child(1)")
                    ?.text()?.split('–')
            val runtime = tags?.find { it.contains("DURATA") }?.trim()
                ?.removePrefix("DURATA")
                ?.removeSuffix("′")?.trim()?.toInt()
            newMovieLoadResponse(title, url, type, url) {
                addPoster(poster)
                this.plot = plot
                this.backgroundPosterUrl = banner
                this.tags = tags?.mapNotNull {
                    if (it.contains("DURATA")) null else it.trim()
                }
                this.duration = runtime
            }
        } else {
            val description = mainContainer.selectFirst(".ignore-css > p:nth-child(1)")?.text()
                ?.split(Regex("""\(\d{4}-(\d{4})?\)"""))
            Log.d("CB01", description.toString())
            val plot = description?.last()?.trim()
            val tags = description?.first()?.split('/')
            newTvSeriesLoadResponse(title, url, type, emptyList()) {
                addPoster(poster)
                this.plot = plot
                this.backgroundPosterUrl = banner
                this.tags = tags?.map { it.trim() }
            }
        }
    }

    data class Post(
        @JsonProperty("id") val id: String,
        @JsonProperty("popup") val popup: String,
        @JsonProperty("unique_id") val uniqueId: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("permalink") val permalink: String,
        @JsonProperty("item_id") val itemId: String,
        var poster: String? = null,
    ) {
    }
}