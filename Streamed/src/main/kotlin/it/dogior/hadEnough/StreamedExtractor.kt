package it.dogior.hadEnough

import com.lagradost.cloudstream3.APIHolder.capitalize
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import it.dogior.hadEnough.Streamed.Source
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

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

