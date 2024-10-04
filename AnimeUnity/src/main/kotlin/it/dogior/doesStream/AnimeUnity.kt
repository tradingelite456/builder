@file:Suppress("PackageName")

package it.dogior.doesStream

import com.lagradost.api.Log
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.addPoster
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.NiceResponse

typealias Str = BooleanOrString.AsString
typealias Bool = BooleanOrString.AsBoolean

const val TAG = "AnimeUnity"

class AnimeUnity : MainAPI() {
    override var mainUrl = Companion.mainUrl
    override var name = Companion.name
    override var supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override var lang = "it"
    override val hasMainPage = true
    override val hasQuickSearch: Boolean = true
    override var sequentialMainPage = true

    companion object {
        val mainUrl = "https://www.animeunity.to"
        var name = "AnimeUnity"
        val headers = mapOf(
            "Host" to "www.animeunity.to"
        ).toMutableMap()
        var cookies = emptyMap<String, String>()
    }

    private val sectionNamesList = mainPageOf(
        "$mainUrl/archivio/" to "Popolari",
        "$mainUrl/archivio/" to "I migliori",
        "$mainUrl/archivio/" to "Popolari non doppiati",
        "$mainUrl/archivio/" to "I migliori non doppiati",
    )
    override val mainPage = sectionNamesList


    private suspend fun setupHeadersAndCookies() {
        val response = app.get(mainUrl, headers = mapOf("Host" to "www.animeunity.to"))

        val csrfToken = response.document.head().select("meta[name=csrf-token]").attr("content")
        val h = mapOf(
            "X-Requested-With" to "XMLHttpRequest",
            "Content-Type" to "application/json;charset=utf-8",
            "X-CSRF-Token" to csrfToken,
            "Referer" to "https://www.animeunity.to/archivio",
            "Referer" to "https://www.animeunity.to"
        )
        headers.putAll(h)
        cookies = response.cookies
//        Log.d("$TAG:setup", "Headers: $headers")

    }

    private fun resetHeadersAndCookies() {
        headers.clear()
        headers["Host"] = "www.animeunity.to"
        cookies = emptyMap()
    }

    private fun searchResponseBuilder(objectList: List<Anime>): List<SearchResponse> {
        val list = objectList.map {
            newAnimeSearchResponse(
                name = it.title ?: it.titleIt ?: it.titleEng!!,
                url = "$mainUrl/anime/${it.id}-${it.slug}",
                type = if (it.type == "TV") TvType.Anime
                else if (it.type == "Movie" || it.episodesCount == 1) TvType.AnimeMovie
                else TvType.OVA
            ) {
                addDubStatus(it.dub == 1)
                addPoster(it.imageUrl)
            }
        }
        return list
    }

    //Get the Homepage
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val localTag = "$TAG:MainPage"

        val url = request.data + "get-animes"
        if (cookies.isEmpty()) {
            resetHeadersAndCookies()
            setupHeadersAndCookies()
        }

        Log.d(localTag, "Sezione: ${request.name}")
        val requestData = getDataPerHomeSection(request.name)

        val offset = (page - 1) * 30
        requestData.offset = offset

        Log.d(localTag, "Page: $page \t Offset: $offset \t Request offset: ${requestData.offset}")
        val requestBody = requestData.toRequestBody()


        val response =
            app.post(url, headers = headers, cookies = cookies, requestBody = requestBody)

//        Log.d(localTag, "Cookies: ${response.cookies}")
        val responseObject = parseJson<ApiResponse>(response.text)
        val titles = responseObject.titles
        Log.d(localTag, "Titles: $titles")

        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = titles?.let { searchResponseBuilder(it) } ?: emptyList(),
                isHorizontalImages = false
            ), false
        )
    }

    private fun getDataPerHomeSection(section: String) = when (section) {
        "Popolari" -> {
            RequestData(orderBy = Str("Popolarità"))
        }

        "I migliori" -> {
            RequestData(orderBy = Str("Valutazione"))
        }

        "Popolari non doppiati" -> {
            RequestData(orderBy = Str("Popolarità"), dubbed = 0)
        }

        "I migliori non doppiati" -> {
            RequestData(orderBy = Str("Valutazione"), dubbed = 0)
        }

        else -> {
            RequestData()
        }
    }


    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // This function gets called when you search for something also
    //This is to get Title,Href,Posters for Homepage
    override suspend fun search(query: String): List<SearchResponse> {
        val localTag = "$TAG:search"
        val url = "$mainUrl/archivio/get-animes"

        resetHeadersAndCookies()
        setupHeadersAndCookies()

        val requestBody = RequestData(title = query, dubbed = 0).toRequestBody()
        val response =
            app.post(url, headers = headers, cookies = cookies, requestBody = requestBody)

        val responseObject = parseJson<ApiResponse>(response.text)
        val titles = responseObject.titles
        Log.d(localTag, "Titles: $titles")

        return searchResponseBuilder(titles ?: emptyList())
    }

    // This function gets called when you enter the page/show
    override suspend fun load(url: String): LoadResponse {
        val localTag = "$TAG:load"
        Log.d(localTag, "URL: $url")

        val animeId = url.substringAfterLast("/").substringBefore("-").toInt()
        val animeSlug = url.substringAfterLast("/").substringAfter("-")

        Log.d(localTag, "Id: $animeId \t Slug: $animeSlug")

        val title =
            getAnimeElement(animeId, animeSlug) ?: throw ErrorLoadingException("Error loading API")

        val anime = newAnimeLoadResponse(
            name = title.titleIt ?: title.titleEng ?: title.title!!,
            url = url,
            type = if (title.type == "TV") TvType.Anime
                else if (title.type == "Movie" || title.episodesCount == 1) TvType.AnimeMovie
                else TvType.OVA,
            ) {
            addPoster(title.imageUrlCover ?: title.imageUrl)
            addRating(title.score)
            addDuration(title.episodesLength.toString() + " minuti")
            val dub = if (title.dub == 1) DubStatus.Dubbed else DubStatus.Subbed
            if (this.type != TvType.AnimeMovie) {
                addEpisodes(dub, getEpisodes(title))
                addAniListId(title.anilistId)
                addMalId(title.malId)
            }
            this.plot = title.plot
            this.tags = title.genres.map { it.name.capitalize() }
        }

        return anime
    }

    private suspend fun getAnimeElement(animeId: Int, slug: String): Anime? {

        val url = "$mainUrl/archivio/get-animes"
        val animeName = slug.replace("-", " ").replace("ita", "").trim()
        var titles: List<Anime>?
        var offset = 0
        do {
            resetHeadersAndCookies()
            setupHeadersAndCookies()
            val requestBody = RequestData(title = animeName, offset = offset, dubbed = 0).toRequestBody()
            val response =
                app.post(url, headers = headers, cookies = cookies, requestBody = requestBody)
            val responseObj = parseJson<ApiResponse>(response.text)
            titles = responseObj.titles
            val totalTitles = responseObj.total

            Log.d("$TAG:getAnime", "Id e Slug attuali: $animeId \t $slug")
            Log.d("$TAG:getAnime", "Offset: $offset \t Titles: $titles")
            if (titles?.any { it.id == animeId && it.slug == slug } == true) {
                return titles.firstOrNull { it.id == animeId && it.slug == slug }
            }
            offset += 30
        } while ((offset < totalTitles - 30))

        return null
    }

    private fun getEpisodes(anime: Anime): List<Episode> {
        val localTag = "$TAG:getEpisodes"
        val episodeList = mutableListOf<Episode>()

        anime.episodes.forEach { ep ->
            episodeList.add(
                newEpisode(ep.link){
                    this.episode = ep.number.toIntOrNull()
                }
            )
        }

        return episodeList
    }

    // This function is how you load the links
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val localTag = "$TAG:loadLinks"

        Log.d(TAG, "Url : $data")

        AnimeUnityExtractor().getUrl(
            url = data,
            referer = mainUrl,
            subtitleCallback = subtitleCallback,
            callback = callback
        )
        return false
    }
}
