@file:Suppress("PackageName")

package it.dogior.doesStream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
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
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink


class StreamingCommunity : MainAPI() {
    override var mainUrl = Companion.mainUrl
    override var name = Companion.name
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "it"
    override val hasMainPage = true
    private val headers =
        mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:96.0) Gecko/20100101 Firefox/96.0")


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
        val list: List<SearchResponse> = listJson.filter{ it.type == "movie" || it.type == "tv"}.map { title ->
            val itemData = "@${title.name}§${title.score}·${title.slug}"
            if (title.type == "tv"){
                TvSeriesSearchResponse(
                    name = title.name,
                    url = "$mainUrl/api/titles/preview/${title.id}$itemData",
                    apiName = this@StreamingCommunity.name,
                    posterUrl = "https://cdn.streamingcommunity.computer/images/" + title.getPoster(),

                )
            } else {
                MovieSearchResponse(
                    name = title.name,
                    url = "$mainUrl/api/titles/preview/${title.id}$itemData",
                    apiName = this@StreamingCommunity.name,
                    posterUrl = "https://cdn.streamingcommunity.computer/images/" + title.getPoster(),
                )
            }
        }
        return list
    }


    //Get the Homepage
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val TAG = "STREAMINGCOMMUNITY:MainPage"
        val section = request.data.substringAfterLast("/")
        var url: String = request.data
        var params = emptyMap<String, String>().toMutableMap()
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
        }
        if (page > 0) {
            params["offset"] = ((page - 1) * 60).toString()
        }
        Log.d(TAG, "Url: $url")
        Log.d(TAG, "Params: $params")
        val response = app.get(url, params = params, headers = headers)
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
        val previewUrl = url.substringBeforeLast('·')
        Log.d(TAG, "Name: $name")
        Log.d(TAG, "IMDB: $imdbScore")
        Log.d(TAG, "SLUG: $slug")
        Log.d(TAG, "Url: $previewUrl")
        val title = parseJson<TitlePreview>(app.post(previewUrl).body.string())

        if (title.type == "tv") {
            val episodes: List<Episode> = getEpisodes(title.id, slug)

            val tvShow = TvSeriesLoadResponse(
                name = name,
                url = previewUrl,
                type = TvType.TvSeries,
                apiName = this.name,
                plot = title.plot,
                posterUrl = "https://cdn.streamingcommunity.computer/images/" + title.getBackgroundImage(),
                tags = listOf("IMDB: $imdbScore"),
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
                posterUrl = "https://cdn.streamingcommunity.computer/images/" + title.getBackgroundImage(),
                tags = listOf("IMDB: $imdbScore"),
            )

            Log.d(TAG, "Movie: $movie")
            return movie
        }
    }

    private suspend fun getEpisodes(id: Int, slug: String): List<Episode> {
//        val episode = Episode(
//
//        )
        return emptyList()
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
        @JsonProperty("sub_ita") val sub_ita: Int,
        @JsonProperty("last_air_date") val last_air_date: String,
        @JsonProperty("age") val age: Int?, // Can be null or another type, modify if more info is available
        @JsonProperty("seasons_count") val seasons_count: Int,
        @JsonProperty("images") val images: List<PosterImage>
    ){
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
        @JsonProperty("imageable_type") val imageable_type: String,
        @JsonProperty("imageable_id") val imageable_id: Int,
    )

    data class Pivot(
        @JsonProperty("title_id") val title_id: Int,
        @JsonProperty("genre_id") val genre_id: Int
    )

    data class Genre(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("hidden") val hidden: Int,
        @JsonProperty("created_at") val created_at: String,
        @JsonProperty("updated_at") val updated_at: String,
        @JsonProperty("pivot") val pivot: Pivot
    )

    data class TitlePreview(
        @JsonProperty("id") val id: Int,
        @JsonProperty("type") val type: String,
        @JsonProperty("runtime") val runtime: Int,
        @JsonProperty("release_date") val release_date: String,
        @JsonProperty("quality") val quality: String,
        @JsonProperty("plot") val plot: String,
        @JsonProperty("seasons_count") val seasons_count: Int,
        @JsonProperty("genres") val genres: List<Genre>,
        @JsonProperty("images") val images: List<PosterImage>
    )
    {
        fun getBackgroundImage(): String? {
            this.images.forEach {
                if (it.type == "cover") {
                    return it.filename
                }
            }
            return null
        }
    }
}
