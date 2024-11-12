package it.dogior.hadEnough

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class AnimeWorldDub : AnimeWorldCore() {
    override var name = "AnimeWorld Dub"
    override var lang = "it"
    override val isDubbed = true

    override val mainPage = mainPageOf(
        "$mainUrl/filter?status=0&language=it&sort=1" to "In Corso",
        "$mainUrl/filter?language=it&sort=1" to "Ultimi aggiunti",
        "$mainUrl/filter?language=it&sort=6" to "Più Visti",
        "$mainUrl/tops/dubbed?sort=1" to "Top 100 Anime",
    )
}
