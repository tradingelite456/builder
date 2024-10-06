@file:Suppress("PackageName")

package it.dogior.doesStream

import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app

import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink


class StreamingCommunity : MainAPI() {
    override var mainUrl = Companion.mainUrl
    override var name = Companion.name
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "it"
    override val hasMainPage = true
    private var inertiaVersion = ""
    private val headers =
        mapOf("X-Inertia" to true.toString(), "X-Inertia-Version" to inertiaVersion).toMutableMap()


    companion object {
        val mainUrl = "https://streamingcommunity.computer"
        var name = "StreamingCommunity"
    }

    private val sectionNamesList = mainPageOf(
        "$mainUrl/browse/top10" to "Top 10 di oggi",
        "$mainUrl/browse/trending" to "I Titoli Del Momento",
        "$mainUrl/browse/latest" to "Aggiunti di Recente",
        "$mainUrl/browse/genre?g=Animazione" to "Animazione",
        "$mainUrl/browse/genre?g=Avventura" to "Avventura",
        "$mainUrl/browse/genre?g=Azione" to "Azione",
        "$mainUrl/browse/genre?g=Commedia" to "Commedia",
        "$mainUrl/browse/genre?g=Crime" to "Crime",
        "$mainUrl/browse/genre?g=Documentario" to "Documentario",
        "$mainUrl/browse/genre?g=Dramma" to "Dramma",
        "$mainUrl/browse/genre?g=Famiglia" to "Famiglia",
        "$mainUrl/browse/genre?g=Fantascienza" to "Fantascienza",
        "$mainUrl/browse/genre?g=Fantasy" to "Fantasy",
        "$mainUrl/browse/genre?g=Horror" to "Horror",
        "$mainUrl/browse/genre?g=Reality" to "Reality",
        "$mainUrl/browse/genre?g=Romance" to "Romance",
        "$mainUrl/browse/genre?g=Thriller" to "Thriller",
    )
    override val mainPage = sectionNamesList

    private suspend fun setInertiaVersion() {
        val inertiaPageObject = app.get(mainUrl).document.select("#app").attr("data-page")
        this.inertiaVersion =
            inertiaPageObject.substringAfter("\"version\":\"").substringBefore("\"")
        this.headers["X-Inertia-Version"] = this.inertiaVersion
    }

    private fun searchResponseBuilder(listJson: List<Title>): List<SearchResponse> {
        val domain = mainUrl.substringAfter("://")
        val list: List<SearchResponse> =
            listJson.filter { it.type == "movie" || it.type == "tv" }.map { title ->
                val url = "$mainUrl/titles/${title.id}-${title.slug}"

                if (title.type == "tv") {
                    TvSeriesSearchResponse(
                        name = title.name,
                        url = url,
                        apiName = this@StreamingCommunity.name,
                        posterUrl = "https://cdn.$domain/images/" + title.getPoster(),
                    )
                } else {
                    MovieSearchResponse(
                        name = title.name,
                        url = url,
                        apiName = this@StreamingCommunity.name,
                        posterUrl = "https://cdn.$domain/images/" + title.getPoster(),
                    )
                }
            }
        return list
    }

    //Get the Homepage
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val TAG = "STREAMINGCOMMUNITY:MainPage"
        var url: String = mainUrl + "/api" + request.data.substringAfter(mainUrl)
        val params = emptyMap<String, String>().toMutableMap()

        val section = request.data.substringAfterLast("/")
        when (section) {
            "trending" -> {
                Log.d(TAG, "TRENDING")
            }

            "latest" -> {
                Log.d(TAG, "LATEST")
            }

            "top10" -> {
                Log.d(TAG, "TOP10")
            }

            else -> {
                val genere = url.substringAfterLast('=')
                url = url.substringBeforeLast('?')
                params["g"] = genere
            }
        }

        if (page > 0) {
            params["offset"] = ((page - 1) * 60).toString()
        }
        Log.d(TAG, "Url: $url")
        Log.d(TAG, "Params: $params")
        val response = app.get(url, params = params)
        val responseString = response.body.string()
        val responseJson = parseJson<Section>(responseString)
        Log.d(TAG, "Response: $responseJson")

        val titlesList = searchResponseBuilder(responseJson.titles)

        val hasNextPage =
            response.okhttpResponse.request.url.queryParameter("offset")?.toIntOrNull()
                ?.let { it < 120 } ?: true && titlesList.size == 60

        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = titlesList,
                isHorizontalImages = false
            ), hasNextPage
        )
    }

    // This function gets called when you search for something also
    //This is to get Title,Href,Posters for Homepage
    override suspend fun search(query: String): List<SearchResponse> {
        val TAG = "STREAMINGCOMMUNITY:search"
        val url = "$mainUrl/api/search"
        val params = mapOf("q" to query)

        val response = app.get(url, params = params).body.string()
        Log.d(TAG, "Response: $response")
        val result = parseJson<SearchData>(response)

        return searchResponseBuilder(result.titles)
    }

    // This function gets called when you enter the page/show
    override suspend fun load(url: String): LoadResponse {
        val TAG = "STREAMINGCOMMUNITY:Item"

        Log.d(TAG, "URL: $url")
        if (this.inertiaVersion == "") {
            setInertiaVersion()
        }

        val response = app.get(url, headers = this.headers)
        val responseBody = response.body.string()
        Log.d(TAG, "Request: ${response.okhttpResponse.request}")
        Log.d(TAG, "Response: $responseBody")

        val props = parseJson<InertiaResponse>(responseBody).props
        val title = props.title!!
        val genres = title.genres.map { it.name.capitalize() }
        val domain = mainUrl.substringAfter("://")
        val tags = listOf("IMDB: ${title.score}") + genres
        val actors = title.mainActors.map { ac -> ActorData(actor = Actor(ac.name)) }
        val year = title.releaseDate.substringBefore('-').toIntOrNull()
        val related = props.sliders?.get(0)
        if (title.type == "tv") {
            val episodes: List<Episode> = getEpisodes(props)
            Log.d(TAG, "Episode List: $episodes")

            val tvShow = TvSeriesLoadResponse(
                name = title.name,
                url = url,
                type = TvType.TvSeries,
                apiName = this.name,
                plot = title.plot,
                posterUrl = "https://cdn.$domain/images/" + title.getBackgroundImage(),
                tags = tags,
                episodes = episodes,
                actors = actors,
                year = year,
                recommendations = related?.titles?.let { searchResponseBuilder(it) }
            )
            Log.d(TAG, "TV Show: $tvShow")
            return tvShow
        } else {
            val movie = MovieLoadResponse(
                name = title.name,
                url = url,
                dataUrl = "$mainUrl/iframe/${title.id}&canPlayFHD=1",
                type = TvType.Movie,
                apiName = this.name,
                plot = title.plot,
                posterUrl = "https://cdn.$domain/images/" + title.getBackgroundImage(),
                tags = tags,
                actors = actors,
                year = year,
//                comingSoon = title.status == "Post Production",
                recommendations = related?.titles?.let { searchResponseBuilder(it) }
            )

            Log.d(TAG, "Movie: $movie")
            return movie
        }
    }

    private suspend fun getEpisodes(props: Props): List<Episode> {
        val TAG = "STREAMINGCOMMUNITY:getEpisodes"

        val episodeList = mutableListOf<Episode>()
        val title = props.title

        title?.seasons?.forEach { season ->
            val responseEpisodes = emptyList<it.dogior.doesStream.Episode>().toMutableList()
            if (season.id == props.loadedSeason!!.id) {
                responseEpisodes.addAll(props.loadedSeason.episodes!!)
            } else {
                if (this.inertiaVersion == "") {
                    setInertiaVersion()
                }
                val url = "$mainUrl/titles/${title.id}-${title.slug}/stagione-${season.number}"
                val obj =
                    parseJson<InertiaResponse>(app.get(url, headers = this.headers).body.string())
                Log.d(TAG, "Parsed Response: $obj")
                responseEpisodes.addAll(obj.props.loadedSeason?.episodes!!)
            }
            responseEpisodes.forEach { ep ->
                episodeList.add(
                    Episode(
                        data = "$mainUrl/iframe/${title.id}?episode_id=${ep.id}&canPlayFHD=1",
                        name = ep.name,
                        posterUrl = props.cdnUrl + "/images/" + ep.getCover(),
                        description = ep.plot,
                        episode = ep.number,
                        season = season.number
                    )
                )
            }
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
        val TAG = "STREAMINGCOMMUNITY:Links"

        Log.d(TAG, "Url : $data")

        StreamingCommunityExtractor().getUrl(
            url = data,
            referer = mainUrl,
            subtitleCallback = subtitleCallback,
            callback = callback
        )
        return false
    }
}
