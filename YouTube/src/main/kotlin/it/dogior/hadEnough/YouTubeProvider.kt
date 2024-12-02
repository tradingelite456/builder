package it.dogior.hadEnough

import android.content.Context
import com.lagradost.api.Log
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class YouTubeProvider(val context: Context) :
    MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = "https://www.youtube.com"
    override var name = "YouTube"
    override val supportedTypes = setOf(TvType.Others)
    override val hasMainPage = true
    override var lang = "un"

    private val ytParser = YouTubeParser()
    override val mainPage = mainPageOf(
        "https://www.youtube.com/feed/trending" to "Trending",
//        "https://www.youtube.com/channel/UCYfdidRxbB8Qhf0Nx7ioOYw" to "News",
//        "https://www.youtube.com/gaming" to "Gaming",
//        "https://www.youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ" to "Music",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homepage = mutableListOf<HomePageList>()
        homepage.add(HomePageList("Trending", emptyList()))
        val videoList = ytParser.getVideoUrls(request.data)
        val videos = videoList.map { ytParser.videoToSearchResponse(it) }
        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = videos,
                isHorizontalImages = true
            ), false
        )
    }

    override suspend fun load(url: String): LoadResponse? {
        val video = ytParser.videoToLoadResponse(url)
        return video
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        loadExtractor(data, subtitleCallback, callback)
        return true
    }
}