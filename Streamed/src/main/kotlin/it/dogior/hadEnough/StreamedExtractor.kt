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
        Streamed.canShowToast = true
        if (url.isNotEmpty()) {
            val rawSource = app.get(url).body.string()
//            Log.d(TAG, "Body: $rawSource")
            val source = parseJson<List<Source>>(rawSource)

            if (source.isNotEmpty()) {
                source.forEach { s ->
                    val path = "/${s.source}/js/${s.id}/${s.streamNumber}/playlist.m3u8"

                    val serverDomain = "https://rr.vipstreams.in"
                    val contentUrl = "$serverDomain$path"

//                    Log.d(TAG, contentUrl)

                    val isHdString = if (s.isHD) "HD" else "SD"
                    val sourceName =
                        "${s.streamNumber}. \t ${s.source?.capitalize()} $isHdString \t ${s.language}"

//                    Log.d(
//                        TAG,
//                        sourceName
//                    )

                    callback(
                        ExtractorLink(
                            source = name,
                            name = sourceName,
                            url = contentUrl,
                            referer = referer!!,
                            headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0",
                            ),
                            isM3u8 = true,
                            quality = Qualities.Unknown.value
                        )
                    )
                }

            }

        }

    }
}

/** Gets url with security token provided as id parameter
 * Don't call it in the Extractor or you'll get rate limited really fast */
suspend fun getContentUrl(
    path: String,
    rateLimitCallback: () -> Unit = {}
): String {
    val serverDomain = "https://rr.vipstreams.in"
    var contentUrl = "$serverDomain$path"

    val securityData = getSecurityData(path, rateLimitCallback)
    securityData?.let {
        contentUrl += "?id=${it.id}"
    }

    return contentUrl
}

/** Gets and refreshes security token */
private suspend fun getSecurityData(path: String, rateLimitCallback: () -> Unit = {}): SecurityResponse? {
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

    if (securityResponse.code == 429){
        rateLimitCallback()
        Log.d("STREAMED:Interceptor", "Rate Limited")
        return null
    }

    Log.d(
        TAG,
        "Response: $responseBodyStr"
    )
    return securityData
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
