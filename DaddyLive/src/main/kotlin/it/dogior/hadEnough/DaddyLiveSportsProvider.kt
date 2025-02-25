package it.dogior.hadEnough

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.VPNStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DaddyLiveSportsProvider : MainAPI() {
    override var mainUrl = "https://daddylive.mp"
    override var name = "DaddyLive Sports"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "un"
    override val hasMainPage = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"

    @Suppress("ConstPropertyName")
    companion object {
        private val streams = mutableListOf<Event>()
        private const val posterUrl = "https://raw.githubusercontent.com/doGior/doGiorsHadEnough/refs/heads/master/DaddyLive/daddylive.jpg"
        fun getFormattedDate(): String {
            val calendar = Calendar.getInstance()
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            // Get the correct ordinal suffix
            val suffix = when (day % 10) {
                1 -> if (day == 11) "th" else "st"
                2 -> if (day == 12) "th" else "nd"
                3 -> if (day == 13) "th" else "rd"
                else -> "th"
            }

            // Define the date format
            val dateFormat = SimpleDateFormat("EEEE dd'$suffix' MMM yyyy", Locale.ENGLISH)

            return dateFormat.format(calendar.time) + " - Schedule Time UK GMT"
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val headers = mapOf("User-Agent" to userAgent)

        val schedule = app.get(
            "$mainUrl/schedule/schedule-generated.json",
            headers,
            timeout = 10
        ).body.string()
        val jsonSchedule = JSONObject(schedule)
        val cat = jsonSchedule.getJSONObject(getFormattedDate())
        val events = mutableMapOf<String, List<Event>>()
        cat.keys().forEach {
            if (it != "TV Shows") {
                val array = cat.getJSONArray(it)
                val e = tryParseJson<List<Event>>(array.toString())
                if (e != null) {
                    events[it] = e
                }
            }
        }
        streams.addAll(events.values.toList().flatten())
        val sections = events.map {
            HomePageList(
                it.key,
                it.value.map { event -> event.toSearchResponse(this.name) },
                false
            )
        }

        return newHomePageResponse(
            sections,
            false
        )
    }

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        val matches = streams.filter {
            query.lowercase().replace(" ", "") in
                    it.name.lowercase().replace(" ", "")
        }
        if (matches.isNotEmpty()) {
            val results = matches.map {
                it.toSearchResponse(this.name)
            }
            return results
        } else {
            return emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val event = parseJson<Event>(url)
        val time = convertGMTToLocalTime(event.time)

        return LiveStreamLoadResponse(
            event.name,
            "",
            this.name,
            event.channels.toJson(),
            tags = listOf(time),
            posterUrl = posterUrl
        )
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val channels = parseJson<List<Channel>>(data)
        val links = channels.map {
            val link = "$mainUrl/stream/stream-${it.channelId}.php"
            it.channelName to link
        }

        links.map {
            val headers = mapOf(
                "Referer" to mainUrl,
                "user-agent" to userAgent
            )
            val resp = app.post(it.second, headers = headers).body.string()
            val url1 = Regex("iframe src=\"([^\"]*)").find(resp)?.groupValues?.get(1)
                ?: throw Exception("URL not found")
            val parsedUrl = URL(url1)
            val refererBase = "${parsedUrl.protocol}://${parsedUrl.host}"
            val referer = URLEncoder.encode(refererBase, "UTF-8")
            val userAgent = URLEncoder.encode(userAgent, "UTF-8")

            val resp2 = app.post(url1, headers).body.string()


            val streamId = Regex("fetch\\('([^']*)").find(resp2)?.groupValues?.get(1)
                ?: throw Exception("Stream ID not found")
            val url2 = Regex("var channelKey = \"([^\"]*)").find(resp2)?.groupValues?.get(1)
                ?: throw Exception("Channel Key not found")
            val m3u8 = Regex("(/mono\\.m3u8)").find(resp2)?.groupValues?.get(1)
                ?: throw Exception("M3U8 not found")

            val url3 = "$refererBase$streamId$url2"
            val resp3 = app.post(url3, headers).body.string()
            val key =
                Regex(":\"([^\"]*)").find(resp3)?.groupValues?.get(1)
                    ?: throw Exception("Key not found")

            val finalLink =
                "https://$key.iosplayer.ru/$key/$url2$m3u8"

            callback(
                ExtractorLink(
                    it.first,
                    it.first,
                    finalLink,
                    referer = "",
                    isM3u8 = true,
                    headers = mapOf(
                        "Referer" to referer,
                        "Origin" to referer,
                        "Keep-Alive" to "true",
                        "User-Agent" to userAgent
                    ),
                    quality = Qualities.Unknown.value
                )
            )
        }
        return true
    }

    fun convertGMTToLocalTime(gmtTime: String): String {
        // Define the input format (GMT time)
        val gmtFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        gmtFormat.timeZone = TimeZone.getTimeZone("GMT") // Set the timezone to GMT

        // Parse the input time string
        val date: Date = gmtFormat.parse(gmtTime) ?: throw IllegalArgumentException("Invalid time format")

        // Define the output format (local time)
        val localFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        localFormat.timeZone = TimeZone.getDefault() // Set the timezone to the device's local timezone

        // Format the date to local time
        return localFormat.format(date)
    }

    data class Event(
        val time: String,
        @JsonProperty("event")
        val name: String,
        val channels: List<Channel>,
        @JsonProperty("channels2")
        val channels2: List<Channel>
    ) {
        fun toSearchResponse(apiName: String): LiveSearchResponse {
            return LiveSearchResponse(
                name,
                this.toJson(),
                apiName,
                posterUrl = posterUrl
            )
        }
    }

    data class Channel(
        @JsonProperty("channel_name")
        val channelName: String,
        @JsonProperty("channel_id")
        val channelId: String
    )
}