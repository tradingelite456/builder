package it.dogior.hadEnough

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty

import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import it.dogior.hadEnough.Streamed.Source
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.nio.charset.Charset

const val TAG = "StreamedExtractor"

class StreamedExtractor : ExtractorApi() {
    override val mainUrl = Streamed.MAIN_URL
    override val name = Streamed.NAME
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (url.isNotEmpty()) {
            val rawSource = app.get(url).body.string()
//            Log.d(TAG, "Body: $rawSource")
            val source = parseJson<List<Source>>(rawSource)

            if (source.isNotEmpty()) {
                // TODO: remove [0].let and put amap
                source[0].let { s ->
                    val path = "/${s.source}/js/${s.id}/${s.streamNumber}/playlist.m3u8"

//                    val contentUrl = getContentUrl(serverDomain, path)
//                    Log.d(TAG, contentUrl)

                    val isHdString = if (s.isHD) "HD" else "SD"
                    val sourceName =
                        "${s.streamNumber}. \t ${s.source?.capitalize()} $isHdString \t ${s.language}"

//                    Log.d(
//                        TAG,
//                        sourceName
//                    )

                    callback(ExtractorLink(
                        source = name,
                        name = sourceName,
                        url = getContentUrl(path),
                        referer = referer!!,
                        headers = mapOf(
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0",
                            "Cache-Control" to "no-cache"
                        ),
                        isM3u8 = true,
                        quality = Qualities.Unknown.value
                    ))
                }

            }

        }

    }

    private suspend fun getContentUrl(
        path: String
    ): String {
        val serverDomain = "https://rr.vipstreams.in"
        var contentUrl = "$serverDomain$path"

        val securityData = getSecurityData(path)
        securityData?.let {
            contentUrl += "?id=${it.id}"
        }

//        val playlist = app.get(
//            contentUrl,
//            headers = mapOf(
//                "Referer" to referer!!,
//                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0"
//            )
//        )
//        Log.d(TAG, "Playlist: ${playlist.body.string()}")
        return contentUrl
    }


    private suspend fun getSecurityData(path: String): SecurityResponse? {
        val targetUrl = "https://secure.bigcoolersonline.top/init-session"
        val headers = mapOf(
            "Referer" to "https://embedme.top/",
            "Content-Type" to "application/json",
            "Host" to targetUrl.toHttpUrl().host,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0"
        )

        val requestBody =
            "{\"path\":\"$path\"}"
                .toRequestBody("application/json".toMediaType())

        val securityResponse = app.post(
            targetUrl,
            headers = headers,
            requestBody = requestBody
        )
        val responseBodyStr = securityResponse.body.string()
        val securityData = tryParseJson<SecurityResponse>(responseBodyStr)

//        Log.d(
//            TAG,
//            "Headers: ${securityResponse.headers}"
//        )
        Log.d(
            TAG,
            "Response: $responseBodyStr"
        )
        return securityData
    }

}


/** For debugging purposes */
fun RequestBody.convertToString(): String {
    val buffer = Buffer()
    writeTo(buffer)
    return buffer.readString(Charset.defaultCharset())
}

/** Example {"expiry":1731135300863,"id":"H7X8vD9QDXkc4kQlJ9qgu"} */
data class SecurityResponse(
    @JsonProperty("expiry") val expiry: Long,
    @JsonProperty("id") val id: String
)
