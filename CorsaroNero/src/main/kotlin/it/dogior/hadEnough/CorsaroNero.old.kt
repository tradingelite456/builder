package it.dogior.hadEnough
//
//import android.util.Log
//import com.lagradost.cloudstream3.HomePageResponse
//import com.lagradost.cloudstream3.LoadResponse
//import com.lagradost.cloudstream3.MainAPI
//import com.lagradost.cloudstream3.MainPageRequest
//import com.lagradost.cloudstream3.MovieSearchResponse
//import com.lagradost.cloudstream3.SubtitleFile
//import com.lagradost.cloudstream3.TvType
//import com.lagradost.cloudstream3.mainPageOf
//import com.lagradost.cloudstream3.utils.ExtractorLink
//import com.lagradost.cloudstream3.utils.INFER_TYPE
//import com.lagradost.cloudstream3.app
//import com.lagradost.cloudstream3.newHomePageResponse
//import com.lagradost.cloudstream3.newMovieLoadResponse
//import com.lagradost.cloudstream3.newMovieSearchResponse
//import com.lagradost.cloudstream3.utils.AppUtils.toJson
//import com.lagradost.cloudstream3.utils.newExtractorLink
//import org.jsoup.nodes.Element
//
//class TrueCorsaroNero : MainAPI() {
//    override var mainUrl = "https://ilcorsaronero.link"
//    override var name = "TrueCorsaroNero"
//    override val supportedTypes = setOf(TvType.Movie, TvType.Torrent)
//    override var lang = "it"
//    override val hasMainPage = true
//
//    override val mainPage = mainPageOf(
//        "$mainUrl/cat/film" to "Film",
//    )
//
//    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
//        val resp = app.get(request.data)
//        val respBody = resp.document
//        val titles = respBody.select("tbody").select("th").select("a")
//        val searchResponses = titles.map { it.toSearchResponse() }
//        return newHomePageResponse(
//            request.name,
//            searchResponses,
//            false
//        )
//    }
//
//    private fun Element.toSearchResponse(): MovieSearchResponse {
//        val title = this.text()
//        val link = this.attr("href")
//        return newMovieSearchResponse(title, mainUrl + link, TvType.Movie)
//    }
//
//    override suspend fun load(url: String): LoadResponse {
//        val resp = app.get(url)
//        val document = resp.document
//        val mainDiv = document.select("div.w-full:nth-child(2)")
//        val title = mainDiv.select("h1").text()
//        val magnet = mainDiv.select("a.w-full:nth-child(1)").attr("href")
//        val loadResponse = newMovieLoadResponse(title, magnet, TvType.Movie, magnet)
//        Log.d("CorsaroNero:load", loadResponse.toJson())
//        return loadResponse
//    }
//
//    override suspend fun loadLinks(
//        data: String,
//        isCasting: Boolean,
//        subtitleCallback: (SubtitleFile) -> Unit,
//        callback: (ExtractorLink) -> Unit,
//    ): Boolean {
//        callback(
//            newExtractorLink(
//                source = this.name,
//                name = this.name,
//                url = data,
//                type = INFER_TYPE,
//            )
//        )
//        return true
//    }
//}