package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class EmpireStreamingProvider : MainAPI() {
    override var mainUrl = "https://empire-stream.ink/"
    override var name = "Empire Streaming"
    override val hasMainPage = true
    override var lang = "fr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // ----------- Data classes utilisées par l’API interne -----------
    data class Image(val src: String?)
    data class SymImage(val poster: String?)
    data class NewEpisode(val id: String?, val title: String?, val poster: String?, val date: String?)
    data class BackDrop(val src: String?)

    // ----------- Page d’accueil (catégories principales) -----------
    override val mainPage = mainPageOf(
        "$mainUrlfilms?page=" to "Films",
        "$mainUrlseries?page=" to "Séries"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url, interceptor = interceptor).document
        val items = document.select("div.mov").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items, hasNext = true)
    }

    // ----------- Résultats de recherche -----------
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrlrecherche/$query"
        val document = app.get(url, interceptor = interceptor).document
        return document.select("div.mov").mapNotNull { it.toSearchResult() }
    }

    // ----------- Chargement d’une fiche (film ou série) -----------
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document
        val title = document.selectFirst("h1")?.text() ?: return null
        val poster = document.selectFirst("div.poster img")?.attr("src")
        val year = document.select("span.year").text().toIntOrNull()

        val description = document.select("div.desc").text()
        val actors = document.select("div.cast a").map { ActorData(it.text()) }

        return if (url.contains("/series/")) {
            val episodes = document.select("div#episodes a").mapNotNull { ep ->
                val name = ep.text()
                val link = fixUrl(ep.attr("href"))
                Episode(link, name)
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                addActors(actors)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                addActors(actors)
            }
        }
    }

    // ----------- Chargement des liens (lecteurs/hosts) -----------
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        safeApiCall {
            val doc = app.get(data, interceptor = interceptor).document
            val players = doc.select("iframe").map { it.attr("src") }

            for (player in players) {
                val playerUrl = fixUrl(player)
                loadExtractor(playerUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }

    // ----------- Utilitaires internes -----------
    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val poster = this.selectFirst("img")?.attr("src")
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }
}
