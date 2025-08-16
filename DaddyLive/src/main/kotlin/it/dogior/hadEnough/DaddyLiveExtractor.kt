package it.dogior.hadEnough

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URL
import java.net.URLEncoder

class DaddyLiveExtractor : ExtractorApi() {
    override val mainUrl = ""
    override val name = "DaddyLive"
    override val requiresReferer = false
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
    private val headers = mapOf(
        "Referer" to mainUrl,
        "user-agent" to userAgent
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // List of pairs <channel name, link>
        val links = tryParseJson<List<Pair<String, String>>>(url)
        val extractors = links?.map {
            extractVideo(it.second, it.first)
        } ?: listOf(extractVideo(url))

        extractors.forEach {
            if (it != null) {
                callback(it)
            }
        }
    }

    private suspend fun extractVideo(url: String, sourceName: String = this.name): ExtractorLink? {
        if (!url.contains("daddylive")) return null

        val resp = app.post(url, headers = headers).document
        val iframes = resp.select("iframe")
        val url1 = iframes.attr("src")
        val parsedUrl = URL(url1)
        val refererBase = "${parsedUrl.protocol}://${parsedUrl.host}"


        val finalUrl = (if (url1.contains("vidembed")) extractFromVidembed(url1) else
            extractFinalUrl(url1, refererBase)) ?: return null


        return newExtractorLink(
            sourceName,
            sourceName,
            finalUrl,
            type = ExtractorLinkType.M3U8,
        ) {
            this.referer = "$refererBase/"
            this.quality = Qualities.Unknown.value
            this.headers = mapOf(
                "Origin" to refererBase,
                "Connection" to "Keep-Alive",
                "User-Agent" to userAgent
            )
        }

    }

    private suspend fun extractFromVidembed(urlNextPage: String): String? {
        val vidembedHost = urlNextPage.toHttpUrl().host
        val liveId = urlNextPage.substringAfterLast("/").substringBefore("#")
        val liveUrl = "https://www.$vidembedHost/api/source/$liveId?type=live"
        val requestBody = "{\"r\":\"https://thedaddy.top/\",\"d\":\"www.$vidembedHost\"}"
        val referer = if (urlNextPage.contains("//www.")) urlNextPage
        else {
            "https://www." + urlNextPage.substringAfter("https://").substringBefore("#")
        }
        val headers = mapOf(
            "User-Agent" to userAgent,
            "Referer" to referer,
            "Origin" to referer.substringBefore("/stream"),
            "X-Requested-With" to "XMLHttpRequest"
        )
        val resp = app.post(liveUrl, headers, referer = referer, json = requestBody).body.string()
        val data = parseJson<VidembedResponse>(resp)
        Log.d("DaddyLiveExtractor", data.toJson())
        return null
    }

    private suspend fun extractFinalUrl(urlNextPage: String, serverUrl: String): String? {

        val page = app.get(urlNextPage, headers).body.string()
        // Extracting security values
        val channelKeyRegex = "(?<=var channelKey = \").*(?=\")".toRegex()
        val authTsRegex = "(?<=var __c = atob.\").*(?=\")".toRegex()
        val authRndRegex = "(?<=var __d = atob.\").*(?=\")".toRegex()
        val authSigRegex = "(?<=var __e = atob.\").*(?=\")".toRegex()


        val channelKey = channelKeyRegex.find(page)?.value ?: return null
//        Log.d("DDL", channelKey)
        val authTs = base64Decode(authTsRegex.find(page)?.value ?: return null)
//        Log.d("DDL", authTs)
        val authRnd = base64Decode(authRndRegex.find(page)?.value ?: return null)
//        Log.d("DDL", authRnd)
        val authSig = base64Decode(authSigRegex.find(page)?.value ?: return null)
//        Log.d("DDL", authSig)

        //Requests
//        Log.d("DDL", "TRYING TO AUTH")
        val h = mapOf(
            "User-Agent" to userAgent,
            "Referer" to "$serverUrl/",
            "Origin" to serverUrl
        )
        val authResponse = //withContext(Dispatchers.IO) {
            app.get(
                "https://top2new.newkso.ru/auth.php?channel_id=" + channelKey +
                        "&ts=" + authTs +
                        "&rnd=" + authRnd +
                        "&sig=" + URLEncoder.encode(authSig, "UTF-8"),
                headers = h,
//                interceptor = CloudflareKiller()
            )
        //}
//        Log.d("DDL", authResponse.code.toString())
        Log.d("DDL", "Auth: " + authResponse.body.string())

        val serverKey = app.get("$serverUrl/server_lookup.php?channel_id=$channelKey").body.string()
        Log.d("DDL", "Server Key: $serverKey")
        val data = parseJson<DataResponse>(serverKey)
        //So far it works
        val m3u8 = when (data.serverKey) {
            "top1/cdn" -> "https://top1.newkso.ru/top1/cdn/$channelKey/mono.m3u8"
            else -> "https://${data.serverKey}new.newkso.ru/${data.serverKey}/$channelKey/mono.m3u8"
        }
        Log.d("DDL", "Final Url: $m3u8")
        return m3u8
    }

    data class DataResponse(val serverKey: String)
    data class VidembedResponse(
        val success: Boolean,
        val player: String
    )
}