package com.example

import android.util.Log
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class RojaDirecta : MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = MAIN_URL
    override var name = Companion.name
    override var supportedTypes = setOf(TvType.Live)
    override var lang = "en"
    override val hasMainPage = true
    override val hasDownloadSupport = false


    companion object {
        const val MAIN_URL = "http://rojadirecta.eu/"
        var name = "RojaDirecta"
        suspend fun getMatches(): List<Match> {
            val document = app.get(MAIN_URL).document
            val eventList = document.select("#agendadiv .menutitle")
//            Log.d("getMainPage", "eventList: $eventList")

            val matchList = eventList.map {
                val league = it.ownText()
                    .replace(":", "")
                    .replace("(", "")
                    .replace(")", "")
                    .replace("-", "")

                Log.d("getMatches", "league: $league")
                Match(
                    startDate = it.select("meta").attr("content"),
                    category = it.select(".en").text(),
                    league = league,
                    name = it.select("b span").text()
                )
            }
            matchList.forEach { if (it.category == "") it.category = it.league }

            return matchList
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val matches = getMatches()
        val homeSections = emptyList<HomePageList>().toMutableList()

        matches.groupBy { it.category }.forEach {
            it.key?.let { key ->
                if(key != ""){
                    Log.d("getMainPage", "category: $it")
                    homeSections.add(
                        HomePageList(
                            key,
                            it.value.map { match -> match.toSearchResponse() },
                            false
                        )
                    )
                }
            }
        }
        return HomePageResponse(homeSections, false)
    }

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf<LiveSearchResponse>()
    }
}

data class Match(
    val startDate: String,
    var category: String?,
    val league: String?,
    /** I.e. "Real Madrid vs Barcelona" */
    val name: String
) {

    fun toSearchResponse(): LiveSearchResponse {
        var formattedTime = ""
        try {
            // Parse the ISO format date string
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date: Date = parser.parse(startDate) ?: throw Exception("Failed to parse date")

            // Format the time part
            val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            formattedTime = timeFormatter.format(date)
        } catch (e: Exception) {
            Log.w("Match to SearchResponse", "DateTime Exception: $e")
        }

        return LiveSearchResponse(
            name = if (formattedTime.isNotEmpty()) {
                "$formattedTime ${this.name}"
            } else {
                this.name
            },
            url = "",
            apiName = RojaDirecta.name,
        )
    }
}

