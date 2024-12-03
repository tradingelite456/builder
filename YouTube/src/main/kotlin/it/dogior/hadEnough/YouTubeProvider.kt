package it.dogior.hadEnough

import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.addPoster
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.util.Locale

class YouTubeProvider(val context: Context) : MainAPI() {
    // all providers must be an intstance of MainAPI
    override var mainUrl = "https://pipedapi.kavin.rocks/"
    override var name = "YouTube"
    override val supportedTypes = setOf(TvType.Others)
    override val hasMainPage = true
    override var lang = "un"
    private val region = "IT"
    private val ytParser = YouTubeParser(this.name)

    companion object {
        const val YOUTUBE_URL = "https://www.youtube.com"
    }

    override val mainPage = mainPageOf(
        "$mainUrl/feed/trending" to "Trending",
//        "https://www.youtube.com/channel/UCYfdidRxbB8Qhf0Nx7ioOYw" to "News",
//        "https://www.youtube.com/gaming" to "Gaming",
//        "https://www.youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ" to "Music",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("$mainUrl/trending?region=$region")
        val videoUrls = parseJson<List<TrendingVideo>>(response.body.string())
        val videos = videoUrls.amap { it.toSearchResponse() }
        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = videos,
                isHorizontalImages = true
            ), false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/search?q=$query&filter=videos")
        val videoUrls = parseJson<SearchResult>(response.body.string())
        val videos = videoUrls.items.amap { it.toSearchResponse() }
        return videos
    }

    override suspend fun load(url: String): LoadResponse {
        val videoID = url.substringAfter("v=")
        val response = app.get("$mainUrl/streams/$videoID")
        val resBody = response.body.string()
        Log.d("YT load", resBody)
        val video = parseJson<VideoDetails>(resBody)
        return video.toLoadResponse()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        YouTubeExtractor().getUrl("$YOUTUBE_URL$data", null, subtitleCallback, callback)
        return true
    }
}

data class TrendingVideo(
    @JsonProperty("duration")
    val duration: Int, // The duration of the trending video in seconds

    @JsonProperty("thumbnail")
    val thumbnail: String, // The thumbnail of the trending video

    @JsonProperty("title")
    val title: String, // The title of the trending video

    @JsonProperty("uploadedDate")
    val uploadedDate: String, // The date the trending video was uploaded

    @JsonProperty("uploaderAvatar")
    val uploaderAvatar: String, // The avatar of the channel of the trending video

    @JsonProperty("uploaderUrl")
    val uploaderUrl: String, // The URL of the channel of the trending video

    @JsonProperty("uploaderVerified")
    val uploaderVerified: Boolean, // Whether or not the channel of the trending video is verified

    @JsonProperty("url")
    val url: String, // The URL of the trending video

    @JsonProperty("views")
    val views: Int, // The number of views the trending video has
) {
    fun toSearchResponse(): SearchResponse {
        return MovieSearchResponse(
            name = title,
            url = url,
            posterUrl = thumbnail,
            apiName = "YouTube"
        )
    }
    fun toLoadResponse(): LoadResponse {
        return MovieLoadResponse(
            name = title,
            url = url,
            dataUrl = url,
            posterUrl = thumbnail,
            type = TvType.Others,
//            tags = listOf(videoInfo.uploaderName, views, likes),
            apiName = "YouTube"
        ).apply {
            addDuration(duration.toString())
        }
    }
}

data class SearchResult(
    @JsonProperty("items")
    val items: List<SearchItem>
)

data class SearchItem(
    @JsonProperty("url")
    val url: String, // The URL of the video

    @JsonProperty("type")
    val type: String, // The type of content (e.g., "stream")

    @JsonProperty("title")
    val title: String, // The title of the video

    @JsonProperty("thumbnail")
    val thumbnail: String, // The URL of the video thumbnail

    @JsonProperty("uploaderName")
    val uploaderName: String, // The name of the uploader

    @JsonProperty("uploaderUrl")
    val uploaderUrl: String, // The URL of the uploader's channel

    @JsonProperty("uploaderAvatar")
    val uploaderAvatar: String?, // The URL of the uploader's avatar

    @JsonProperty("uploadedDate")
    val uploadedDate: String, // A human-readable date when the video was uploaded

    @JsonProperty("shortDescription")
    val shortDescription: String?, // A short description of the video

    @JsonProperty("duration")
    val duration: Int, // The duration of the video in seconds

    @JsonProperty("views")
    val views: Int, // The number of views the video has

    @JsonProperty("uploaded")
    val uploaded: Long, // The upload timestamp in milliseconds since epoch

    @JsonProperty("uploaderVerified")
    val uploaderVerified: Boolean?, // Whether the uploader is verified

    @JsonProperty("isShort")
    val isShort: Boolean // Whether the video is a short
){
    fun toSearchResponse(): SearchResponse {
        return MovieSearchResponse(
            name = title,
            url = this.toJson(),
            posterUrl = thumbnail,
            apiName = "YouTube"
        )
    }
}

data class VideoDetails(
    @JsonProperty("audioStreams")
    val audioStreams: List<AudioStream>, // The audio streams of the video

    @JsonProperty("dash")
    val dash: String?, // The dash manifest URL, nullable

    @JsonProperty("description")
    val description: String, // The description of the video

//    @JsonProperty("dislikes")
//    val dislikes: Int, // The number of dislikes the video has

    @JsonProperty("duration")
    val duration: Int, // The duration of the video in seconds

    @JsonProperty("hls")
    val hls: String?, // The hls manifest URL, nullable

//    @JsonProperty("lbryId")
//    val lbryId: String, // The lbry id of the video, if available

    @JsonProperty("likes")
    val likes: Int, // The number of likes the video has

    @JsonProperty("livestream")
    val livestream: Boolean, // Whether or not the video is a livestream

    @JsonProperty("proxyUrl")
    val proxyUrl: String, // The proxy URL for rewrites

//    @JsonProperty("relatedStreams")
//    val relatedStreams: List<RelatedStream>, // A list of related streams

    @JsonProperty("subtitles")
    val subtitles: List<Subtitle>, // A list of subtitles

    @JsonProperty("thumbnailUrl")
    val thumbnailUrl: String, // The thumbnail of the video

    @JsonProperty("title")
    val title: String, // The title of the video

    @JsonProperty("uploadDate")
    val uploadDate: String, // The date the video was uploaded

    @JsonProperty("uploader")
    val uploader: String, // The name of the channel of the video

    @JsonProperty("uploaderUrl")
    val uploaderUrl: String, // The URL of the channel of the video

    @JsonProperty("uploaderVerified")
    val uploaderVerified: Boolean, // Whether or not the channel is verified

    @JsonProperty("videoStreams")
    val videoStreams: List<VideoStream>, // The video streams of the video

    @JsonProperty("views")
    val views: Int // The number of views the video has
){
    fun toLoadResponse(): LoadResponse {
        return MovieLoadResponse(
            name = title,
            url = videoStreams[0].url,
            dataUrl = videoStreams[0].url,
            posterUrl = thumbnailUrl,
            plot = description,
            type = TvType.Others,
            tags = listOf(uploader, views.toString(), likes.toString()),
            apiName = "YouTube"
        ).apply {
            addDuration(duration.toString())
        }
    }
}

data class AudioStream(
    @JsonProperty("bitrate")
    val bitrate: Int, // The bitrate of the audio stream in bytes

    @JsonProperty("codec")
    val codec: String, // The codec of the audio stream

    @JsonProperty("format")
    val format: String, // The format of the audio stream

    @JsonProperty("indexEnd")
    val indexEnd: Int, // Useful for creating dash streams

    @JsonProperty("indexStart")
    val indexStart: Int, // Useful for creating dash streams

    @JsonProperty("initStart")
    val initStart: Int, // Useful for creating dash streams

    @JsonProperty("initEnd")
    val initEnd: Int, // Useful for creating dash streams

    @JsonProperty("mimeType")
    val mimeType: String, // The mime type of the audio stream

    @JsonProperty("quality")
    val quality: String, // The quality of the audio stream

    @JsonProperty("url")
    val url: String, // The stream's URL

    @JsonProperty("videoOnly")
    val videoOnly: Boolean // Whether or not the stream is video only
)

data class RelatedStream(
    @JsonProperty("duration")
    val duration: Int, // The duration of the related video in seconds

    @JsonProperty("thumbnail")
    val thumbnail: String, // The thumbnail of the related video

    @JsonProperty("title")
    val title: String, // The title of the related video

    @JsonProperty("uploadedDate")
    val uploadedDate: String, // The date the related video was uploaded

    @JsonProperty("uploaderAvatar")
    val uploaderAvatar: String, // The avatar of the channel of the related video

    @JsonProperty("uploaderUrl")
    val uploaderUrl: String, // The URL of the channel of the related video

    @JsonProperty("uploaderVerified")
    val uploaderVerified: Boolean, // Whether or not the channel is verified

    @JsonProperty("url")
    val url: String, // The URL of the related video

    @JsonProperty("views")
    val views: Int // The number of views the related video has
)

data class Subtitle(
    @JsonProperty("autoGenerated")
    val autoGenerated: Boolean, // Whether or not the subtitle was auto-generated

    @JsonProperty("code")
    val code: String, // The language code of the subtitle

    @JsonProperty("mimeType")
    val mimeType: String, // The mime type of the subtitle

    @JsonProperty("name")
    val name: String, // The name of the subtitle

    @JsonProperty("url")
    val url: String // The URL of the subtitle
)

data class VideoStream(
    @JsonProperty("bitrate")
    val bitrate: Int, // The bitrate of the video stream in bytes

    @JsonProperty("codec")
    val codec: String, // The codec of the video stream

    @JsonProperty("format")
    val format: String, // The format of the video stream

    @JsonProperty("fps")
    val fps: Int, // The frames per second of the video stream

    @JsonProperty("height")
    val height: Int, // The height of the video stream

    @JsonProperty("indexEnd")
    val indexEnd: Int, // Useful for creating dash streams

    @JsonProperty("indexStart")
    val indexStart: Int, // Useful for creating dash streams

    @JsonProperty("initStart")
    val initStart: Int, // Useful for creating dash streams

    @JsonProperty("initEnd")
    val initEnd: Int, // Useful for creating dash streams

    @JsonProperty("mimeType")
    val mimeType: String, // The mime type of the video stream

    @JsonProperty("quality")
    val quality: String, // The quality of the video stream

    @JsonProperty("url")
    val url: String, // The stream's URL

    @JsonProperty("videoOnly")
    val videoOnly: Boolean, // Whether or not the stream is video only

    @JsonProperty("width")
    val width: Int // The width of the video stream
)