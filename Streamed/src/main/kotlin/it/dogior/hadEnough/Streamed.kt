@file:Suppress("PackageName")

package it.dogior.hadEnough

import android.content.Context
import android.widget.Toast
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app

import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class Streamed : MainAPI() {
    override var mainUrl = MAIN_URL
    override var name = NAME
    override var supportedTypes = setOf(TvType.Live)
    override var lang = "uni"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    private val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)
    init {
        val editor = sharedPref?.edit()
        editor?.clear()
        editor?.apply()
    }

    companion object {
        var canShowToast = true
        const val MAIN_URL = "https://streamed.su"
        const val NAME = "Streamed"
    }

    private val sectionNamesList = mainPageOf(
        "$mainUrl/api/matches/live/popular" to "Popular",
        "$mainUrl/api/matches/football" to "Football",
        "$mainUrl/api/matches/baseball" to "Baseball",
        "$mainUrl/api/matches/american-football" to "American Football",
        "$mainUrl/api/matches/hockey" to "Hockey",
        "$mainUrl/api/matches/basketball" to "Basketball",
        "$mainUrl/api/matches/motor-sports" to "Motor Sports",
        "$mainUrl/api/matches/fight" to "Fight",
        "$mainUrl/api/matches/tennis" to "Tennis",
        "$mainUrl/api/matches/rugby" to "Rugby",
        "$mainUrl/api/matches/golf" to "Golf",
        "$mainUrl/api/matches/billiards" to "Billiards",
        "$mainUrl/api/matches/afl" to "AFL",
        "$mainUrl/api/matches/darts" to "Darts",
        "$mainUrl/api/matches/cricket" to "Cricket",
        "$mainUrl/api/matches/other" to "Other",
    )

    override val mainPage = sectionNamesList

    private suspend fun searchResponseBuilder(
        listJson: List<Match>,
        filter: (Match) -> Boolean
    ): List<LiveSearchResponse> {
        return listJson.filter(filter).amap { match ->
            var url = ""
            if (match.matchSources.isNotEmpty()) {
                val sourceName = match.matchSources[0].sourceName
                val id = match.matchSources[0].id
                url = "$mainUrl/api/stream/$sourceName/$id"
            }
            url += "/${match.id}"
            LiveSearchResponse(
                name = match.title,
                url = url,
                apiName = this@Streamed.name,
                posterUrl = "$mainUrl${match.posterPath ?: "/api/images/poster/fallback.webp"}"
            )
        }.filter { it.url.count { char -> char == '/' } > 1 }
    }

    //Get the Homepage
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val rawList = app.get(request.data).text
        val listJson = parseJson<List<Match>>(rawList)
        listJson.amap {
            with(sharedPref?.edit()) {
                this?.putString("${it.id}", it.toJson())
                this?.apply()
            }
        }

        val list = searchResponseBuilder(listJson) { match ->
            match.matchSources.isNotEmpty() && match.popular
        }

        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = list,
                isHorizontalImages = true
            ), false
        )
    }


    // This function gets called when you search for something also
    //This is to get Title, Link, Posters for Homepage carousel
    override suspend fun search(query: String): List<SearchResponse> {
        val allMatches = app.get("$mainUrl/api/matches/all").body.string()
        val allMatchesJson = parseJson<List<Match>>(allMatches)
        val searchResults = searchResponseBuilder(allMatchesJson) { match ->
            match.matchSources.isNotEmpty() && match.title.contains(query, true)
        }
        return searchResults
    }

    private suspend fun Source.getMatch(id: String): Match? {
        val allMatches = app.get("$mainUrl/api/matches/all").body.string()
        val allMatchesJson = parseJson<List<Match>>(allMatches)
        val matchesList = allMatchesJson.filter { match ->
            match.matchSources.isNotEmpty() &&
                    match.matchSources.any { it.id == id && it.sourceName == this.source }
        }
        if (matchesList.isEmpty()) {
            return null
        }
        return matchesList[0]
    }

    // This function gets called when you enter the page/show
    override suspend fun load(url: String): LoadResponse {
        val matchId = url.substringAfterLast('/')
        val trueUrl = url.substringBeforeLast('/')

        var comingSoon = true

        if (trueUrl.toHttpUrlOrNull() == null) {
            throw ErrorLoadingException("The stream is not available")
        }

        val match = sharedPref?.getString(matchId, null)?.let { parseJson<Match>(it) }
            ?: throw ErrorLoadingException("Error loading match from cache")

        val elementName = match.title
        val elementPlot = match.title
        val elementPoster = "$mainUrl${match.posterPath ?: "/api/images/poster/fallback.webp"}"
        val elementTags = arrayListOf(match.category.capitalize())

        try {
            val response = app.get(trueUrl)

            val rawJson = response.body.string()
            val data = parseJson<List<Source>>(rawJson)
//            Log.d("STREAMED:Item", "Sources: $data")

            match.isoDateTime?.let {
                val calendar = Calendar.getInstance()
                calendar.time = Date(it)
                calendar.add(Calendar.MINUTE, -15)
                val matchTimeMinus15 = calendar.time.time

                if (matchTimeMinus15 <= Date().time && data.isNotEmpty()) {
                    comingSoon = false
                }
            }
            if (match.isoDateTime == null && data.isNotEmpty()) {
                comingSoon = false
            }
        } catch (e: Exception) {
            Log.e("STREAMED:Item", "Failed to load sources: $e")
        }

        match.isoDateTime?.let {
            val formatter = SimpleDateFormat("dd MMM yyyy 'at' HH:mm", Locale.getDefault())
            val date = formatter.format(Date(it))
            elementTags.add(date)
        }
        val teams = match.teams?.values?.mapNotNull { it!!.name!! }
        if (teams != null) {
            elementTags.addAll(teams)
        }

        return LiveStreamLoadResponse(
            name = elementName,
            url = trueUrl,
            apiName = this.name,
            dataUrl = trueUrl,
            plot = elementPlot,
            posterUrl = elementPoster,
            tags = elementTags,
            comingSoon = comingSoon
        )
    }

    // This function is how you load the links
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val rawJson = app.get(data).body.string()
        val source = parseJson<List<Source>>(rawJson)[0]

        val sourceUrlID = data.substringAfterLast("/")

        val match = source.getMatch(sourceUrlID)

        match?.matchSources?.forEach { matchSource ->
//            Log.d(
//                "STREAMED:loadLinks",
//                "URLS: $mainUrl/api/stream/${matchSource.sourceName}/${matchSource.id}"
//            )
            loadExtractor(
                url = "$mainUrl/api/stream/${matchSource.sourceName}/${matchSource.id}",
                referer = "https://embedme.top/",
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }
        return true
    }

    @Suppress("ObjectLiteralToLambda")
    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request()
//                Log.d("STREAMED:Interceptor", "Request Headers: ${request.headers}")
                val response = chain.proceed(request)

//                Log.d("STREAMED:Interceptor", "Response Headers: ${response.headers}")
//                Log.d("STREAMED:Interceptor", "Response: $response")

                val url = response.request.url.toString()
                val path = url.substringAfterLast(url.toHttpUrl().host).substringBeforeLast("?id=")
                var isLimited = false
                if (response.code != 200 || !url.contains("?id=")) {
                    val newUrl = runBlocking {
                        getContentUrl(path) {
                            // This code executes only when the response code is 429
                            showToastOnce("You reached the limit of request for this session.")
                            isLimited = true
                            return@getContentUrl
                        }
                    }
                    Log.d("STREAMED:Interceptor", "Response body: ${response.body.string()}")

                    val newRequest = request.newBuilder().url(newUrl).build()
                    if (isLimited){
if(url.contains("?id=")){
                      return chain.proceed(request.newBuilder().url(url).build())
                    }
                        return Response.Builder()
                            .code(429)
                            .message("Rate Limited")
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .request(newRequest)
                            .build()
                    }
                    return chain.proceed(newRequest)
                }

                return response
            }
        }
    }

    fun showToastOnce(message: String) {
        if (canShowToast){
            showToast(message, Toast.LENGTH_LONG)
            canShowToast = false
        }
    }

    data class Match(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("title") val title: String,
        @JsonProperty("category") val category: String,
        @JsonProperty("date") val isoDateTime: Long? = null,
        @JsonProperty("poster") val posterPath: String? = null,
        @JsonProperty("popular") val popular: Boolean = false,
        @JsonProperty("teams") val teams: LinkedHashMap<String, Team?>? = null,
        @JsonProperty("sources") val matchSources: ArrayList<MatchSource> = arrayListOf(),
    )

    data class Team(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("badge") val badge: String? = null,
    )

    data class MatchSource(
        @JsonProperty("source") val sourceName: String,
        @JsonProperty("id") val id: String
    )

    data class Source(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("streamNo") val streamNumber: Int? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("hd") val isHD: Boolean = false,
        @JsonProperty("embedUrl") val embedUrl: String? = null,
        @JsonProperty("source") val source: String? = null,
    )
}
