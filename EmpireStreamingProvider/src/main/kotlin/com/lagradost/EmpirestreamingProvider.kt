package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element
import kotlin.collections.ArrayList
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.nicehttp.JsonAsString
import com.lagradost.nicehttp.NiceResponse


class EmpirestreamingProvider : MainAPI() {

    override var mainUrl = "https://empire-stream.ink/"
    override var name = "\uD83D\uDC51 Empire-Streaming \uD83D\uDC51"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override var lang = "fr"
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries)
    private val interceptor = CloudflareKiller()

    data class SearchJson(
        @JsonProperty("status") var status: Boolean? = null,
        @JsonProperty("data") var data: Data? = Data()
    )

    data class Films(
        @JsonProperty("id") var id: Int? = null,
        @JsonProperty("title") var title: String? = null,
        @JsonProperty("versions") var versions: ArrayList<String> = arrayListOf(),
        @JsonProperty("dateCreatedAt") var dateCreatedAt: String? = null,
        @JsonProperty("description") var description: String? = null,
        @JsonProperty("label") var label: String? = null,
        @JsonProperty("image") var image: ArrayList<Image> = arrayListOf(),
        @JsonProperty("season") var season: String? = null,
        @JsonProperty("new_episode") var newEpisode: NewEpisode? = NewEpisode(),
        @JsonProperty("sym_image") var symImage: SymImage? = SymImage(),
        @JsonProperty("BackDrop") var BackDrop: ArrayList<BackDrop> = arrayListOf(),
        @JsonProperty("note") var note: Int? = null,
        @JsonProperty("createdAt") var createdAt: String? = null,
        @JsonProperty("path") var path: String? = null,
        @JsonProperty("trailer") var trailer: String? = null,
        @JsonProperty("urlPath") var urlPath: String? = null
    )

    data class Data(
        @JsonProperty("films") var films: ArrayList<Films> = arrayListOf(),
        @JsonProperty("series") var series: ArrayList<Series> = arrayListOf()
    )

    data class Series(
        @JsonProperty("id") var id: Int? = null,
        @JsonProperty("title") var title: String? = null,
        @JsonProperty("versions") var versions: ArrayList<String> = arrayListOf(),
        @JsonProperty("dateCreatedAt") var dateCreatedAt: String? = null,
        @JsonProperty("description") var description: String? = null,
        @JsonProperty("label") var label: String? = null,
        @JsonProperty("image") var image: ArrayList<Image> = arrayListOf(),
        @JsonProperty("season") var season: String? = null,
        @JsonProperty("new_episode") var newEpisode: NewEpisode? = NewEpisode(),
        @JsonProperty("sym_image") var symImage: SymImage? = SymImage(),
        @JsonProperty("BackDrop") var BackDrop: ArrayList<BackDrop> = arrayListOf(),
        @JsonProperty("note") var note: Int? = null,
        @JsonProperty("createdAt") var createdAt: String? = null,
        @JsonProperty("path") var path: String? = null,
        @JsonProperty("trailer") var trailer: String? = null,
        @JsonProperty("urlPath") var urlPath: String? = null
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val json = JsonAsString("""{"search":"$query"}""")
        val html =
            app.post(
                "$mainUrl/api/views/search",
                data = null,
                json = json, interceptor = interceptor
            )
        val jsonrep = html.parsed<SearchJson>()

        return jsonrep.data!!.series.map {
            newAnimeSearchResponse(
                name = it.title.toString(),
                url = fixUrl(it.urlPath.toString()),
                type = TvType.TvSeries,
            ) {
                this.posterUrl = fixUrl("/images/medias" + it.symImage!!.poster.toString())
                this.posterHeaders =
                    interceptor.getCookieHeaders("$mainUrl/api/views/search").toMap()
                addDubStatus(
                    isDub = it.versions.any { it.contains("vf") },
                    episodes = null
                )
            }
        } + jsonrep.data!!.films.map {
            newAnimeSearchResponse(
                name = it.title.toString(),
                url = fixUrl(it.urlPath.toString()),
                type = TvType.TvSeries,
            ) {
                this.posterUrl =
                    fixUrl("/images/medias" + it.symImage!!.poster.toString())
                this.posterHeaders =
                    interceptor.getCookieHeaders("$mainUrl/api/views/search").toMap()
                addDubStatus(
                    isDub = it.versions.any { it.contains("vf") },
                    episodes = null
                )
            }
        }
    }

    // === Toutes tes data class EpisodeInfo, CreatedAt, YearProduct, Video, Image, etc... inchangées ===

    // Je coupe ici pour éviter la surcharge mais toutes tes classes internes restent identiques
    // Seuls les changements importants sont ci-dessous 👇

    override suspend fun load(url: String): LoadResponse {
        val html = avoidCloudflare(url)
        val document = html.document
        val subEpisodes = ArrayList<Episode>()
        val dubEpisodes = ArrayList<Episode>()
        var dataUrl = url

        // (ton parsing des épisodes reste identique...)

        if (subEpisodes.isEmpty() && dubEpisodes.isEmpty()) {
            return newMovieLoadResponse(
                name = document.select("h1.fs-40.c-w.ff-bb.tt-u.mb-0.ta-md-c.fs-md-30.mb-2").text(),
                url = url,
                type = TvType.Movie,
                dataUrl = dataUrl
            ) {
                this.posterUrl = fixUrl(document.select("picture > img").attr("data-src"))
                this.plot = document.select("p.description").text()
                this.year =
                    document.select("span.c-w.ff-cond.ml-2.ml-md-0.mt-md-1").text().toIntOrNull()
                this.tags = document.select("ul.d-f.f-w.ls-n.mb-2.jc-md-c > li").map { it.text() }
                this.posterHeaders = interceptor.getCookieHeaders(url).toMap() // ✅ plus de runBlocking
            }
        } else {
            return newAnimeLoadResponse(
                document.select("h1.fs-40.c-w.ff-bb.tt-u.mb-0.ta-md-c.fs-md-30.mb-2").text(),
                url,
                TvType.Anime,
            ) {
                this.posterUrl = fixUrl(document.select("picture > img").attr("data-src"))
                this.plot = document.select("p.description").text()
                this.posterHeaders = interceptor.getCookieHeaders(url).toMap() // ✅
                if (subEpisodes.isNotEmpty()) addEpisodes(DubStatus.Subbed, subEpisodes)
                if (dubEpisodes.isNotEmpty()) addEpisodes(DubStatus.Dubbed, dubEpisodes)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        for (part in data.split("||")) {
            for (url in part.split("&")) {
                var playerUrl = url   // ✅ changé en var

                val flag = when {
                    playerUrl.contains("*vf") -> {
                        playerUrl = playerUrl.replace("*vf", "")
                        "\uD83C\uDDE8\uD83C\uDDF5"
                    }
                    playerUrl.contains("*vostfr") -> {
                        playerUrl = playerUrl.replace("*vostfr", "")
                        "\uD83C\uDDEC\uD83C\uDDE7"
                    }
                    else -> ""
                }

                if (playerUrl.isNotBlank()) {
                    loadExtractor(
                        httpsify(playerUrl),
                        mainUrl,
                        subtitleCallback
                    ) { link ->
                        callback.invoke(
                            newExtractorLink(
                                source = link.source,
                                name = link.name + flag,
                                url = link.url
                            ) {
                                this.referer = link.referer
                                this.quality = Qualities.Unknown.value
                                this.isM3u8 = link.isM3u8
                                this.headers = link.headers
                                this.extractorData = link.extractorData
                            }
                        )
                    }
                }
            }
        }
        return true
    }

    private suspend fun Element.toSearchResponse(url: String): SearchResponse {
        val posterUrl = fixUrl(select("div.w-100 > picture > img").attr("data-src"))
        val type = select("div.w-100 > a").attr("data-itype")
        val title = select("div.w-100 > section").attr("data-title")
        val link = fixUrl(select("div.w-100 > a").attr("href"))
        return if (type.contains("film", true)) {
            newMovieSearchResponse(title, link, TvType.Movie) {
                this.posterUrl = posterUrl
                this.posterHeaders = interceptor.getCookieHeaders(url).toMap() // ✅
            }
        } else {
            newAnimeSearchResponse(
                name = title,
                url = link,
                type = TvType.TvSeries,
            ) {
                this.posterUrl = posterUrl
                this.posterHeaders = interceptor.getCookieHeaders(url).toMap() // ✅
                addDubStatus(
                    isDub = select(" div.w-100 > picture > img").attr("alt")
                        .contains("vf", true),
                    episodes = null
                )
            }
        }
    }
}
