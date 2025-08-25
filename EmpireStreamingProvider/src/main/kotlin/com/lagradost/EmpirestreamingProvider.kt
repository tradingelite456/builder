package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.model.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.loadHtml
import org.jsoup.nodes.Document

class EmpireStreamingProvider : MainAPI() {

    override var mainUrl = "https://empire-stream.ink/"
    override var lang = "FR"

    private val interceptor = AppUtils.defaultInterceptor()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document: Document = mainUrl.loadHtml(interceptor = interceptor)
        // Exemple de parsing des films / séries pour la home page
        val movies = mutableListOf<Movie>()
        document.select(".film-item").forEach {
            val title = it.select(".film-title").text()
            val url = it.select("a").attr("href")
            val poster = it.select("img").attr("src")
            movies.add(Movie(title = title, url = url, posterUrl = poster))
        }
        return HomePageResponse(movies = movies)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val document: Document = mainUrl.loadHtml(path = "/recherche/$query", interceptor = interceptor)
        val results = mutableListOf<SearchResponse>()
        document.select(".film-item").forEach {
            val title = it.select(".film-title").text()
            val url = it.select("a").attr("href")
            val poster = it.select("img").attr("src")
            results.add(SearchResponse(title = title, url = url, posterUrl = poster))
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val document: Document = url.loadHtml(interceptor = interceptor)
        val episodes = mutableListOf<Episode>()
        val actors = mutableListOf<Actor>()

        // Exemple parsing épisodes
        document.select(".episode-item").forEach {
            val epTitle = it.select(".episode-title").text()
            val epUrl = it.select("a").attr("href")
            episodes.add(Episode(name = epTitle, url = epUrl))
        }

        // Exemple parsing acteurs
        document.select(".actor-item").forEach {
            val actorName = it.select(".actor-name").text()
            actors.add(Actor(name = actorName))
        }

        return LoadResponse(
            name = document.select("h1.film-title").text(),
            posterUrl = document.select(".film-poster img").attr("src"),
            episodes = episodes,
            actors = actors
        )
    }
}
