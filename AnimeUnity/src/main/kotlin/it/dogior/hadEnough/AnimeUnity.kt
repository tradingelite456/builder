package it.dogior.hadEnough

import android.content.Context
import com.lagradost.api.Log
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.DubStatus
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
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import kotlinx.coroutines.withContext

typealias Str = BooleanOrString.AsString
//typealias Bool = BooleanOrString.AsBoolean

const val TAG = "AnimeUnity"

class AnimeUnity : MainAPI() {
    override var mainUrl = Companion.mainUrl
    override var name = Companion.name
    override var supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override var lang = "it"
    override val hasMainPage = true
    override val hasQuickSearch: Boolean = true
    override var sequentialMainPage = true
    private val sharedPref = activity?.getSharedPreferences("AnimeUnity", Context.MODE_PRIVATE)

    companion object {
        val mainUrl = "https://www.animeunity.to"
        var name = "AnimeUnity"
        val headers = mapOf(
            "Host" to "www.animeunity.to"
        ).toMutableMap()
        var cookies = emptyMap<String, String>()
    }

//    init {
//        val editor = sharedPref?.edit()
//        editor?.clear()
//        editor?.apply()
//    }

    private val sectionNamesList = mainPageOf(
        "$mainUrl/archivio/" to "In Corso",
        "$mainUrl/archivio/" to "Popolari",
        "$mainUrl/archivio/" to "I migliori",
        "$mainUrl/archivio/" to "In Arrivo",
//        "$mainUrl/archivio/" to "In Corso doppiati",
//        "$mainUrl/archivio/" to "Popolari doppiati",
//        "$mainUrl/archivio/" to "I migliori doppiati",
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

    private suspend fun searchResponseBuilder(objectList: List<Anime>): List<SearchResponse> {
        return objectList.amap { anime ->
            // Cache anime data in shared preferences
            withContext(Dispatchers.IO) {
                sharedPref?.edit()?.apply {
                    putString("${anime.id}-${anime.slug}", anime.toJson())
                    apply()
                }

                // Determine title
                val title = (anime.titleIt ?: anime.titleEng ?: anime.title!!)
                    .replace(" (ITA)", "")

                // Get poster image efficiently
                val poster = getImage(anime.imageUrl, anime.anilistId)

                // Create search response
                newAnimeSearchResponse(
                    name = title,
                    url = "$mainUrl/anime/${anime.id}-${anime.slug}",
                    type = when {
                        anime.type == "TV" -> TvType.Anime
                        anime.type == "Movie" || anime.episodesCount == 1 -> TvType.AnimeMovie
                        else -> TvType.OVA
                    }
                ).apply {
                    addDubStatus(anime.dub == 1)
                    addPoster(poster)
                }
            }
        }
    }

    private suspend fun getImage(imageUrl: String?, anilistId: Int): String {
        // First try the direct image URL if available
        if (!imageUrl.isNullOrEmpty()) {
            try {
                val response = withContext(Dispatchers.IO) {
                    app.get(imageUrl)
                }
                if (response.code == 200) {
                    return imageUrl
                }
            } catch (_: Exception) {
                // Fallback to Anilist if direct image fails
            }
        }

        // Fallback to Anilist
        return getAnilistPoster(anilistId)
    }

    private suspend fun getAnilistPoster(anilistId: Int): String {
        val query = """
        query (${'$'}id: Int) {
            Media(id: ${'$'}id, type: ANIME) {
                coverImage {
                    large
                    medium
                }
            }
        }
    """.trimIndent()

        val body = mapOf(
            "query" to query,
            "variables" to """{"id":$anilistId}"""
        )

        return withContext(Dispatchers.IO) {
            val response = app.post("https://graphql.anilist.co", data = body)
            val anilistObj = parseJson<AnilistResponse>(response.text)
            anilistObj.data.media.coverImage?.let { coverImage ->
                coverImage.large ?: coverImage.medium!!
            } ?: throw IllegalStateException("No valid image found")
        }
    }

    //Get the Homepage
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val localTag = "$TAG:MainPage"

        val url = request.data + "get-animes"
        if (cookies.isEmpty()) {
            resetHeadersAndCookies()
            setupHeadersAndCookies()
        }

        val requestData = getDataPerHomeSection(request.name)

        val offset = (page - 1) * 30
        requestData.offset = offset

        Log.d(
            localTag,
            "Sezione: ${request.name} \tPage: $page \t Offset: $offset \t Request offset: ${requestData.offset}"
        )
        val requestBody = requestData.toRequestBody()


        val response =
            app.post(url, headers = headers, cookies = cookies, requestBody = requestBody)

//        Log.d(localTag, "Cookies: ${response.cookies}")
        val responseObject = parseJson<ApiResponse>(response.text)
        val titles = responseObject.titles
//        Log.d(localTag, "Titles: $titles")

        val hasNextPage = requestData.offset
            ?.let { it < 177 } ?: true && titles?.size == 30
        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = titles?.let { searchResponseBuilder(it) } ?: emptyList(),
                isHorizontalImages = false
            ), hasNextPage
        )
    }

    private fun getDataPerHomeSection(section: String) = when (section) {
        "Popolari" -> {
            RequestData(orderBy = Str("Popolarità"), dubbed = 0)
        }

        "In Arrivo" -> {
            RequestData(status = Str("In Uscita"), dubbed = 0)
        }

        "I migliori" -> {
            RequestData(orderBy = Str("Valutazione"), dubbed = 0)
        }

        "In Corso" -> {
            RequestData(orderBy = Str("Popolarità"), status = Str("In Corso"), dubbed = 0)
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

        val animeKey = url.substringAfterLast("/")

        Log.d(localTag, "Pref key: $animeKey")

        val anime = sharedPref?.getString(animeKey, null)?.let { parseJson<Anime>(it) }
            ?: throw ErrorLoadingException("Error loading anime from cache")
        val episodes = anime.episodes.map {
//            Log.d(localTag, "Episodes: ${it.toJson()}")
            newEpisode("$url/${it.id}") {
                this.episode = it.number.toIntOrNull()
            }
        }
        val title = anime.titleIt ?: anime.titleEng ?: anime.title!!
        val animeLoadResponse = newAnimeLoadResponse(
            name = title.replace(" (ITA)", ""),
            url = url,
            type = if (anime.type == "TV") TvType.Anime
            else if (anime.type == "Movie" || anime.episodesCount == 1) TvType.AnimeMovie
            else TvType.OVA,
        ) {
            this.posterUrl =
                getImage(anime.imageUrl, anime.anilistId)
            this.backgroundPosterUrl = getImage(anime.cover, anime.anilistId)
            addRating(anime.score)
            addDuration(anime.episodesLength.toString() + " minuti")
            val dub = if (anime.dub == 1) DubStatus.Dubbed else DubStatus.Subbed
            addEpisodes(dub, episodes)

            addAniListId(anime.anilistId)
            addMalId(anime.malId)
            this.plot = anime.plot
            val doppiato =
                if (anime.dub == 1) "\uD83C\uDDEE\uD83C\uDDF9  Italiano" else "\uD83C\uDDEF\uD83C\uDDF5  Giapponese"
            this.tags = listOf(doppiato) + anime.genres.map { genre ->
                genre.name.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        Locale.getDefault()
                    ) else it.toString()
                }
            }
            this.comingSoon = anime.status == "In uscita prossimamente"
        }

        return animeLoadResponse
    }


    // This function is how you load the links
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val localTag = "$TAG:loadLinks"
        Log.d(localTag, "Url : $data")

        val document = app.get(data).document

        val sourceUrl = document.select("video-player").attr("embed_url")
//        Log.d(localTag, "Document: $document")
        Log.d(localTag, "Iframe: $sourceUrl")



        AnimeUnityExtractor().getUrl(
            url = sourceUrl,
            referer = mainUrl,
            subtitleCallback = subtitleCallback,
            callback = callback
        )
        return true
    }
}
