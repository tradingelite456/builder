package com.phisher98

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.CLEARKEY_UUID
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newDrmExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class DaddyLive : MainAPI() {
    override var lang = "fr"
    override var mainUrl: String = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_france.m3u8"
    override var name = "DaddyLive"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Live,
    )

    // Liste des groupes français à prioriser
    private val frenchGroups = setOf(
        "france", "french", "français", "fr", "francia", "frankreich",
        "tf1", "france 2", "france 3", "m6", "canal+", "arte", "bfm"
    )

    private suspend fun fetchAllPlaylists(): Playlist {
        val allItems = mutableListOf<PlaylistItem>()
        
        // URLs alternatives pour les chaînes françaises
        val urls = listOf(
            "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_france.m3u8",
            "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/fr.m3u",
            "https://raw.githubusercontent.com/davidmuma/EPG_dobleM/master/TDT_ES/data/IPTV_French_XMLTV.m3u"
        )
        
        for (url in urls) {
            try {
                val content = app.get(url).text
                val playlist = IptvPlaylistParser().parseM3U(content)
                allItems.addAll(playlist.items.filter { item ->
                    val groupTitle = item.attributes["group-title"]?.lowercase() ?: ""
                    val title = item.title?.lowercase() ?: ""
                    
                    // Filtrer pour les chaînes françaises
                    frenchGroups.any { group ->
                        groupTitle.contains(group) || title.contains(group)
                    } || groupTitle.contains("fr") || title.contains("france")
                })
            } catch (e: Exception) {
                // Continuer avec l'URL suivante si celle-ci échoue
                continue
            }
        }
        
        // Si aucune chaîne française n'est trouvée, utiliser toutes les chaînes de la première URL
        if (allItems.isEmpty()) {
            try {
                val content = app.get(urls.first()).text
                val playlist = IptvPlaylistParser().parseM3U(content)
                allItems.addAll(playlist.items)
            } catch (e: Exception) {
                // En dernier recours, créer quelques chaînes de test
                allItems.addAll(createTestChannels())
            }
        }
        
        return Playlist(allItems)
    }

    private fun createTestChannels(): List<PlaylistItem> {
        return listOf(
            PlaylistItem(
                title = "TF1",
                attributes = mapOf(
                    "group-title" to "France",
                    "tvg-logo" to "https://upload.wikimedia.org/wikipedia/commons/d/da/TF1_logo_2013.png"
                ),
                url = "https://tf1-hls-live-ssl.tf1.fr/tf1/1/hls/live_2328.m3u8",
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            ),
            PlaylistItem(
                title = "France 2",
                attributes = mapOf(
                    "group-title" to "France",
                    "tvg-logo" to "https://upload.wikimedia.org/wikipedia/commons/7/79/France_2_logo_2018.svg"
                ),
                url = "https://raw.githubusercontent.com/azgaresncf/strm2hls/main/streams/france2.m3u8",
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            ),
            PlaylistItem(
                title = "M6",
                attributes = mapOf(
                    "group-title" to "France",
                    "tvg-logo" to "https://upload.wikimedia.org/wikipedia/commons/1/12/M6_logo_2009.svg"
                ),
                url = "https://raw.githubusercontent.com/azgaresncf/strm2hls/main/streams/m6.m3u8",
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            )
        )
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = fetchAllPlaylists()
        return newHomePageResponse(
            data.items.groupBy { 
                val group = it.attributes["group-title"] ?: "Autres"
                // Prioritiser les groupes français
                when {
                    frenchGroups.any { fr -> group.lowercase().contains(fr) } -> "📺 Chaînes Françaises"
                    group.lowercase().contains("sport") -> "⚽ Sport"
                    group.lowercase().contains("news") || group.lowercase().contains("info") -> "📰 Actualités"
                    group.lowercase().contains("movie") || group.lowercase().contains("cinema") -> "🎬 Cinéma"
                    else -> group
                }
            }.map { group ->
                val title = group.key
                val show = group.value.map { channel ->
                    val streamurl = channel.url.orEmpty()
                    val channelname = channel.title.orEmpty()
                    val posterurl = channel.attributes["tvg-logo"].orEmpty()
                    val nation = channel.attributes["group-title"].orEmpty()
                    val key = channel.key.orEmpty()
                    val keyid = channel.keyid.orEmpty()

                    // Pass parsed headers through to LoadData
                    val headers = channel.headers
                        .plusIfAbsent("User-Agent", channel.userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .filterValues { it.isNotBlank() }

                    newLiveSearchResponse(
                        channelname,
                        LoadData(
                            url = streamurl,
                            title = channelname,
                            poster = posterurl,
                            nation = nation,
                            key = key,
                            keyid = keyid,
                            headers = headers
                        ).toJson(),
                        TvType.Live
                    ) {
                        this.posterUrl = posterurl
                        this.lang = "fr"
                    }
                }
                HomePageList(title, show, isHorizontalImages = true)
            }.sortedBy { if (it.name.contains("Françaises")) 0 else 1 } // Mettre les chaînes françaises en premier
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = fetchAllPlaylists()
        return data.items
            .filter { it.title?.contains(query, ignoreCase = true) ?: false }
            .map { channel ->
                val streamurl = channel.url.orEmpty()
                val channelname = channel.title.orEmpty()
                val posterurl = channel.attributes["tvg-logo"].orEmpty()
                val nation = channel.attributes["group-title"].orEmpty()
                val key = channel.key.orEmpty()
                val keyid = channel.keyid.orEmpty()

                val headers = channel.headers
                    .plusIfAbsent("User-Agent", channel.userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .filterValues { it.isNotBlank() }

                newLiveSearchResponse(
                    channelname,
                    LoadData(
                        url = streamurl,
                        title = channelname,
                        poster = posterurl,
                        nation = nation,
                        key = key,
                        keyid = keyid,
                        headers = headers
                    ).toJson(),
                    TvType.Live
                ) {
                    this.posterUrl = posterurl
                    this.lang = "fr"
                }
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<LoadData>(url)
        return newLiveStreamLoadResponse(data.title, data.url, url) {
            this.posterUrl = data.poster
            this.plot = "Chaîne: ${data.nation}"
        }
    }

    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val nation: String,
        val key: String,
        val keyid: String,
        val headers: Map<String, String> = emptyMap(),
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        val headers = loadData.headers.filterValues { it.isNotBlank() }.ifEmpty {
            mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        }

        if (loadData.url.contains(".m3u8", ignoreCase = true)) {
            callback.invoke(
                newExtractorLink(
                    "$name HLS",
                    name,
                    loadData.url,
                    "",
                    Qualities.P1080.value,
                    isM3u8 = true,
                    headers = headers,
                    extractorData = null
                )
            )
        } else if (loadData.url.contains(".mpd", ignoreCase = true)) {
            // Pour les flux DASH avec DRM
            if (loadData.key.isNotEmpty() && loadData.keyid.isNotEmpty()) {
                val key = decodeHex(loadData.key)
                val keyid = decodeHex(loadData.keyid)
                callback.invoke(
                    newDrmExtractorLink(
                        "$name DASH",
                        name,
                        loadData.url,
                        ExtractorLinkType.DASH,
                        CLEARKEY_UUID
                    ) {
                        this.quality = Qualities.P1080.value
                        this.key = key
                        this.kid = keyid
                        this.headers = headers
                    }
                )
            } else {
                // DASH sans DRM
                callback.invoke(
                    newExtractorLink(
                        "$name DASH",
                        name,
                        loadData.url,
                        "",
                        Qualities.P1080.value,
                        isDashMpd = true,
                        headers = headers,
                        extractorData = null
                    )
                )
            }
        } else {
            // URL directe
            callback.invoke(
                newExtractorLink(
                    "$name Direct",
                    name,
                    loadData.url,
                    "",
                    Qualities.P1080.value,
                    headers = headers,
                    extractorData = null
                )
            )
        }
        return true
    }
}

/* ----------------------------- DATA MODELS ------------------------------ */

data class Playlist(
    val items: List<PlaylistItem> = emptyList(),
)

data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null,
    val key: String? = null,
    val keyid: String? = null,
)

/* ----------------------------- PLAYLIST PARSER ------------------------------ */

class IptvPlaylistParser {
    /**
     * Parse M3U8 string into [Playlist]
     */
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    /**
     * Parse M3U8 content [InputStream] into [Playlist]
     */
    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()

        val firstLine = reader.readLine()
        if (firstLine?.isExtendedM3u() != true) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems = mutableListOf<PlaylistItem>()

        var currentTitle: String? = null
        var currentAttributes: Map<String, String> = emptyMap()
        var currentUserAgent: String? = null
        var currentReferrer: String? = null
        var currentHeaders: Map<String, String> = emptyMap()
        var currentKey: String? = null
        var currentKeyId: String? = null

        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachLine

            when {
                // Kodi clear key support (kid:key)
                line.startsWith("#KODIPROP:inputstream.adaptive.license_key=", ignoreCase = true) -> {
                    val license = line.substringAfter("=")
                    val parts = license.split(":")
                    if (parts.size == 2) {
                        currentKeyId = parts[0]
                        currentKey = parts[1]
                    }
                }

                // New item start
                line.startsWith(EXT_INF, ignoreCase = true) -> {
                    currentTitle = line.getTitle()
                    currentAttributes = line.getAttributes()
                    currentUserAgent = null
                    currentReferrer = null
                    currentHeaders = emptyMap()
                }

                // Capture ALL http-* variants dynamically from EXTVLCOPT
                line.startsWith(EXT_VLC_OPT, ignoreCase = true) -> {
                    val opt = line.removePrefix("$EXT_VLC_OPT:") // e.g. "http-user-agent=Mozilla/5.0 ..."
                    val eqIdx = opt.indexOf('=')
                    if (eqIdx > 0) {
                        val kRaw = opt.substring(0, eqIdx).trim()
                        val vRaw = opt.substring(eqIdx + 1).trim().replaceQuotesAndTrim()
                        if (kRaw.startsWith("http-", ignoreCase = true)) {
                            val headerKey = normalizeHeaderKey(kRaw)
                            val headerVal = vRaw
                            currentHeaders = currentHeaders + mapOf(headerKey to headerVal)

                            // track UA/Referer for convenience
                            when (headerKey) {
                                "User-Agent" -> currentUserAgent = headerVal
                                "Referer" -> currentReferrer = headerVal
                            }
                        }
                    }
                }

                // URL line
                !line.startsWith("#") -> {
                    val url = line.getUrl()

                    // Inline headers after '|': User-Agent, Referer, Origin, Cookie, etc.
                    val inlineParams = line.getUrlParameters()
                    var urlHeaders = currentHeaders
                    inlineParams.forEach { (k, v) ->
                        val key = normalizeHeaderKey(k)
                        urlHeaders = urlHeaders + mapOf(key to v)
                    }

                    val combinedUserAgent = inlineParams["User-Agent"] ?: currentUserAgent
                    if (!combinedUserAgent.isNullOrBlank()) {
                        urlHeaders = urlHeaders + mapOf("User-Agent" to combinedUserAgent)
                    }

                    if (currentReferrer != null && !urlHeaders.containsKey("Referer")) {
                        urlHeaders = urlHeaders + mapOf("Referer" to currentReferrer!!)
                    }

                    val key = line.getUrlParameter("key") ?: currentKey
                    val keyid = line.getUrlParameter("keyid") ?: currentKeyId

                    if (currentTitle != null && url != null) {
                        val finalAttributes = currentAttributes.toMutableMap().apply {
                            if (!key.isNullOrEmpty()) put("key", key)
                            if (!keyid.isNullOrEmpty()) put("keyid", keyid)
                        }

                        playlistItems.add(
                            PlaylistItem(
                                title = currentTitle ?: "",
                                attributes = finalAttributes,
                                url = url,
                                userAgent = combinedUserAgent,
                                headers = urlHeaders,
                                key = key,
                                keyid = keyid
                            )
                        )
                    }

                    // reset for next item
                    currentTitle = null
                    currentAttributes = emptyMap()
                    currentUserAgent = null
                    currentReferrer = null
                    currentHeaders = emptyMap()
                    currentKey = null
                    currentKeyId = null
                }
            }
        }

        return Playlist(playlistItems)
    }

    /* ----------------------------- Helpers ------------------------------ */

    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U, ignoreCase = true)

    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }

    // Extract a single parameter after the '|' (kept for DRM key/keyid)
    private fun String.getUrlParameter(key: String): String? {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex = Regex("$key=([^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()
        return keyRegex.find(paramsString)?.groups?.get(1)?.value?.let {
            URLDecoder.decode(it, StandardCharsets.UTF_8.name())
        }
    }

    // Extract ALL inline params after '|', normalize common header names
    private fun String.getUrlParameters(): Map<String, String> {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val headersString = replace(urlRegex, "").replaceQuotesAndTrim()
        if (headersString.isEmpty() || headersString == this) return emptyMap()

        val out = mutableMapOf<String, String>()
        headersString.split("&").forEach { token ->
            val idx = token.indexOf('=')
            if (idx > 0) {
                val rawKey = token.substring(0, idx).trim()
                val rawVal = token.substring(idx + 1).trim()
                val decodedVal = try {
                    URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name())
                } catch (_: Exception) {
                    rawVal
                }
                out[normalizeHeaderKey(rawKey)] = decodedVal.replaceQuotesAndTrim()
            }
        }
        return out
    }

    // Map VLC/http-* and common variants to proper HTTP header casing
    private fun normalizeHeaderKey(key: String): String {
        return when (key.lowercase()) {
            "http-user-agent", "user-agent" -> "User-Agent"
            "http-referrer", "http-referer", "referer", "referrer" -> "Referer"
            "http-origin", "origin" -> "Origin"
            "http-cookie", "cookie" -> "Cookie"
            else -> key // keep unknown headers as-is
        }
    }

    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()
        return attributesString.split(Regex("\\s")).mapNotNull {
            val pair = it.split("=")
            if (pair.size == 2) pair.first() to pair.last().replaceQuotesAndTrim() else null
        }.toMap()
    }

    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }
}

/* ----------------------------- ERRORS ------------------------------ */

sealed class PlaylistParserException(message: String) : Exception(message) {
    class InvalidHeader :
        PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")
}

/* ----------------------------- UTIL ------------------------------ */

private fun decodeHex(hexString: String): String {
    val length = hexString.length
    val byteArray = ByteArray(length / 2)
    for (i in 0 until length step 2) {
        byteArray[i / 2] = ((Character.digit(hexString[i], 16) shl 4) +
                Character.digit(hexString[i + 1], 16)).toByte()
    }
    val base64ByteArray = Base64.encode(byteArray, Base64.NO_PADDING)
    return String(base64ByteArray, StandardCharsets.UTF_8).trim()
}

// Small helper to add a header if missing
private fun Map<String, String>.plusIfAbsent(key: String, value: String?): Map<String, String> {
    return if (!this.containsKey(key) && !value.isNullOrBlank()) this + mapOf(key to value) else this
}
