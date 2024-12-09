package it.dogior.hadEnough

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.ExtractorLink

class YouTubeProvider(language: String) : MainAPI() {
    override var mainUrl = MAIN_URL
    override var name = "YouTube"
    override val supportedTypes = setOf(TvType.Others)
    override val hasMainPage = true
    override var lang = language

    private val ytParser = YouTubeParser(this.name)

    companion object{
        const val MAIN_URL = "https://www.youtube.com"
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val videos = ytParser.getTrendingVideoUrls()

        // TODO: Add sections based on user given playlists
        // i.e the users goes to the settings puts the url of a playlists
        // and then that playlist will be added to the main pageas a section
        return newHomePageResponse(
            HomePageList(
                name = "Trending",
                list = videos,
                isHorizontalImages = true
            ), false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return ytParser.search(query)
    }

    override suspend fun load(url: String): LoadResponse {
        val video = ytParser.videoToLoadResponse(url)
        return video
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {

        YouTubeExtractor().getUrl(data, "", subtitleCallback, callback)
        return true
    }
}