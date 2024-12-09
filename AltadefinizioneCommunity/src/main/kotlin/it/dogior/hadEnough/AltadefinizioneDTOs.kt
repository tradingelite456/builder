package it.dogior.hadEnough

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addQuality
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import it.dogior.hadEnough.AltadefinizioneCommunity.Companion.URL

val movieCategories = mapOf(
    "1" to "Film",
    "734" to "Medical-Drama",
    "735" to "Teen-Drama",
    "754" to "Oscar 2023 \ud83c\udfc6\u200a",
    "756" to "Oscar 2024 \ud83c\udfc6\u200a",
    "755" to "Novità 4K",
    "738" to "Netflix",
    "739" to "Disney+",
    "740" to "Prime Video",
    "741" to "Apple TV",
    "751" to "Sky",
    "31" to "Al cinema",
    "744" to "Anime",
    "18" to "Animazione",
    "745" to "Animazione per bambini",
    "746" to "Animazione per ragazzi",
    "747" to "Animazione per adulti",
    "3" to "Nuove uscite",
    "16" to "Avventura",
    "12" to "Azione",
    "6" to "Biografico",
    "4" to "Commedia",
    "32" to "CORTO",
    "10" to "Crimine",
    "17" to "Documentario",
    "7" to "Drammatico",
    "27" to "Erotico",
    "21" to "Familiare",
    "13" to "Fantascienza",
    "20" to "Fantasy",
    "29" to "Gangster",
    "22" to "Guerra",
    "14" to "Horror",
    "23" to "Mistero",
    "750" to "Musica",
    "26" to "Noir",
    "28" to "Poliziesco",
    "11" to "Romantico",
    "8" to "Sportivo",
    "15" to "Storico",
    "731" to "Supereroi",
    "2" to "Thriller",
    "19" to "Western",
    "9" to "SUB-ITA",
    "725" to "MUTO"
)
val tvShowCategories = mapOf(
    "30" to "Serie TV",
    "738" to "Netflix",
    "739" to "Disney+",
    "740" to "Prime Video",
    "741" to "Apple TV",
    "751" to "Sky",
    "733" to "Serie italiane",
    "744" to "Anime",
    "18" to "Animazione",
    "745" to "Animazione per bambini",
    "746" to "Animazione per ragazzi",
    "747" to "Animazione per adulti",
    "16" to "Avventura",
    "12" to "Azione",
    "6" to "Biografico",
    "4" to "Commedia",
    "32" to "CORTO",
    "10" to "Crimine",
    "17" to "Documentario",
    "7" to "Drammatico",
    "734" to "Medical-Drama",
    "736" to "Legal-Drama",
    "735" to "Teen-Drama",
    "27" to "Erotico",
    "21" to "Familiare",
    "13" to "Fantascienza",
    "20" to "Fantasy",
    "29" to "Gangster",
    "22" to "Guerra",
    "14" to "Horror",
    "23" to "Mistero",
    "750" to "Musica",
    "26" to "Noir",
    "28" to "Poliziesco",
    "11" to "Romantico",
    "8" to "Sportivo",
    "15" to "Storico",
    "731" to "Supereroi",
    "2" to "Thriller",
    "19" to "Western",
    "9" to "SUB-ITA"
)
val tvProgramCategories = mapOf(
    "748" to "Programmi TV",
    "749" to "Reality",
    "752" to "Cucina",
    "738" to "Netflix",
    "739" to "Disney+",
    "740" to "Prime Video",
    "751" to "Sky",
    "16" to "Avventura",
    "4" to "Commedia",
    "10" to "Crimine",
    "17" to "Documentario",
    "3" to "Nuove uscite",
    "21" to "Familiare",
    "14" to "Horror",
    "750" to "Musica",
    "8" to "Sportivo",
    "15" to "Storico"
)


data class Response(
    @JsonProperty("status") val status: Boolean,
    @JsonProperty("k") val key: String,
    @JsonProperty("slider_posts") val sliderPosts: List<Post>,
    @JsonProperty("showcase_posts") val showcasePosts: List<Post>,
)

data class ResponseDetail(
    @JsonProperty("status") val status: Boolean,
    @JsonProperty("post") val post: Post,
)

data class ResponseSearch(
    @JsonProperty("current_page") val currentPage: Int,
    @JsonProperty("data") val data: List<Post>,
    @JsonProperty("first_page_url") val firstPageUrl: String,
    @JsonProperty("from") val from: Int,
    @JsonProperty("last_page") val lastPage: Int,
    @JsonProperty("last_page_url") val lastPageUrl: String,
    @JsonProperty("links") val links: List<Link>,
    @JsonProperty("next_page_url") val nextPageUrl: String?,
    @JsonProperty("path") val path: String,
    @JsonProperty("per_page") val perPage: Int,
    @JsonProperty("prev_page_url") val prevPageUrl: String?,
    @JsonProperty("to") val to: Int,
    @JsonProperty("total") val total: Int,
)

data class Post(
    @JsonProperty("id") val id: Int,
    @JsonProperty("logo_disabled") val logoDisabled: Int,
    @JsonProperty("collection_id") val collectionId: Int?,
    @JsonProperty("youtube_video_id") val youtubeVideoId: String?,
    @JsonProperty("has_youtube_thumbnail") val hasYoutubeThumbnail: Int,
    @JsonProperty("type") val type: String,
    @JsonProperty("title") val title: String,
    @JsonProperty("alternative_titles") val alternativeTitles: List<String>?,
    @JsonProperty("plot") val plot: String,
    @JsonProperty("imdb_id") val imdbId: String,
    @JsonProperty("tmdb_id") val tmdbId: String,
    @JsonProperty("minimum_age_limit") val minimumAgeLimit: Int?,
    @JsonProperty("views") val views: Int,
    @JsonProperty("disable_tmdb") val disableTmdb: Int,
    @JsonProperty("poster_path") val posterPath: String?,
    @JsonProperty("backdrop_path") val backdropPath: String?,
    @JsonProperty("youtube_trailer_id") val youtubeTrailerId: String?,
    @JsonProperty("trailer_silence_starts_at") val trailerSilenceStartsAt: String?,
    @JsonProperty("total_episodes_count") val totalEpisodesCount: Int?,
//    @JsonProperty("first_episode") val firstEpisode: String?,
    @JsonProperty("old_total_episodes_count") val oldTotalEpisodesCount: Int?,
    @JsonProperty("slug") val slug: String,
    @JsonProperty("disable_collection_sync") val disableCollectionSync: Int,
    @JsonProperty("checked_at") val checkedAt: String?,
    @JsonProperty("deleted_at") val deletedAt: String?,
    @JsonProperty("created_at") val createdAt: String,
    @JsonProperty("updated_at") val updatedAt: String,
    @JsonProperty("rating") val score: String,
    @JsonProperty("runtime") val runtime: String?,
    @JsonProperty("quality") val quality: List<String>,
    @JsonProperty("release_date") val releaseDate: String?,
    @JsonProperty("soon_category_removed_at") val soonCategoryRemovedAt: String?,
    @JsonProperty("backdrop_with_text_path") val backdropWithTextPath: String?,
    @JsonProperty("logo_path") val logoPath: String?,
    @JsonProperty("backdrop_without_text_path") val backdropWithoutTextPath: String?,
    @JsonProperty("manual_uploads") val manualUploads: String?,
    @JsonProperty("comments_count") val commentsCount: Int,
    @JsonProperty("seasons_count") val seasonsCount: Int,
    @JsonProperty("poster_image") val posterImage: String?,
    @JsonProperty("small_image") val smallImage: String?,
    @JsonProperty("large_image") val largeImage: String?,
    @JsonProperty("large_image_mobile") val largeImageMobile: String?,
    @JsonProperty("large_image_mobile_lazy") val largeImageMobileLazy: String?,
    @JsonProperty("year") val year: String,
    @JsonProperty("final_quality") val finalQuality: String,
    @JsonProperty("poster_slider_image_src") val posterSliderImageSrc: String?,
    @JsonProperty("poster_image_src") val posterImageSrc: String?,
    @JsonProperty("title_without_special_characters") val titleWithoutSpecialCharacters: String,
    @JsonProperty("categories_ids") val categoriesIds: List<Int>,
    @JsonProperty("show_next_episode_flag") val showNextEpisodeFlag: Boolean,
    @JsonProperty("imdb_url") val imdbUrl: String?,
    @JsonProperty("collection") val collection: Collection?,
) {
    private suspend fun getEpisodes(slug: String): List<com.lagradost.cloudstream3.Episode> {
        val response =
            app.get("$URL/api/posts/seasons/$slug").body.string()
        val seasonResponse = parseJson<SeasonResponse>(response)
        val epList = seasonResponse.seasons.mapIndexed { seasonIndex, season ->
            season.episodes.map { episode ->
                com.lagradost.cloudstream3.Episode(
                    data = "$URL/api/post/guest-urls/stream/$slug/$seasonIndex/${episode.number}"
                ).apply {
                    this.season = seasonIndex + 1
                    this.episode = episode.number + 1
                }
            }
        }.flatten()
        return epList
    }

    fun toSearchResponse(): SearchResponse {
        return if (type == "movie") {
            MovieSearchResponse(
                name = title,
                url = "$URL/p/$slug",
                apiName = "AltadefinizioneCommunity",
                posterUrl = posterImage,
                year = year.toInt(),
            ).apply {
                if (finalQuality == "2K") addQuality("HD") else addQuality(finalQuality)
            }
        } else {
            TvSeriesSearchResponse(
                name = title,
                url = "$URL/p/$slug",
                apiName = "AltadefinizioneCommunity",
                posterUrl = posterImage,
                year = year.toInt()
            ).apply {
                if (finalQuality == "2K") addQuality("HD") else addQuality(finalQuality)
            }
        }
    }

    suspend fun toLoadResponse(apiName: String): LoadResponse {
        return if (type == "movie") {
            MovieLoadResponse(
                name = title,
                url = "$URL/play/$slug",
                dataUrl = "$URL/api/post/guest-urls/stream/$slug",
                apiName = apiName,
                posterUrl = posterImage?.substringBeforeLast("?"),
                year = year.toInt(),
                type = TvType.Movie,
                plot = plot,
            ).apply {
                addTrailer("https://www.youtube.com/watch?v=$youtubeTrailerId")
                addRating(score)
                if (!largeImage.isNullOrEmpty()) {
                    this.backgroundPosterUrl = largeImage
                }
                addImdbId(imdbId)
                addTMDbId(tmdbId)
                val genres = categoriesIds.mapNotNull { movieCategories[it.toString()] }
                this.tags = genres
            }
        } else {
            val episodes = getEpisodes(slug)
            TvSeriesLoadResponse(
                name = title,
                url = "$URL/play/$slug",
                apiName = apiName,
                posterUrl = posterImage?.substringBeforeLast("?"),
                year = year.toInt(),
                type = TvType.TvSeries,
                episodes = episodes,
                plot = plot,
            ).apply {
                addTrailer("https://www.youtube.com/watch?v=$youtubeTrailerId")
                addRating(score)
                if (!largeImage.isNullOrEmpty()) {
                    this.backgroundPosterUrl = largeImage
                }
                addImdbId(imdbId)
                addTMDbId(tmdbId)
                val genres = if (categoriesIds.contains(748)) {
                    categoriesIds.mapNotNull {
                        tvProgramCategories[it.toString()]
                    }
                } else {
                    categoriesIds.mapNotNull {
                        tvShowCategories[it.toString()]
                    }
                }
                this.tags = genres
            }
        }
    }
}


data class Collection(
    @JsonProperty("id") val id: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("slug") val slug: String,
    @JsonProperty("poster_path") val posterPath: String?,
    @JsonProperty("backdrop_path") val backdropPath: String?,
)

data class Link(
    @JsonProperty("url") val url: String?,
    @JsonProperty("label") val label: String,
    @JsonProperty("active") val active: Boolean,
)

data class SeasonResponse(
    @JsonProperty("status") val status: Boolean,
    @JsonProperty("seasons") val seasons: List<Season>,
    @JsonProperty("new_episodes_count") val newEpisodesCount: Int,
)

data class Season(
    @JsonProperty("episodes") val episodes: List<Episode>,
    @JsonProperty("season_label") val seasonLabel: String,
)

data class Episode(
    @JsonProperty("number") val number: Int,
    @JsonProperty("label") val label: String,
    @JsonProperty("identifier") val identifier: Int,
    @JsonProperty("is_new") val isNew: Boolean,
)

data class StreamResponse(
    @JsonProperty("streams") val streams: List<Stream>,
)

data class Stream(
    @JsonProperty("url")
    val url: String,
    @JsonProperty("download_size")
    val downloadSize: String,
    @JsonProperty("length")
    val length: Long,  // Using Long because the number is large@JsonProperty("audio_codec")
    val audioCodec: String?,
    @JsonProperty("languages")
    val languages: List<String>,
    @JsonProperty("resolution")
    val resolution: Resolution,
    @JsonProperty("selected")
    val selected: Boolean,
    @JsonProperty("need_upgrade")
    val needUpgrade: Boolean,
)

data class Resolution(
    @JsonProperty("id")
    val id: Int,
    @JsonProperty("name")
    val name: String,
    @JsonProperty("is_default")
    val isDefault: Int,  // You may choose a Boolean type, depending on your needs@JsonProperty("stream_sort")
    val streamSort: Int,
    @JsonProperty("stream_enabled")
    val streamEnabled: Int,
    @JsonProperty("download_sort")
    val downloadSort: Int,
    @JsonProperty("download_enabled")
    val downloadEnabled: Int,
    @JsonProperty("height")
    val height: Int,
)