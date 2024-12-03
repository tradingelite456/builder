package it.dogior.hadEnough

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.StreamInfo

class YouTubeParser(private val apiName: String) {
    private val videoRegex = Regex("/watch\\?v=[a-zA-Z0-9_-]+[^\",]")

    /** Returns video urls from the provided url */
    suspend fun getVideoUrls(feedUrl: String): List<String> {
        val response = app.get("$feedUrl?cbrd=1&ucbcb=1", headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"))
        val initialData = response.document.toString()
            .substringAfter("var ytInitialData =")
            .substringBefore("</script>")
        val contents = ((JSONObject(initialData)
            .getJSONObject("contents")
            .getJSONObject("twoColumnBrowseResultsRenderer")
            .get("tabs") as JSONArray)[0] as JSONObject)
            .getJSONObject("tabRenderer")
            .getJSONObject("content")
            .getJSONObject("sectionListRenderer")
            .get("contents") as JSONArray
        val contentList = parseJson<List<Any>>(contents.toString())
        val videoUrls = contentList.amap {
            videoRegex.findAll(it.toString()).toList()
                .amap { id -> "https://www.youtube.com${id.value}" }
        }
        val v = videoUrls.flatten()
        return v
    }

    fun videoToSearchResponse(videoUrl: String): SearchResponse {
        val videoInfo = StreamInfo.getInfo(videoUrl)

        return MovieSearchResponse(
            name = videoInfo.name,
            url = videoUrl,
            posterUrl = videoInfo.thumbnails[0].url,
            apiName = apiName
        )
    }

    fun videoToLoadResponse(videoUrl: String): LoadResponse {
        val videoInfo = StreamInfo.getInfo(videoUrl)
        val views = "Views: ${videoInfo.viewCount}"
        val likes = "Likes: ${videoInfo.likeCount}"
        val length = videoInfo.duration
        return MovieLoadResponse(
            name = videoInfo.name,
            url = videoUrl,
            dataUrl = videoUrl,
            posterUrl = videoInfo.thumbnails[0].url,
            plot = videoInfo.description.content,
            type = TvType.Others,
            tags = listOf(videoInfo.uploaderName, views, likes),
            apiName = apiName
        ).apply {
            addDuration(length.toInt().toString())
        }
    }
}