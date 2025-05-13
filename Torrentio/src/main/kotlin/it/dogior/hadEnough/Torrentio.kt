package it.dogior.hadEnough

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.Qualities


class Torrentio : TmdbProvider() {
    override var mainUrl = "https://torrentio.strem.fun"
    override var name = "Torrentio"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Torrent)
    override var lang = "it"
    override val hasMainPage = true
    private val tmdbAPI = "https://api.themoviedb.org/3"
    private val torrentioUrl =
        "$mainUrl/providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,ilcorsaronero,magnetdl|sort=seeders|language=italian"
    private val TRACKER_LIST_URL = "https://newtrackon.com/api/stable"

    private val apiKey = BuildConfig.TMDB_API
    private val authHeaders =
        mapOf("Authorization" to "Bearer $apiKey")

    override val mainPage = mainPageOf(
        "$tmdbAPI/trending/movie/day?region=IT&language=it-IT" to "Di Tendenza",
        "$tmdbAPI/movie/popular?region=IT&language=it-IT" to "Popolari",
        "$tmdbAPI/movie/top_rated?region=IT&language=it-IT" to "Valutazione più alta",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=28" to "Azione",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=12" to "Avventura",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=16" to "Animazione",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=35" to "Commedia",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=80" to "Crime",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=99" to "Documentario",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=18" to "Drama",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=10751" to "Famiglia",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=878" to "Fantascienza",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=14" to "Fantasy",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=27" to "Horror",
        "$tmdbAPI/discover/movie?language=it-IT&sort_by=popularity.desc&with_origin_country=IT" to "Italiani",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=9648" to "Mistero",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=10749" to "Romantico",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=36" to "Storico",
        "$tmdbAPI/discover/movie?region=IT&language=it-IT&with_genres=53" to "Thriller",
    )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val urlTv = when (request.name) {
            "Azione", "Avventura" -> {
                request.data.replace("movie?", "tv?")
                    .replace(Regex("=\\d+"), "=10759")
            }

            "Fantasy", "Fantascienza" -> {
                request.data.replace("movie?", "tv?")
                    .replace(Regex("=\\d+"), "=10765")
            }

            "Di Tendenza", "Popolari",
            "Valutazione più alta" -> {
                request.data.replace("/movie/", "/tv/")
            }

            "Horror", "Thriller" -> {
                null
            }

            else -> {
                request.data.replace("movie?", "tv?")
            }
        }

        val resp = app.get("${request.data}&page=$page", headers = authHeaders).body.string()
        val respTv = urlTv?.let { app.get("${it}&page=$page", headers = authHeaders).body.string() }
        val joinedResponses = parseJson<Results>(resp).results?.mapNotNull { media ->
            media.toSearchResponse(type = "movie")
        }?.toMutableList()

        if (respTv != null) {
            val parsedResponse = parseJson<Results>(respTv).results
            val searchResponses = parsedResponse?.mapNotNull { media ->
                media.toSearchResponse(type = "tv")
            }
            searchResponses?.let {
                joinedResponses?.addAll(it)
            }
        }

        val home = joinedResponses ?: throw ErrorLoadingException("Invalid Json reponse")
        return newHomePageResponse(request.name, home)
    }


    override suspend fun search(query: String): List<SearchResponse>? {
        return app.get(
            "$tmdbAPI/search/multi?language=it-IT&query=$query&page=1&include_adult=true",
            headers = authHeaders
        ).parsedSafe<Results>()?.results?.mapNotNull { media ->
            media.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = parseJson<Data>(url)
        val type = if (data.type == "movie") TvType.Movie else TvType.TvSeries
        val append = "alternative_titles,credits,external_ids,keywords,videos,recommendations"

        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?language=it-IT&append_to_response=$append"
        } else {
            "$tmdbAPI/tv/${data.id}?language=it-IT&append_to_response=$append"
        }
        val res = app.get(resUrl, headers = authHeaders).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")
        Log.d("Torrentio", res.toJson())

        val title = res.title ?: res.name ?: return null
        val poster = getImageUrl(res.posterPath, getOriginal = true)
        val bgPoster = getImageUrl(res.backdropPath, getOriginal = true)
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.first()?.toIntOrNull()
        val rating = res.voteAverage.toString().toRatingInt()
        val genres = res.genres?.mapNotNull { it.name }

        val actors = res.credits?.cast?.mapNotNull { cast ->
            val name = cast.name ?: cast.originalName ?: return@mapNotNull null
            ActorData(
                Actor(name, getImageUrl(cast.profilePath)),
                roleString = cast.character
            )
        } ?: emptyList()

        val recommendations =
            res.recommendations?.results?.mapNotNull { media -> media.toSearchResponse() }

        val trailer = res.videos?.results?.filter { it.type == "Trailer" }
            ?.map { "https://www.youtube.com/watch?v=${it.key}" }?.reversed().orEmpty()
            .ifEmpty { res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" } }

        return if (type == TvType.Movie) {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LinkData(
                    data.id,
                    type = data.type,
                    title = title,
                    year = year,
                    imdbId = res.imdbId,
                    airedDate = res.releaseDate
                        ?: res.firstAirDate,
                ).toJson(),
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = genres
                this.rating = rating
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
                addTMDbId(data.id.toString())
            }
        } else {
            val episodes = getEpisodes(res, data.id)
            Log.d("BANANA", "IMDB: ${res.imdbId}       Ext: ${res.externalIds?.imdbId}")
            newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                episodes,
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.tags = genres
                this.rating = rating
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
                addTMDbId(data.id.toString())
            }
        }
    }

    private suspend fun getEpisodes(showData: MediaDetail, id: Int?): List<Episode> {
        val episodes = showData.seasons?.mapNotNull { season ->
            app.get(
                "$tmdbAPI/tv/${showData.id}/season/${season.seasonNumber}",
                headers = authHeaders
            ).parsedSafe<MediaDetailEpisodes>()?.episodes?.map { ep ->
                newEpisode(
                    LinkData(
                        id,
                        type = "tv",
                        season = ep.seasonNumber,
                        episode = ep.episodeNumber,
                        epid = ep.id,
                        title = showData.title,
                        year = season.airDate?.split("-")?.first()?.toIntOrNull(),
                        epsTitle = ep.name,
                        date = season.airDate,
                        imdbId = showData.imdbId ?: showData.externalIds?.imdbId
                    ).toJson()
                ) {
                    this.name = ep.name
                    this.season = ep.seasonNumber
                    this.episode = ep.episodeNumber
                    this.posterUrl = getImageUrl(ep.stillPath)
                    this.description = ep.overview
                }
            }
        }?.flatten()
        return episodes ?: emptyList()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        Log.d("BANANA", data)
        val show = parseJson<LinkData>(data)
        var success = false
        val url = if (show.season == null) {
            "$torrentioUrl/stream/movie/${show.imdbId}.json"
        } else {
            "$torrentioUrl/stream/series/${show.imdbId}:${show.season}:${show.episode}.json"
        }
        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        )
        Log.d("Torrentio", url)
        Log.d("Torrentio", data)
        val res = app.get(url, headers = headers, timeout = 100L)
        val body = res.body.string()
        Log.d("Torrentio", body)
        val response = parseJson<TorrentioResponse>(body)

        response.streams.forEach { stream ->
            val formattedTitleName = stream.title
                ?.let { title ->
                    val tags = "\\[(.*?)]".toRegex().findAll(title)
                        .map { match -> "[${match.groupValues[1]}]" }
                        .joinToString(" | ")
                    val seeder = "👤\\s*(\\d+)".toRegex().find(title)?.groupValues?.get(1) ?: "0"
                    val provider =
                        "⚙️\\s*([^\\\\]+)".toRegex().find(title)?.groupValues?.get(1)?.trim()
                            ?: "Unknown"
                    "Torrentio | $tags | Seeder: $seeder | Provider: $provider".trim()
                }
            val magnet = generateMagnetLink(TRACKER_LIST_URL, stream.infoHash)
            if (magnet.isNotEmpty()) success = true
            callback.invoke(
                newExtractorLink(
                    "Torrentio",
                    formattedTitleName ?: stream.name ?: "",
                    url = magnet,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = getIndexQuality(stream.name)
                }
            )
        }
        return success
    }

    private suspend fun generateMagnetLink(url: String, hash: String?): String {
        val response = app.get(url)

        val trackerList = response.text.trim().split("\n") // Assuming each tracker is on a new line

        // Build the magnet link
        return buildString {
            append("magnet:?xt=urn:btih:$hash")
            trackerList.forEach { tracker ->
                if (tracker.isNotBlank()) {
                    append("&tr=").append(tracker.trim())
                }
            }
        }
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private fun getImageUrl(link: String?, getOriginal: Boolean = false): String? {
        if (link == null) return null
        val width = if (getOriginal) "original" else "w500"
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/$width/$link" else link
    }

    private fun Media.toSearchResponse(type: String = "tv"): SearchResponse? {
        if (mediaType == "person") return null
        return newMovieSearchResponse(
            title ?: name ?: originalTitle ?: return null,
            Data(id = id, type = mediaType ?: type).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = getImageUrl(posterPath)
        }
    }
}