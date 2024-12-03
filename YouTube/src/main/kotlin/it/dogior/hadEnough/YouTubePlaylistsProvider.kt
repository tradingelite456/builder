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
import org.schabi.newpipe.extractor.NewPipe

class YouTubePlaylistsProvider : MainAPI() {
    override var mainUrl = MAIN_URL
    override var name = "YouTube Playlists"
    override val supportedTypes = setOf(TvType.Others)
    override val hasMainPage = false
    override var lang = "un"

    private val ytParser = YouTubeParser(this.name)

    companion object{
        const val MAIN_URL = "https://www.youtube.com"
    }
    init {
        NewPipe.init(DownloaderTestImpl.getInstance())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/results?search_query=$query&sp=EgIQAw%253D%253D"
        val videoUrls = ytParser.getPlaylistUrls(url)
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

        YouTubeExtractor().getUrl(data, "", subtitleCallback, callback)
        return true
    }
}