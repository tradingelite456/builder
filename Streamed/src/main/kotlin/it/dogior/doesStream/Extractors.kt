package it.dogior.doesStream

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty

import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import it.dogior.doesStream.Streamed.Source
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.nio.charset.Charset

class StreamedExtractor : ExtractorApi() {
    override val mainUrl = Streamed.mainUrl
    override val name = Streamed.name
    override val requiresReferer = false
    private val TAG = "StreamedExtractor"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
//        Log.d(TAG, "REFERER: $referer  URL: $url")

        if (url.isNotEmpty()) {
            val rawSource = app.get(url).body.string()
//                Log.d(TAG, "Body: $rawSource")
            val source = parseJson<List<Source>>(rawSource)
            val serverDomain = "https://rr.vipstreams.in"

            if (source.isNotEmpty()) {
                source.forEach { s ->
                    val path = "/${s.source}/js/${s.id}/${s.streamNumber}/playlist.m3u8"
                    val securityData = getSecurityData(path)
                    var contentUrl = "$serverDomain$path"
                    securityData?.let { contentUrl += "?id=${it.id}" }

                    val isHdString = if (s.isHD) "HD" else "SD"
                    val sourceName =
                        "${s.streamNumber}. \t ${s.source?.capitalize()} $isHdString \t ${s.language}"

                    Log.d(
                        TAG,
                        sourceName
                    )

                    callback.invoke(
                        ExtractorLink(
                            source = sourceName,
                            name = sourceName,
                            url = contentUrl,
                            referer = referer!!,
                            isM3u8 = true,
                            quality = Qualities.Unknown.value
                        )
                    )
//                    delay(1000*60)
                }
            }

        }

    }



    private suspend fun getSecurityData(path: String): SecurityResponse? {
        val headers = mapOf(
            "Accept" to "*/*",
            "Referer" to "https://embedme.top/",
            "Content-Type" to "application/json",
            "Host" to "secure.bigcoolersonline.top"
        )

        val requestBody =
            "{\"path\":\"$path\"}"
                .toRequestBody("application/json".toMediaType())

        val securityResponse = app.post(
            "https://secure.bigcoolersonline.top/init-session",
            headers = headers,
            requestBody = requestBody
        )
        val responseBodyStr = securityResponse.body.string()
        val securityData = tryParseJson<SecurityResponse>(responseBodyStr)

//        Log.d(
//            "StreamedExtractor",
//            "Headers: ${securityResponse.headers}"
//        )
        Log.d(
            TAG,
            "Response: $responseBodyStr"
        )
        return securityData
    }

}


/** For debugging purpose */
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
