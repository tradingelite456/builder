package it.dogior.hadEnough

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.schemaStripRegex
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

open class YouTubeExtractor : ExtractorApi() {
    override val mainUrl = "https://www.youtube.com"
    override val requiresReferer = false
    override val name = "YouTube"


    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val ytVideos: MutableMap<String, List<VideoStream>> = mutableMapOf()
        val ytVideosSubtitles: MutableMap<String, List<SubtitlesStream>> = mutableMapOf()
        Log.d("YoutubeExtractor", "StreamEmpty? ${ytVideos[url].isNullOrEmpty()}")
        if (ytVideos[url].isNullOrEmpty()) {
            val link =
                YoutubeStreamLinkHandlerFactory.getInstance().fromUrl(
                    url.replace(
                        schemaStripRegex, ""
                    )
                )

            val s = object : YoutubeStreamExtractor(
                ServiceList.YouTube,
                link
            ) {

            }
            s.fetchPage()
            Log.d("YoutubeExtractor", "StreamNumber: ${s.videoOnlyStreams.size}")
            ytVideos[url] = s.videoStreams
            ytVideosSubtitles[url] = try {
                s.subtitlesDefault.filterNotNull()
            } catch (e: Exception) {
                logError(e)
                emptyList()
            }
        }
        Log.d("YoutubeExtractor", "Number of streams: ${ytVideos[url]?.size}")
        ytVideos[url]?.mapNotNull {
            if (it.isVideoOnly() || it.height <= 0) return@mapNotNull null

            ExtractorLink(
                this.name,
                this.name,
                it.content ?: return@mapNotNull null,
                referer ?: "",
                it.height
            )
        }?.forEach(callback)
        ytVideosSubtitles[url]?.mapNotNull {
            SubtitleFile(it.languageTag ?: return@mapNotNull null, it.content ?: return@mapNotNull null)
        }?.forEach(subtitleCallback)
    }
}