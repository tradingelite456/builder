package it.dogior.hadEnough

import android.util.Log
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app

class YouTubeParser {
    private val videoRegex = Regex("/watch\\?v=[a-zA-Z0-9_-]+[^\"']*")

    /** Returns video urls from the provided url */
    suspend fun getVideoUrls(feedUrl: String): List<String> {
        val response = app.get(feedUrl)
        val initialData = response.document.toString()
            .substringAfter("var ytInitialData =")
            .substringBefore("</script>")
        return videoRegex.findAll(initialData).toList()
            .map { "https://www.youtube.com${it.value}" }
    }

    suspend fun videoToSearchResponse(videoUrl: String): SearchResponse {
        val response = app.get(videoUrl)
        val head = response.document.head()
        val title = head.select("meta[property=\"og:title\"]").attr("content")
        val thumbnail = head.select("meta[property=\"og:image\"]").attr("content")
        return MovieSearchResponse(
            name = title,
            url = videoUrl,
            posterUrl = thumbnail,
            apiName = "YouTube"
        )
    }

    suspend fun videoToLoadResponse(videoUrl: String): LoadResponse {
        val response = app.get(videoUrl)
        val document = response.document
        val title = document.select("meta[property=\"og:title\"]").attr("content")
        val description = document.select("meta[property=\"og:description\"]").attr("content")
        val thumbnail = document.select("meta[property=\"og:image\"]").attr("content")
        return MovieLoadResponse(
            name = title,
            url = videoUrl,
            dataUrl = videoUrl,
            posterUrl = thumbnail,
            plot = description,
            type = TvType.Others,
            apiName = "YouTube"
        )
    }


}