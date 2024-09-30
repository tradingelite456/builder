@file:Suppress("PackageName")

package it.dogior.doesStream

import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
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
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink


class StreamingCommunity : MainAPI() {
    override var mainUrl = Companion.mainUrl
    override var name = Companion.name
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "it"
    override val hasMainPage = true


    companion object {
        val mainUrl = "https://streamingcommunity.computer"
        var name = "StreamingCommunity"
    }

    private val sectionNamesList = mainPageOf(
        "$mainUrl/api/browse/top10" to "Top 10 di oggi",
        "$mainUrl/api/browse/trending" to "I Titoli Del Momento",
        "$mainUrl/api/browse/latest" to "Aggiunti di Recente",
        "$mainUrl/api/browse/genre@Animazione" to "Animazione",
        "$mainUrl/api/browse/genre@Avventura" to "Avventura",
        "$mainUrl/api/browse/genre@Azione" to "Azione",
        "$mainUrl/api/browse/genre@Commedia" to "Commedia",
        "$mainUrl/api/browse/genre@Crime" to "Crime",
        "$mainUrl/api/browse/genre@Documentario" to "Documentario",
        "$mainUrl/api/browse/genre@Dramma" to "Dramma",
        "$mainUrl/api/browse/genre@Famiglia" to "Famiglia",
        "$mainUrl/api/browse/genre@Fantascienza" to "Fantascienza",
        "$mainUrl/api/browse/genre@Fantasy" to "Fantasy",
        "$mainUrl/api/browse/genre@Horror" to "Horror",
        "$mainUrl/api/browse/genre@Reality" to "Reality",
        "$mainUrl/api/browse/genre@Romance" to "Romance",
        "$mainUrl/api/browse/genre@Thriller" to "Thriller",
    )
    override val mainPage = sectionNamesList

    private fun searchResponseBuilder(listJson: List<Title>): List<SearchResponse> {
        val domain = mainUrl.substringAfter("://")
        val list: List<SearchResponse> =
            listJson.filter { it.type == "movie" || it.type == "tv" }.map { title ->
                val itemData = "@${title.name}§${title.score}·${title.slug}"

                if (title.type == "tv") {
                    TvSeriesSearchResponse(
                        name = title.name,
                        url = "$mainUrl/api/titles/preview/${title.id}$itemData",
                        apiName = this@StreamingCommunity.name,
                        posterUrl = "https://cdn.$domain/images/" + title.getPoster(),

                        )
                } else {
                    MovieSearchResponse(
                        name = title.name,
                        url = "$mainUrl/api/titles/preview/${title.id}$itemData",
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
        var url: String = request.data
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
//                url = request.data.substringBeforeLast("m")
//                params["type"] = "movie"
                Log.d(TAG, "TOP10")
            }
            else -> {
                val genere = url.substringAfterLast('@')
                url = url.substringBeforeLast('@')
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
        val titlesList = mutableListOf<Title>()

        val response = app.get(url, params = params).body.string()
        Log.d(TAG, "Response: $response")
        val result = parseJson<SearchData>(response)
        titlesList.addAll(result.titles)

        return searchResponseBuilder(titlesList)
    }

    // This function gets called when you enter the page/show
    override suspend fun load(url: String): LoadResponse {
        val TAG = "STREAMINGCOMMUNITY:Item"
//        Log.d(TAG, "Url: $url")
        val name = url.substringAfterLast('@').substringBeforeLast('§')
        val imdbScore = url.substringAfterLast('§').substringBeforeLast('·')
        val slug = url.substringAfterLast('·')
        val previewUrl = url.substringBeforeLast('@')
        Log.d(TAG, "URL with data: $url")
        Log.d(TAG, "Name: $name")
        Log.d(TAG, "IMDB: $imdbScore")
        Log.d(TAG, "SLUG: $slug")
        Log.d(TAG, "Url: $previewUrl")
        val response = app.post(previewUrl).body.string()
        Log.d(TAG, "Title Preview: $response")
        val title = parseJson<TitlePreview>(response)
        val genres = title.genres.map { it.name.capitalize() }
        val domain = mainUrl.substringAfter("://")
        if (title.type == "tv") {
            val episodes: List<Episode> = getEpisodes(title.id, slug)
            Log.d(TAG, "Episode List: $episodes")

            val tvShow = TvSeriesLoadResponse(
                name = name,
                url = previewUrl,
                type = TvType.TvSeries,
                apiName = this.name,
                plot = title.plot,
                posterUrl = "https://cdn.$domain/images/" + title.getBackgroundImage(),
                tags = listOf("IMDB: $imdbScore") + genres,
                episodes = episodes
            )
            Log.d(TAG, "TV Show: $tvShow")
            return tvShow
        } else {
            val movie = MovieLoadResponse(
                name = name,
                url = previewUrl,
                dataUrl = previewUrl,
                type = TvType.Movie,
                apiName = this.name,
                plot = title.plot,
                posterUrl = "https://cdn.$domain/images/" + title.getBackgroundImage(),
                tags = listOf("IMDB: $imdbScore") + genres
            )

            Log.d(TAG, "Movie: $movie")
            return movie
        }
    }

    private suspend fun getEpisodes(id: Int, slug: String): List<Episode> {
        val TAG = "STREAMINGCOMMUNITY:getEpisodes"

        val episodeList = mutableListOf<Episode>()

        val url = "$mainUrl/titles/$id-$slug"
        val response = app.get(url).document.select("#app").attr("data-page")
        Log.d(TAG, "Response data: $response")
        val data = parseJson<SingleShowResponse>(response)
        val props = data.props
        val loadedSeason = props.loadedSeason
        Log.d(TAG, "Props: $props")
        Log.d(TAG, "Loaded Season: $loadedSeason")

        props.title?.seasons?.forEach { season ->
            val episodes = if (season.id == props.loadedSeason!!.id) {
                props.loadedSeason.episodes
            } else{
                val r = app.get(url + "/stagione-${season.number}")
                parseJson<SingleShowResponse>(r.document.select("#app").attr("data-page")).props.loadedSeason!!.episodes
            }

            episodes.forEach { ep ->
                episodeList.add(newEpisode("$mainUrl/watch/${season.id}?e=${ep.id}"){
                    this.name = ep.name
                    this.season = season.number
                    this.episode = ep.number
                })
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

        return true
    }
}
