@file:Suppress("PackageName")

package it.dogior.doesStream

import com.fasterxml.jackson.annotation.JsonProperty
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
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.Headers
import org.jsoup.nodes.DataNode
import java.io.File


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
    )

    override val mainPage = sectionNamesList

    private fun searchResponseBuilder(
        listJson: List<Title>
    ): List<SearchResponse> {
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
        val section = request.data.substringAfterLast("/")
        val url: String = request.data
        var params = emptyMap<String, String>().toMutableMap()
        /*        when (section) {
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
                }*/
        if (page > 0) {
            params["offset"] = ((page - 1) * 60).toString()
        }
        Log.d(TAG, "Url: $url")
        Log.d(TAG, "Params: $params")
        val response = app.get(url, params = params)
        val responseString = response.body.string()
        val responseJson = parseJson<Sezione>(responseString)
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
    override suspend fun search(query: String): List<SearchResponse>? {
//        val slug = if (genreFilter.state != 0) {
//            "browse/genre?g=${URLEncoder.encode(genreFilter.toUriPart(), "utf-8")}"
//        } else {
//            "search?q=$query"
//        }
//
//        return if (page == 1) {
//            app.get("$mainUrl/$slug")
//        } else {
//            val apiHeaders = headers.newBuilder()
//                .add("Accept", "application/json, text/plain, */*")
//                .add("Host", baseUrl.toHttpUrl().host)
//                .add("Referer", "$mainUrl/$slug")
//                .build()
//            app.get("$mainUrl/api/$slug&offset=${(page - 1) * 60}", headers = apiHeaders)
//        }
        return null
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

    data class Sezione(
        @JsonProperty("name") val name: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("titles") val titles: List<Title>,
    )

    data class Title(
        @JsonProperty("id") val id: Int,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("score") val score: String,
        @JsonProperty("sub_ita") val subIta: Int,
        @JsonProperty("last_air_date") val lastAirDate: String,
        @JsonProperty("age") val age: Int?, // Can be null or another type, modify if more info is available
        @JsonProperty("seasons_count") val seasonsCount: Int,
        @JsonProperty("images") val images: List<PosterImage>
    ) {
        fun getPoster(): String? {
            this.images.forEach {
                if (it.type == "poster") {
                    return it.filename
                }
            }
            return null
        }
    }


    data class PosterImage(
        @JsonProperty("filename") val filename: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("imageable_type") val imageableType: String,
        @JsonProperty("imageable_id") val imageableId: Int,
    )

    data class Pivot(
        @JsonProperty("title_id") val titleId: Int,
        @JsonProperty("genre_id") val genreId: Int
    )

    data class Genre(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("hidden") val hidden: Int,
        @JsonProperty("created_at") val createdAt: String,
        @JsonProperty("updated_at") val updatedAt: String,
        @JsonProperty("pivot") val pivot: Pivot
    )

    data class TitlePreview(
        @JsonProperty("id") val id: Int,
        @JsonProperty("type") val type: String,
        @JsonProperty("runtime") val runtime: Int,
        @JsonProperty("release_date") val releaseDate: String,
        @JsonProperty("quality") val quality: String,
        @JsonProperty("plot") val plot: String,
        @JsonProperty("seasons_count") val seasonsCount: Int,
        @JsonProperty("genres") val genres: List<Genre>,
        @JsonProperty("images") val images: List<PosterImage>
    ) {
        fun getBackgroundImage(): String? {
            this.images.forEach {
                if (it.type == "background") {
                    return it.filename
                }
            }
            return null
        }
    }

    // Data class copied from the StreamingCommunity plugin for Aniyomi because I'm lazy
    data class SingleShowResponse(
        val props: SingleShowObject,
        val version: String? = null,
    ) {
        data class SingleShowObject(
            val title: ShowObject? = null,
            val loadedSeason: LoadedSeasonObject? = null,
        ) {
            data class ShowObject(
                val id: Int,
                val plot: String? = null,
                val status: String? = null,
                val seasons: List<SeasonObject>,
                val genres: List<GenreObject>? = null,
            ) {
                data class SeasonObject(
                    val id: Int,
                    val number: Int,
                )
                data class GenreObject(
                    val name: String,
                )
            }
            data class LoadedSeasonObject(
                val id: Int,
                val episodes: List<EpisodeObject>,
            ) {
                data class EpisodeObject(
                    val id: Int,
                    val number: Int,
                    val name: String,
                )
            }
        }
    }
}
