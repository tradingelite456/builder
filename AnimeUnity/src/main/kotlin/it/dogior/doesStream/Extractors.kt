package it.dogior.doesStream


import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import okhttp3.HttpUrl.Companion.toHttpUrl

class AnimeUnityExtractor : ExtractorApi() {
    override val mainUrl = AnimeUnity.mainUrl
    override val name = AnimeUnity.name
    override val requiresReferer = false

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val TAG = "GetUrl"
        Log.d(TAG,"REFERER: $referer  URL: $url")

//        if (url.isNotEmpty()) {
//            callback.invoke(
//                ExtractorLink(
//                    source = "Vixcloud",
//                    name = "Streaming Community",
//                    url = url,
//                    referer = referer!!,
//                    isM3u8 = true,
//                    quality = Qualities.Unknown.value
//                )
//            )
        }

}
