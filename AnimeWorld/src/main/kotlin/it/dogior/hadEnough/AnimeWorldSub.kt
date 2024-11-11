package it.dogior.hadEnough

import android.annotation.SuppressLint
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class AnimeWorldSub : MainAPI() {
    override var mainUrl = "https://www.animeworld.so"
    override var name = "AnimeWorld Sub"
    override var lang = "jp"
    override val hasMainPage = true
    override val hasQuickSearch = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        private var cookies = emptyMap<String, String>()
        private lateinit var token: String

        // Disabled authentication as site did
        private suspend fun request(url: String): NiceResponse {
//            if (cookies.isEmpty()) {
//                cookies = getCookies(url)
//            }
            return app.get(url)
        }

        fun getType(t: String?): TvType {
            return when (t?.lowercase()) {
                "movie" -> TvType.AnimeMovie
                "ova" -> TvType.OVA
                else -> TvType.Anime
            }
        }

        fun getStatus(t: String?): ShowStatus? {
            return when (t?.lowercase()) {
                "finito" -> ShowStatus.Completed
                "in corso" -> ShowStatus.Ongoing
                else -> null
            }
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/filter?status=0&language=jp&sort=1" to "In Corso",
        "$mainUrl/filter?language=jp&sort=6" to "Più Visti",
        "$mainUrl/filter?language=jp&sort=1" to "Ultimi aggiunti",
        "$mainUrl/tops/all?sort=1" to "Top Anime",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        Log.d("AnimeWorld:MainPage", "Url:${request.data}")
        val pagedata: NiceResponse = if (page > 1) {
            app.get(request.data + "&page=$page")
        } else {
            app.get(request.data)
        }
        val document = pagedata.document
        val list = ArrayList<AnimeSearchResponse>()
        var hasNextPage = false
        if (request.name == "Top Anime") {
            val items = document.select("div.row  .content")
//            Log.d("AnimeWorld:MainPage", "Items: ${items[0]}")
            items.map { list.add(it.contentToSearchResult()) }
        } else {
            val items = document.select("div.film-list > .item")
//            Log.d("AnimeWorld:MainPage", "Items: ${items[0]}")
            items.map { list.add(it.toSearchResult()) }

            val pagingWrapper = document.select("#paging-form").firstOrNull()
            val totalPages = pagingWrapper?.select("span.total")?.text()?.toIntOrNull()
            hasNextPage = totalPages != null && (page + 1) < totalPages
        }

        val finalList = list.filter{ searchResponse ->
            searchResponse.dubStatus?.any{
                it == DubStatus.Subbed
            } ?: true
        }

        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = finalList,
                isHorizontalImages = false
            ), hasNextPage
        )
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        fun String.parseHref(): String {
            val h = this.split('.').toMutableList()
            h[1] = h[1].substringBeforeLast('/')
            return h.joinToString(".")
        }

        val anchor = this.select("a.name").firstOrNull()
            ?: throw ErrorLoadingException("Error parsing the page")
        val title = anchor.text().removeSuffix(" (ITA)")
        val otherTitle = anchor.attr("data-jtitle").removeSuffix(" (ITA)")

        val url = fixUrl(anchor.attr("href").parseHref())
        val poster = this.select("a.poster img").attr("src")

        val statusElement = this.select("div.status") // .first()
        val dub = statusElement.select(".dub").isNotEmpty()


        val type = when {
            statusElement.select(".movie").isNotEmpty() -> TvType.AnimeMovie
            statusElement.select(".ova").isNotEmpty() -> TvType.OVA
            else -> TvType.Anime
        }

        return newAnimeSearchResponse(title, url, type) {
            addDubStatus(dub)
            this.otherName = otherTitle
            this.posterUrl = poster
        }
    }

    private fun Element.contentToSearchResult(): AnimeSearchResponse {
        fun String.parseHref(): String {
            val h = this.split('.').toMutableList()
            h[1] = h[1].substringBeforeLast('/')
            return h.joinToString(".")
        }

        val anchor = this.selectFirst("a") ?: throw ErrorLoadingException("Error parsing the page")
        val url = fixUrl(anchor.attr("href").parseHref())
        val poster = anchor.select("img").attr("src")

        val info = this.select("div.info > .main")

        val name = info.select("a > .name").firstOrNull()
            ?: throw ErrorLoadingException("Error parsing the page")
        val title = name.text().removeSuffix(" (ITA)")
        val otherTitle = name.attr("data-jtitle").removeSuffix(" (ITA)")


        val typeElement = this.select("div.type") // .first()
        val dub = typeElement.select(".dub").isNotEmpty()


        val type = when {
            typeElement.select(".movie").isNotEmpty() -> TvType.AnimeMovie
            typeElement.select(".ova").isNotEmpty() -> TvType.OVA
            else -> TvType.Anime
        }

        return newAnimeSearchResponse(title, url, type) {
            addDubStatus(dub)
            this.otherName = otherTitle
            this.posterUrl = poster
        }
    }


    data class searchJson(
        @JsonProperty("animes") val animes: List<animejson>
    )

    data class animejson(
        @JsonProperty("name") val name: String,
        @JsonProperty("image") val image: String,
        @JsonProperty("link") val link: String,
        @JsonProperty("animeTypeName") val type: String,
        @JsonProperty("language") val language: String,
        @JsonProperty("jtitle") val otherTitle: String,
        @JsonProperty("identifier") val id: String
    )

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        val document = app.post(
            "https://www.animeworld.tv/api/search/v2?keyword=${query}",
            referer = mainUrl,
            cookies = cookies,
            headers = mapOf("csrf-token" to token)
        ).text

        return tryParseJson<searchJson>(document)?.animes?.map { anime ->
            val type = when (anime.type) {
                "Movie" -> TvType.AnimeMovie
                "OVA" -> TvType.OVA
                else -> TvType.Anime
            }
            val dub = when (anime.language) {
                "it" -> true
                else -> false
            }
            newAnimeSearchResponse(anime.name, "$mainUrl/play/${anime.link}.${anime.id}", type) {
                addDubStatus(dub)
                this.otherName = anime.otherTitle
                this.posterUrl = anime.image
            }
        }?.filter{ searchResponse ->
            searchResponse.dubStatus?.any{
                it == DubStatus.Subbed
            } ?: true
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = request("$mainUrl/search?keyword=$query").document
        val searchResultList = document.select(".film-list > .item").map {
            it.toSearchResult()
        }
        return searchResultList.filter{ searchResponse ->
            searchResponse.dubStatus?.any{
                it == DubStatus.Subbed
            } ?: true
        }
    }

    @SuppressLint("NewApi")
    override suspend fun load(url: String): LoadResponse {
        val document = request(url).document
        Log.d("AnimeWorld:load", "Url: $url")


        val widget = document.select("div.widget.info")
        val title = widget.select(".info .title").text().removeSuffix(" (ITA)")
        val otherTitle = widget.select(".info .title").attr("data-jtitle").removeSuffix(" (ITA)")
        val description =
            widget.select(".desc .long").first()?.text() ?: widget.select(".desc").text()
        val poster = document.select(".thumb img").attr("src")

        val type: TvType = getType(widget.select("dd").first()?.text())
        val genres = widget.select(".meta").select("a[href*=\"/genre/\"]").map { it.text() }
        val rating = widget.select("#average-vote").text()

        val trailerUrl = document.select(".trailer[data-url]").attr("data-url")
        val malId = document.select("#mal-button").attr("href")
            .split('/').last().toIntOrNull()
        val anlId = document.select("#anilist-button").attr("href")
            .split('/').last().toIntOrNull()

        var dub = false
        var year: Int? = null
        var status: ShowStatus? = null
        var duration: String? = null

        for (meta in document.select(".meta dt, .meta dd")) {
            val text = meta.text()
            if (text.contains("Audio"))
                dub = meta.nextElementSibling()?.text() == "Italiano"
            else if (year == null && text.contains("Data"))
                year = meta.nextElementSibling()?.text()?.split(' ')?.last()?.toIntOrNull()
            else if (status == null && text.contains("Stato"))
                status = getStatus(meta.nextElementSibling()?.text())
            else if (status == null && text.contains("Durata"))
                duration = meta.nextElementSibling()?.text()
        }

        val servers = document.select(".widget.servers > .widget-body")


//        val servers = document.select(".widget.servers")

        val episodes = servers.select(".server[data-name=\"9\"] .episode").map {
            val id = it.select("a").attr("data-id")
            val number = it.select("a").attr("data-episode-num").toIntOrNull()
            val epUrl = "$mainUrl/api/episode/info?id=$id"
            Log.d("AnimeWorld:load", "Ep Url: $epUrl")
            Episode(
                epUrl,
                episode = number
            )
        }
        val comingSoon = episodes.isEmpty()
        val nextAiringDate = document.select("#next-episode").attr("data-calendar-date")
        val nextAiringTime = document.select("#next-episode").attr("data-calendar-time")
        Log.d("AnimeWorld:load", "NextAiring: $nextAiringDate $nextAiringTime")

        val nextAiringUnix = try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
                .parse(nextAiringDate + "T" + nextAiringTime)?.time?.div(1000)
        } catch (e: Exception) {
            null
        }

        val recommendations = document.select(".film-list.interesting .item").map {
            it.toSearchResult()
        }
        return newAnimeLoadResponse(title, url, type) {
            engName = title
            japName = otherTitle
            addPoster(poster)
            this.year = year
            addEpisodes(if (dub) DubStatus.Dubbed else DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genres
            addMalId(malId)
            addAniListId(anlId)
            addRating(rating)
            addDuration(duration)
            addTrailer(trailerUrl)
            this.recommendations = recommendations
            this.comingSoon = comingSoon
            this.nextAiring = nextAiringUnix?.let { NextAiring(episodes.last().episode!! + 1, it) }
        }
    }

    data class Json(
        @JsonProperty("grabber") val grabber: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("target") val target: String,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val url = tryParseJson<Json>(
            request(data).text
        )?.grabber

        if (url.isNullOrEmpty())
            return false

        callback.invoke(
            ExtractorLink(
                name,
                name,
                url,
                referer = mainUrl,
                quality = Qualities.Unknown.value
            )
        )
        return true
    }
}
