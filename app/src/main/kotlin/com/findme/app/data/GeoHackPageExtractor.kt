package com.findme.app.data

import java.io.IOException
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A link extracted from a GeoHack page. */
data class GeoHackPageLink(
    val label: String,
    val url: String
)

/** Links from one provider, grouped into the columns shown by the map-options table. */
data class GeoHackProviderLinks(
    val provider: String,
    val map: List<GeoHackPageLink>,
    val satellite: List<GeoHackPageLink>,
    val other: List<GeoHackPageLink>
)

/** Fetches GeoHack's coordinate page and extracts its external map links. */
object GeoHackPageExtractor {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val anchorRegex = Regex(
        """<a\b[^>]*\bhref\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))[^>]*>(.*?)</a\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** Builds a GeoHack URL using decimal coordinates and hemisphere markers. */
    fun pageUrl(latitude: Double, longitude: Double): String {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }

        val latHemisphere = if (latitude < 0) "S" else "N"
        val lonHemisphere = if (longitude < 0) "W" else "E"
        val lat = String.format(Locale.US, "%.6f", kotlin.math.abs(latitude))
        val lon = String.format(Locale.US, "%.6f", kotlin.math.abs(longitude))
        return "https://geohack.toolforge.org/geohack.php?params=${lat}_${latHemisphere}_${lon}_${lonHemisphere}"
    }

    /** Fetches the GeoHack page and returns its external HTTP(S) links grouped by provider. */
    suspend fun extract(latitude: Double, longitude: Double): List<GeoHackProviderLinks> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(pageUrl(latitude, longitude))
            .header("User-Agent", "Findme-Android/1.0 (GeoHack map link viewer)")
            .header("Accept", "text/html")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GeoHack returned HTTP ${response.code}.")
            }
            val html = response.body?.string()
                ?: throw IOException("GeoHack returned an empty page.")
            parse(html, latitude, longitude)
        }
    }

    /** Extracts every external HTTP(S) anchor in the supplied GeoHack HTML, keeping only coordinate links and filtering out Wikipedia/info pages. */
    fun parse(html: String, latitude: Double? = null, longitude: Double? = null): List<GeoHackProviderLinks> {
        data class MutableProvider(
            val displayName: String,
            val map: MutableList<GeoHackPageLink> = mutableListOf(),
            val satellite: MutableList<GeoHackPageLink> = mutableListOf(),
            val other: MutableList<GeoHackPageLink> = mutableListOf()
        )

        val coords = if (latitude != null && longitude != null) {
            Pair(latitude, longitude)
        } else {
            extractCoordinatesFromHtml(html)
        }

        val providers = linkedMapOf<String, MutableProvider>()
        for (match in anchorRegex.findAll(html)) {
            val rawUrl = match.groups[1]?.value
                ?: match.groups[2]?.value
                ?: match.groups[3]?.value
                ?: continue
            var url = decodeHtml(rawUrl.trim()).replace("&amp;", "&")
            if (url.startsWith("//")) {
                url = "https:$url"
            }
            val uri = runCatching { URI(url) }.getOrNull() ?: continue
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: continue
            if (scheme != "http" && scheme != "https") continue
            val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: continue

            val label = decodeHtml(match.groups[4]?.value.orEmpty())
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifEmpty { host }

            // Filter out all Wikipedia or info/documentation pages
            if (isWikiOrInfo(url, label, host)) continue

            // Keep only pages that include coordinates in the URL
            if (coords != null) {
                if (!hasCoordinatesInUrl(url, coords.first, coords.second)) continue
            } else {
                if (!url.any { it.isDigit() }) continue
            }

            val provider = providers.getOrPut(host) { MutableProvider(providerName(host)) }
            val option = GeoHackPageLink(label = label, url = uri.toASCIIString())

            // Deduplicate links with the exact same URL within this provider
            val alreadyPresent = provider.map.any { it.url == option.url } ||
                provider.satellite.any { it.url == option.url } ||
                provider.other.any { it.url == option.url }
            if (alreadyPresent) continue

            val searchable = "$label ${uri.path.orEmpty()} ${uri.query.orEmpty()}".lowercase(Locale.ROOT)
            when {
                listOf("satellite", "satellite view", "aerial", "imagery", "orthophoto").any(searchable::contains) ->
                    provider.satellite += option
                listOf("map", "maps", "street", "topographic", "topo").any(searchable::contains) ->
                    provider.map += option
                else -> provider.other += option
            }
        }

        return providers.values
            .filter { it.map.isNotEmpty() || it.satellite.isNotEmpty() || it.other.isNotEmpty() }
            .map { provider ->
                GeoHackProviderLinks(
                    provider = provider.displayName,
                    map = provider.map.toList(),
                    satellite = provider.satellite.toList(),
                    other = provider.other.toList()
                )
            }
    }

    private fun extractCoordinatesFromHtml(html: String): Pair<Double, Double>? {
        val geoMatch = Regex("""href\s*=\s*["']?geo:([+-]?\d+(?:\.\d+)?),([+-]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE).find(html)
        if (geoMatch != null) {
            val lat = geoMatch.groupValues[1].toDoubleOrNull()
            val lon = geoMatch.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null) return Pair(lat, lon)
        }
        val titleMatch = Regex("""<title>GeoHack\s*\(\s*([+-]?\d+(?:\.\d+)?)\s*;\s*([+-]?\d+(?:\.\d+)?)\s*\)""", RegexOption.IGNORE_CASE).find(html)
        if (titleMatch != null) {
            val lat = titleMatch.groupValues[1].toDoubleOrNull()
            val lon = titleMatch.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null) return Pair(lat, lon)
        }
        return null
    }

    private fun isWikiOrInfo(url: String, label: String, host: String): Boolean {
        val hostLower = host.lowercase(Locale.ROOT)
        val urlLower = url.lowercase(Locale.ROOT)
        val labelLower = label.lowercase(Locale.ROOT)

        val wikiHosts = listOf(
            "wikipedia.org", "wikimedia.org", "mediawiki.org", "wikidata.org",
            "wikibooks.org", "wikinews.org", "wikiquote.org", "wikisource.org",
            "wikiversity.org", "wikivoyage.org", "wiktionary.org", "toolforge.org",
            "wmflabs.org", "bitbucket.org", "github.com", "microformats.org"
        )
        if (wikiHosts.any { hostLower == it || hostLower.endsWith(".$it") || hostLower.contains(it) }) {
            return true
        }

        if (urlLower.contains("/wiki/") || urlLower.contains("/w/index.php") ||
            urlLower.contains("special:") || urlLower.contains("template:") ||
            urlLower.contains("wikipedia:") || urlLower.contains("help:")) {
            return true
        }

        val infoLabels = setOf(
            "info", "information", "about", "faq", "documentation", "doc",
            "bug tracker", "source code", "report inaccuracies", "edit",
            "talk", "history", "purge", "transclusions", "microformats",
            "geo microformat", "coord", "coordinates", "view source",
            "read", "sandbox", "diff", "testcases", "article"
        )
        if (labelLower in infoLabels || infoLabels.any { labelLower == it || labelLower.startsWith("$it ") || labelLower.endsWith(" $it") }) {
            return true
        }
        if (labelLower.contains("wikipedia") || labelLower.contains("wikidata") || labelLower.contains("wikimedia")) {
            return true
        }

        return false
    }

    private fun hasCoordinatesInUrl(url: String, latitude: Double, longitude: Double): Boolean {
        fun getCandidates(value: Double): List<String> {
            val candidates = mutableSetOf<String>()
            val absVal = kotlin.math.abs(value)
            for (p in 6 downTo 2) {
                val formatted = String.format(Locale.US, "%.${p}f", absVal)
                candidates += formatted
                candidates += formatted.trimEnd('0').trimEnd('.')
            }
            candidates += absVal.toString()
            return candidates.filter { it.length >= 3 }
        }

        val latCandidates = getCandidates(latitude)
        val lonCandidates = getCandidates(longitude)

        val hasLat = latCandidates.any { url.contains(it) }
        val hasLon = lonCandidates.any { url.contains(it) }
        return hasLat && hasLon
    }

    private fun providerName(host: String): String {
        val knownNames = mapOf(
            "google.com" to "Google Maps",
            "google.co.uk" to "Google Maps",
            "bing.com" to "Bing Maps",
            "openstreetmap.org" to "OpenStreetMap",
            "maps.apple.com" to "Apple Maps",
            "duckduckgo.com" to "DuckDuckGo",
            "earth.google.com" to "Google Earth",
            "earthengine.google.com" to "Google Earth Engine",
            "mapy.cz" to "Mapy.cz",
            "en.mapy.cz" to "Mapy.cz",
            "mapy.com" to "Mapy.com",
            "yandex.com" to "Yandex Maps",
            "yandex.ru" to "Yandex Maps",
            "maps.yandex.com" to "Yandex Maps",
            "maps.yandex.ru" to "Yandex Maps",
            "here.com" to "HERE WeGo",
            "wego.here.com" to "HERE WeGo",
            "arcgis.com" to "ArcGIS",
            "livingatlas.arcgis.com" to "ArcGIS Living Atlas",
            "mapquest.com" to "MapQuest",
            "tomtom.com" to "TomTom",
            "plan.tomtom.com" to "TomTom",
            "waze.com" to "Waze",
            "waze.to" to "Waze",
            "opentopomap.org" to "OpenTopoMap",
            "openhistoricalmap.org" to "OpenHistoricalMap",
            "openrailwaymap.org" to "OpenRailwayMap",
            "waterwaymap.org" to "WaterwayMap",
            "openinframap.org" to "OpenInfraMap",
            "mapillary.com" to "Mapillary",
            "strava.com" to "Strava",
            "windy.com" to "Windy",
            "flightradar24.com" to "Flightradar24",
            "marinetraffic.com" to "MarineTraffic",
            "geocaching.com" to "Geocaching",
            "peakfinder.org" to "PeakFinder",
            "skyvector.com" to "SkyVector",
            "topozone.com" to "TopoZone",
            "caltopo.com" to "CalTopo",
            "naturalatlas.com" to "Natural Atlas"
        )
        knownNames[host]?.let { return it }

        val parts = host.split('.')
        val base = if (parts.size >= 2 && parts[0] in listOf("maps", "map", "earth", "web", "kart", "viewer", "gis", "topo", "en", "www")) {
            parts[1]
        } else {
            parts[0]
        }
        return base.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&#39;", "'", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: match.value
        }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            match.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: match.value
        }
}