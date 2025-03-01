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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DaddyLiveScheduleProvider : MainAPI() {
    override var mainUrl = "https://daddylive.mp"
    override var name = "DaddyLive Schedule"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "un"
    override val hasMainPage = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val hasDownloadSupport = false
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"

    @Suppress("ConstPropertyName")
    companion object {
        private const val posterUrl =
            "https://raw.githubusercontent.com/doGior/doGiorsHadEnough/refs/heads/master/DaddyLive/daddylive.jpg"
        val formattedDate = let{
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
            val dateFormat = SimpleDateFormat("EEEE dd'$suffix' MMMM yyyy", Locale.UK)

            dateFormat.format(calendar.time) + " - Schedule Time UK GMT"
        }
        fun convertGMTToLocalTime(gmtTime: String): String {
            // Define the input format (GMT time)
            val gmtFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            gmtFormat.timeZone = TimeZone.getTimeZone("GMT") // Set the timezone to GMT

            // Parse the input time string
            val date: Date =
                gmtFormat.parse(gmtTime) ?: throw IllegalArgumentException("Invalid time format")

            // Define the output format (local time)
            val localFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            localFormat.timeZone =
                TimeZone.getDefault() // Set the timezone to the device's local timezone

            // Format the date to local time
            return localFormat.format(date)
        }
    }

    private suspend fun searchResponseBuilder(): List<Pair<String, List<LiveSearchResponse>>> {
        val headers = mapOf("User-Agent" to userAgent)
        val schedule = app.get(
            "$mainUrl/schedule/schedule-generated.json",
            headers,
            timeout = 10
        ).body.string()
        val jsonSchedule = JSONObject(schedule)
//        Log.d("DaddyLive Sports", date)
        val cat = jsonSchedule.getJSONObject(formattedDate)
        val events = mutableMapOf<String, List<Event>>()
        cat.keys().forEach {
            val array = cat.getJSONArray(it)
            val e = tryParseJson<List<Event>>(array.toString())
            if (e != null) {
                events[it] = e
            }
        }
        return events.map {
            it.key to it.value.map { event -> event.toSearchResponse(this.name) }
        }
    }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val searchResponses = searchResponseBuilder()
        val sections = searchResponses.map {
            HomePageList(
                it.first,
                it.second,
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
        val searchResponses = searchResponseBuilder().map { it.second }.flatten()
        val matches = searchResponses.filter {
            query.lowercase().replace(" ", "") in
                    it.name.lowercase().replace(" ", "")
        }
        return matches
    }

    override suspend fun load(url: String): LoadResponse {
        val event = parseJson<Event>(url)
        val time = convertGMTToLocalTime(event.time)

        return LiveStreamLoadResponse(
            event.name,
            url,
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

        DaddyLiveExtractor().getUrl(links.toJson(), null, subtitleCallback, callback)
        return true
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
                convertGMTToLocalTime(time) + " - " + name,
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