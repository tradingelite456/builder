package it.dogior.hadEnough

import android.content.Context
import com.lagradost.cloudstream3.DownloaderTestImpl
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.schabi.newpipe.extractor.NewPipe

class YouTubeProvider(val context: Context) : MainAPI() {
    // all providers must be an intstance of MainAPI
    override var mainUrl = MAIN_URL
    override var name = "YouTube"
    override val supportedTypes = setOf(TvType.Others)
    override val hasMainPage = true
    override var lang = "un"

    private val ytParser = YouTubeParser(this.name)

    companion object{
        const val MAIN_URL = "https://www.youtube.com"
    }
    init {
        NewPipe.init(DownloaderTestImpl.getInstance())
    }

    override val mainPage = mainPageOf(
        "$mainUrl/feed/trending" to "Trending",
//        "https://www.youtube.com/channel/UCYfdidRxbB8Qhf0Nx7ioOYw" to "News",
//        "https://www.youtube.com/gaming" to "Gaming",
//        "https://www.youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ" to "Music",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val videoUrls = ytParser.getVideoUrls(request.data)
        val videos = videoUrls.amap { ytParser.videoToSearchResponse(it) }
        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = videos,
                isHorizontalImages = true
            ), false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/results?search_query=$query"
        val videoUrls = ytParser.getVideoUrls(url)
        return videoUrls.amap { ytParser.videoToSearchResponse(it) }
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

        YoutubeExtractor().getUrl(data, "", subtitleCallback, callback)
        return true
    }
}