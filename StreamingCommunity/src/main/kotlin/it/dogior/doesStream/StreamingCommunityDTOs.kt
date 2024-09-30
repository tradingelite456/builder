package it.dogior.doesStream

import com.fasterxml.jackson.annotation.JsonProperty

data class Section(
    @JsonProperty("name") val name: String,
    @JsonProperty("label") val label: String,
    @JsonProperty("titles") val titles: List<Title>,
)

data class Title(
    @JsonProperty("id") val id: Int,
    @JsonProperty("slug") val slug: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("type") val type: String,
    @JsonProperty("score") val score: String?,
    @JsonProperty("sub_ita") val subIta: Int,
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

data class Genre(
    @JsonProperty("id") val id: Int,
    @JsonProperty("name") val name: String,
    @JsonProperty("type") val type: String,
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

data class SearchData(
    @JsonProperty("data") val titles: List<Title>,
)

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