package it.dogior.hadEnough

import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import it.dogior.hadEnough.YouTubeProvider.Companion.MAIN_URL
import org.schabi.newpipe.extractor.InfoItem.InfoType
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeTrendingExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeTrendingLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo

class YouTubeParser(private val apiName: String) {
    fun getTrendingVideoUrls(): List<SearchResponse> {
        val linkHandler =
            YoutubeTrendingLinkHandlerFactory.getInstance().fromUrl("$MAIN_URL/feed/trending")
        val kiosk = YoutubeService(ServiceList.YouTube.serviceId).kioskList.defaultKioskId
        val extractor = YoutubeTrendingExtractor(ServiceList.YouTube, linkHandler, kiosk)
        extractor.fetchPage()

        val videoUrls = extractor.initialPage.items.mapNotNull {
            if (!it.isShortFormContent) {
                it.url
            } else {
                null
            }
        }
        return videoUrls.mapNotNull { videoToSearchResponse(it) }
    }

    fun search(
        query: String,
        contentFilter: String = "videos",
    ): List<SearchResponse> {
        val handlerFactory = ServiceList.YouTube.searchQHFactory

//        val linkHandler = if (playlistOnly){
//            handlerFactory.fromQuery(query, mutableListOf(playlistFilter), sortFilter)
//        } else {
//            handlerFactory.fromQuery(query)
//        }
        val searchHandler = handlerFactory.fromQuery(
            query,
            listOf(contentFilter),
            null
        )
//        Log.d("YouTubeParser", "Content filters: ${handlerFactory.availableContentFilter.toList()}")
//        val extractor = YoutubeSearchExtractor(ServiceList.YouTube, linkHandler)
//        Log.d("YouTubeParser", "Results size: ${}")
//
//        extractor.forceLocalization(Localization(languageCode))
//        extractor.forceContentCountry(ContentCountry(countryCode))

        val searchInfo = SearchInfo.getInfo(ServiceList.YouTube, SearchQueryHandler(searchHandler))

        val resultSize = searchInfo.relatedItems.size
//        Log.d("YouTubeParser", "Meta info: $resultSize")
        if (resultSize <= 0) {
            return emptyList()
        }
        val pageResults = searchInfo.relatedItems.mapNotNull {
//            Log.d("YouTubeParser", "Related: ${it.name}, type: ${it.infoType}")
            when (it.infoType) {
                InfoType.PLAYLIST, InfoType.CHANNEL -> {
                    //                Log.d("YouTubeParser", "Playlist: ${it.name}")
                    TvSeriesSearchResponse(
                        name = it.name,
                        url = it.url,
                        posterUrl = it.thumbnails[0].url,
                        apiName = apiName
                    )
                }

                InfoType.STREAM -> {
                    //                Log.d("YouTubeParser", "Video: ${it.name}")
                    MovieSearchResponse(
                        name = it.name,
                        url = it.url,
                        posterUrl = it.thumbnails[0].url,
                        apiName = apiName
                    )
                }

                else -> {
                    //                Log.d("YouTubeParser", "Other: ${it.name}")
                    null
                }
            }
        }
        return pageResults
    }

    private fun videoToSearchResponse(videoUrl: String): SearchResponse? {
        try {
            val videoInfo = StreamInfo.getInfo(videoUrl)
            return MovieSearchResponse(
                name = videoInfo.name,
                url = videoUrl,
                posterUrl = videoInfo.thumbnails[0].url,
                apiName = apiName
            )
        } catch (e: ContentNotAvailableException) {
            return null
        }
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

    fun channelToLoadResponse(url: String): LoadResponse {
        val channelInfo = ChannelInfo.getInfo(url)
        val tags = mutableListOf("Subscribers: ${channelInfo.subscriberCount}")
        tags.addAll(channelInfo.tags)
        return TvSeriesLoadResponse(
            name = channelInfo.name,
            url = url,
            posterUrl = channelInfo.avatars[0].url,
            backgroundPosterUrl = channelInfo.banners[0].url,
            plot = channelInfo.description,
            type = TvType.Others,
            tags = tags,
            episodes = getChannelVideos(url),
            apiName = apiName
        )
    }

    private fun getChannelVideos(url: String): List<Episode> {
        val channel = ChannelInfo.getInfo(url)
        val tabsLinkHandlers = channel.tabs
        val tabs = tabsLinkHandlers.map { ChannelTabInfo.getInfo(ServiceList.YouTube, it) }
        val videoTab = tabs.first { it.name == "videos" }

        val videos = videoTab.relatedItems.mapNotNull {
            Episode(
                data = it.url,
                name = it.name,
                posterUrl = it.thumbnails[0].url
            )
        }
        return videos
    }
}