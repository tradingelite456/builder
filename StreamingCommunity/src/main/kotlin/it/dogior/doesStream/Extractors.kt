package it.dogior.doesStream


import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.HttpUrl.Companion.toHttpUrl

class StreamingCommunityExtractor : ExtractorApi() {
    override val mainUrl = StreamingCommunity.mainUrl
    override val name = StreamingCommunity.name
    override val requiresReferer = false

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val TAG = "GetUrl"
        Log.d(TAG,"REFERER: $referer  URL: $url")

        if (url.isNotEmpty()) {
            val playlistUrl = getPlaylistLink(url)
            callback.invoke(
                ExtractorLink(
                    source = "Vixcloud",
                    name = "Streaming Community",
                    url = playlistUrl,
                    referer = referer!!,
                    isM3u8 = true,
                    quality = Qualities.Unknown.value
                )
            )
//            callback.invoke(
//                ExtractorLink(
//                    source = "Vixcloud",
//                    name = "Streaming Community",
//                    url = playlistUrl,
//                    referer = referer!!,
//                    isM3u8 = true,
//                    quality = Qualities.Unknown.value
//                )
//            )
        }

    }

    private suspend fun getPlaylistLink(url: String): String {
        val TAG = "getPlaylistLink"

        Log.d(TAG, url)
        val iframeUrl = app.get(url).document
            .select("iframe").attr("src")

        Log.w(TAG, "IFRAME URL: $iframeUrl")
        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Host" to iframeUrl.toHttpUrl().host,
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site",
        )

        val iframe = app.get(iframeUrl, headers = headers).document
//        Log.d(TAG, "TEST: ${iframe.body()}")

        val script =
            iframe.selectFirst("script:containsData(masterPlaylist)")!!.data().replace("\\", "")
                .replace("\n", "").replace("\t", "")
//        val windowVideo = script.substringAfter("= ").substringBefore(";")
        val windowStreams = script.substringAfter("window.streams = ")
            .substringBefore(";        window.masterPlaylist = ")
        val windowMasterPlaylist = script.substringAfter("window.masterPlaylist = ")
            .substringBefore("        window.canPlayFHD")
//        val windowCanPlayFHD = script.substringAfter("window.canPlayFHD = ")
        Log.d(TAG, "SCRIPT: $script")

        val servers = parseJson<List<Server>>(windowStreams)
        Log.d(TAG, "Server List: $servers")

        // Hopefully different streams will have the same format errors
        val mP = windowMasterPlaylist
            .replace("params", "'params'")
            .replace("url", "'url'")
            .replace("'", "\"")
            .replace(" ", "")
            .replace(",}", "}")
        Log.d(TAG, "windowMasterPlaylist: $mP")

        val masterPlaylist = parseJson<MasterPlaylist>(mP)
        Log.d(TAG, "MasterPlaylist Obj: $masterPlaylist")

        var masterPlaylistUrl: String
        val params = "token=${masterPlaylist.params.token}&expires=${masterPlaylist.params.expires}"
        masterPlaylistUrl = if ("?b1" in masterPlaylist.url) {
            "${masterPlaylist.url}&$params"
        } else{
            "${masterPlaylist.url}?$params"
        }
        masterPlaylistUrl = "$masterPlaylistUrl&h=1"
        if(app.get(masterPlaylistUrl).code == 401){
            Log.d(TAG, "Trying adding h=1")
            Log.d(TAG, "Url before: $masterPlaylistUrl")
            // If I understood things correctly h=1 enables 1080p streaming,
            // but if the source doesn't have it it will return an error 401 Unauthorized
            masterPlaylistUrl = masterPlaylistUrl.substringBefore("&h=1")
            Log.d(TAG, "Url after: $masterPlaylistUrl")
        }

        return masterPlaylistUrl
    }

}
