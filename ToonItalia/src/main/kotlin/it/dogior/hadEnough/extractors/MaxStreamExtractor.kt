package it.dogior.hadEnough.extractors

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class MaxStreamExtractor : ExtractorApi() {
    override var name = "MaxStream"
    override var mainUrl = "https://maxstream.video/"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "User-Agent" to "Mozilla/5.0 (Windows NT 6.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.110 Safari/537.36",
            "Accept-Language" to "en-US;q=0.5,en;q=0.3",
            "Cache-Control" to "max-age=0",
            "Upgrade-Insecure-Requests" to "1"
        )
        val response = app.get(url, headers = headers, timeout = 10_000)
        val responseBody = response.body.string()

        val regex = Regex("""\}\('(.+)',.+,'(.+)'\.split""")
        val matchResult = regex.find(responseBody)
            ?: throw IllegalArgumentException("Regex match not found")
        val (s1, s2) = matchResult.destructured
        val terms = s2.split("|")
        val urlsetIndex = terms.indexOf("urlset")
        val hlsIndex = terms.indexOf("hls")
        val sourcesIndex = terms.indexOf("sources")

        val result = terms.subList(urlsetIndex + 1, hlsIndex).asReversed()
        val firstPart = terms.subList(hlsIndex + 1, sourcesIndex).asReversed()

        var firstUrlPart = ""
        for (part in firstPart) {
            firstUrlPart += if ("0" in part) part else "$part-"
        }

        val baseUrl = "https://$firstUrlPart.host-cdn.net/hls/"
        val reversedElements = result

        val finalUrl = if (reversedElements.size == 1) {
            "$baseUrl,${reversedElements[0]}.urlset/master.m3u8"
        } else {
            val baseUrlBuilder = StringBuilder(baseUrl)
            for ((index, element) in reversedElements.withIndex()) {
                baseUrlBuilder.append(element).append(",")
                if (index == reversedElements.lastIndex) {
                    baseUrlBuilder.append(".urlset/master.m3u8")
                }
            }
            baseUrlBuilder.toString()
        }
        Log.d("MaxStream", "Final URL: $finalUrl")
        callback.invoke(
            ExtractorLink(
                source = name,
                name = name,
                url = finalUrl,
                referer = referer ?: "",
                quality = Qualities.Unknown.value,
                isM3u8 = true
            )
        )
    }
}