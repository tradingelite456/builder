@file:Suppress("PackageName")

package it.dogior.doesStream

import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addPoster
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.RequestBody
import okio.Buffer
import java.io.IOException

typealias Str = BooleanOrString.AsString
typealias Bool = BooleanOrString.AsBoolean

const val TAG = "AnimeUnity"

class AnimeUnity : MainAPI() {
    override var mainUrl = Companion.mainUrl
    override var name = Companion.name
    override var supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override var lang = "it"
    override val hasMainPage = true
//    override val hasQuickSearch: Boolean = true
//    override var sequentialMainPage = true



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
        Log.d("$TAG:setup", "Headers: $headers")

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
                type = if (it.type == "TV") TvType.Anime else if (it.type == "Movie") TvType.AnimeMovie else TvType.OVA
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
        if (offset > 0) {
            requestData.offset = offset
        }

        val requestBody = requestData.toRequestBody()


        val response = app.post(url, headers = headers, cookies = cookies, requestBody = requestBody)

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


//    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    // This function gets called when you search for something also
    //This is to get Title,Href,Posters for Homepage
    override suspend fun search(query: String): List<SearchResponse> {
        val localTag = "$TAG:search"
        val url = "$mainUrl/archivio/get-animes"

        resetHeadersAndCookies()
        setupHeadersAndCookies()

        val requestBody = RequestData(title = Str(query)).toRequestBody()
        val response = app.post(url, headers = headers, cookies = cookies, requestBody = requestBody)

        val responseObject = parseJson<ApiResponse>(response.text)
        val titles = responseObject.titles
        Log.d(localTag, "Titles: $titles")

        return searchResponseBuilder(titles ?: emptyList())
    }

    // This function gets called when you enter the page/show
    override suspend fun load(url: String): LoadResponse? {
        val localTag = "$TAG:load"

        return null
    }

    private suspend fun getEpisodes(): List<Episode> {
        val localTag = "$TAG:getEpisodes"

        val episodeList = mutableListOf<Episode>()

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
