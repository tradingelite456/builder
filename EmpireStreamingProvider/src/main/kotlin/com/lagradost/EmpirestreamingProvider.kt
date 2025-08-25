package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.model.*
import org.jsoup.nodes.Document

class EmpireStreamingProvider : MainAPI() {

    override val mainUrl = "https://empire-stream.ink/"
    override var name = "Empire Streaming"
    override val lang = "FR"

    private val filmsUrl = "$mainUrl/films?page="
    private val seriesUrl = "$mainUrl/series?page="
    private val searchUrl = "$mainUrl/recherche/"

    // Utilisation simple de l'interceptor fourni par CS3
    private val interceptor = AppUtils.defaultInterceptor()

    override fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = mutableListOf<HomePageList>()
        // Exemple : films récents
        val filmsDoc = app.get(filmsUrl + page, interceptor = interceptor).document
        val films = filmsDoc.select("div.film-item").map {
            val title = it.selectFirst("h3.title")?.text() ?: return@map null
            val url = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
            val poster = fixUrl(it.selectFirst("img")?.attr("src") ?: "")
            Movie(url, title, poster)
        }.filterNotNull()
        list.add(HomePageList("Films", films))
        return HomePageResponse(list)
    }

    override fun search(query: String): List<SearchResponse> {
        val doc = app.get(searchUrl + query, interceptor = interceptor).document
        return doc.select("div.result-item").mapNotNull {
            val title = it.selectFirst("h3.title")?.text() ?: return@mapNotNull null
            val url = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
            val poster = fixUrl(it.selectFirst("img")?.attr("src") ?: "")
            Movie(url, title, poster)
        }
    }

    override fun load(url: String): LoadResponse {
        val doc = app.get(url, interceptor = interceptor).document
        val title = doc.selectFirst("h1.title")?.text() ?: "N/A"
        val poster = fixUrl(doc.selectFirst("div.poster img")?.attr("src") ?: "")

        val loadResponse = LoadResponse(
            name = title,
            posterUrl = poster
        )

        // Acteurs
        val actors = doc.select("div.cast a").map { Actor(it.text()) }
        loadResponse.addActors(actors)

        // Episodes pour séries
        val episodes = doc.select("div#episodes a").mapNotNull {
            val epName = it.text()
            val epUrl = fixUrl(it.attr("href"))
            newEpisode(epUrl, epName)
        }
        loadResponse.addEpisodes(episodes)

        return loadResponse
    }

    private fun fixUrl(url: String): String {
        return if (url.startsWith("http")) url else mainUrl.trimEnd('/') + "/" + url.trimStart('/')
    }
}
