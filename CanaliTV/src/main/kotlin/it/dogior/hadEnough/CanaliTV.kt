package it.dogior.hadEnough

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class CanaliTV : MainAPI() {
    override var lang = "it"
    override var mainUrl =
        "https://raw.githubusercontent.com/Free-TV/IPTV/refs/heads/master/playlists/playlist_italy.m3u8"
    override var name = "Canali TV"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override var sequentialMainPage = true
    override val supportedTypes = setOf(TvType.Live)
    private var playlist: Playlist? = null
    private val sharedPref = activity?.getPreferences(Context.MODE_PRIVATE)


    private suspend fun getTVChannels(): List<TVChannel> {
        if (playlist == null) {
            playlist = IptvPlaylistParser().parseM3U(app.get(mainUrl).text)
        }
        return playlist!!.items
    }


    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val data = getTVChannels()
        val title = "Canali TV"
        val show = data.map {
            sharedPref?.edit()?.apply {
                putString(it.url, it.toJson())
                apply()
            }
            it.toSearchResponse(apiName = this@CanaliTV.name)
        }



        return newHomePageResponse(
            HomePageList(
                title,
                show,
                isHorizontalImages = true
            ), false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = getTVChannels()

        return data.filter {
            it.attributes["tvg-id"]?.contains(query) ?: false ||
                    it.title?.lowercase()?.contains(query.lowercase()) ?: false
        }.map { it.toSearchResponse(apiName = this@CanaliTV.name) }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse {
        val tvChannel = sharedPref?.getString(url, null)?.let { parseJson<TVChannel>(it) }
            ?: throw ErrorLoadingException("Error loading channel from cache")

        val streamUrl = tvChannel.url.toString()
        val channelName = tvChannel.title ?: tvChannel.attributes["tvg-id"].toString()
        val posterUrl = tvChannel.attributes["tvg-logo"].toString()

        // TODO: parse and add epg
        //https://www.tivu.tv/ora-in-onda.aspx

        return LiveStreamLoadResponse(
            channelName,
            streamUrl,
            this.name,
            url,
            posterUrl
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        callback.invoke(
            ExtractorLink(
                "Free-TV",
                "Free-TV",
                data,
                "",
                Qualities.Unknown.value,
                isM3u8 = true
            )
        )
        return true
    }
}

data class Playlist(
    val items: List<TVChannel> = emptyList(),
)

data class TVChannel(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null,
) {
    fun toSearchResponse(apiName: String): SearchResponse {
        val streamUrl = url.toString()
        val channelName = title ?: attributes["tvg-id"].toString()
        val posterUrl = attributes["tvg-logo"].toString()
        return LiveSearchResponse(
            channelName,
            streamUrl,
            apiName,
            TvType.Live,
            posterUrl,
        )
    }
}