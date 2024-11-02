@file:Suppress("PackageName")

package it.dogior.doesStream

import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.CommonActivity.activity
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
import com.lagradost.cloudstream3.app

import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale


class Streamed : MainAPI() {
    override var mainUrl = Companion.mainUrl
    override var name = Companion.name
    override var supportedTypes = setOf(TvType.Live)
    override var lang = "it"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    private val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)


    companion object {
        val mainUrl = "https://streamed.su"
        var name = "Streamed"
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
        return listJson.filter(filter).map { match ->
            var url = ""
            if (match.matchSources.isNotEmpty()) {
                val sourceName = match.matchSources[0].sourceName
                val id = match.matchSources[0].id
                url = "$mainUrl/api/stream/$sourceName/$id"
                if (!app.get(url).isSuccessful) {
                    url = ""
                }
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

    /**
     * Gets the sources from the API
     */
    private suspend fun Match.getSources(): Map<String, List<Source>> {

        val sourceObjectList: LinkedHashMap<String, List<Source>> = linkedMapOf()
        this.matchSources.forEach { (source, id) ->
            sourceObjectList[source] =
                parseJson<List<Source>>(app.get("$mainUrl/api/stream/${source}/$id").body.string())
        }

        return sourceObjectList
    }


    //Get the Homepage
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val TAG = "STREAMED:MainPage"
        Log.d(TAG, "Url:${request.data}")
        val rawList = app.get(request.data).text
        val listJson = parseJson<List<Match>>(rawList)
        listJson.forEach {
            with(sharedPref?.edit()) {
                this?.putString("${it.id}", it.toJson())
                this?.apply()
            }
        }

        Log.d(TAG, "Element List: $listJson")
        if (listJson.isNotEmpty()) {
            Log.d(TAG, "Element: ${listJson[0]}")
            Log.d(TAG, "Teams: ${listJson[0].teams}")
            Log.d(TAG, "Sources: ${listJson[0].getSources().values}")
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
    //This is to get Title,Href,Posters for Homepage
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
        val TAG = "STREAMED:Item"

        val matchId = url.substringAfterLast('/')
        val trueUrl = url.substringBeforeLast('/')

        var comingSoon = true

        if (trueUrl.toHttpUrlOrNull() == null) {
            throw ErrorLoadingException("The stream is not available")
        }

        val match = sharedPref?.getString(matchId, null)?.let { parseJson<Match>(it) }
            ?: throw ErrorLoadingException("Error loading match from cache")

        val elementName = match.title
        var elementPlot = match.title
        val elementPoster = "$mainUrl${match.posterPath ?: "/api/images/poster/fallback.webp"}"
        val elementTags = arrayListOf(match.category.capitalize())

        val teams = match.teams?.values?.mapNotNull { it!!.name!! }
        if (teams != null) {
            elementTags.addAll(teams)
        }

        try {
            val response = app.get(trueUrl)

            val rawJson = response.body.string()
            val data = parseJson<List<Source>>(rawJson)
            Log.d(TAG, "Sources: $data")

            if (data.isNotEmpty() && match.isoDateTime!! < Date().time) {
                comingSoon = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load sources: $e")
        }
        if (match.isoDateTime!! > Date().time) {

            val formatter =
                DateFormat.getDateTimeInstance(
                    DateFormat.DEFAULT,
                    DateFormat.SHORT,
                    Locale.getDefault()
                )
            val date = formatter.format(Date(match.isoDateTime))
            elementPlot += " | This stream is scheduled for $date"
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
        val TAG = "STREAMED:Links"

//        Log.d(TAG, "Url : $data")
        val rawJson = app.get(data).body.string()
        val source = parseJson<List<Source>>(rawJson)[0]

        val sourceUrlID = data.substringAfterLast("/")
//        Log.d(TAG, "Source ID: $sourceUrlID")

        val match = source.getMatch(sourceUrlID)
//        Log.d(TAG, "Match: $match")

        match?.matchSources?.forEach { matchSource ->
            Log.d(TAG, "URLS: $mainUrl/api/stream/${matchSource.sourceName}/${matchSource.id}")
            StreamedExtractor().getUrl(
                url = "$mainUrl/api/stream/${matchSource.sourceName}/${matchSource.id}",
                referer = "https://embedme.top/",
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }
        return true
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
