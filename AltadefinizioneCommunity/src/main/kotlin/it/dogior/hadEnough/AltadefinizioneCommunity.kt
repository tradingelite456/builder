package it.dogior.hadEnough

import android.util.Log
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

class AltadefinizioneCommunity : MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = URL
    override var name = "Altadefinizione Community"
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Cartoon, TvType.Anime, TvType.AnimeMovie)
    override var lang = "it"
    override val hasMainPage = true

    companion object {
        const val URL = "https://altadefinizioneapp.com"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val r = app.get("$mainUrl/api/home/posts/sliders").body.string()
        val response = parseJson<Response>(r)
        val sections = mutableListOf<HomePageList>()
        sections.add(HomePageList(
            "In Primo Piano",
            response.sliderPosts.map { it.toSearchResponse() }
        ))
        sections.add(HomePageList(
            "Top 10",
            response.showcasePosts.map { it.toSearchResponse() }
        ))
        return newHomePageResponse(sections, false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val result = mutableListOf<SearchResponse>()
        var count = 1
        do {
            val r =
                app.get("https://altadefinizioneapp.com/api/search?search=$query&page=$count").body.string()
            val response = parseJson<ResponseSearch>(r)
            result.addAll(response.data.map { it.toSearchResponse() }.toMutableList())
            count++
        } while (response.lastPage >= count)
        return result
    }

    override suspend fun load(url: String): LoadResponse? {
        val slug = url.substringAfterLast("/")
        val r = app.get("https://altadefinizioneapp.com/api/posts/slug/$slug")
        val response = parseJson<ResponseDetail>(r.body.string())
        return response.post.toLoadResponse(this.name)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Altadefinizione", "Url : $data")
        val r = app.get(data).body.string()
        val response = parseJson<StreamResponse>(r)
        response.streams.forEach {
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    "$URL/${it.url}",
                    "",
                    isM3u8 = false,
                    quality = it.resolution.height,
                    headers = mapOf("Cookie" to "ap_session=eyJpdiI6Im5GcnZzM0NjUG5vZ1A2bnBnRHVmTEE9PSIsInZhbHVlIjoiZDh1bDV4TCsyR1BNb3Mwbndvd0hnRkhHT3ZhNU9GYVB3SUwzVDRkU1AxWXNES0dMekRXbE9pOGJTY0ZrbktDTURXcWdmTjVvVVVRMkcwWmdTTUY5WHNvSS95UEVIM01pVkM2a1MySk9mU21qd1piMG1TVTVNM2h4aFQrSERET0IiLCJtYWMiOiJmOWI5YzA0NGQ4MWI0OTIxZmYyMjgxMDZlYzYxYWIzZWE2OWFlMTYxZTExM2IzZDMyNjlkYjJlZmZjM2MxMzYwIiwidGFnIjoiIn0%3D"),
                )
            )
        }

        return true
    }
}