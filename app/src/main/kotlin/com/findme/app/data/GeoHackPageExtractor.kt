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
            parse(html)
        }
    }

    /** Extracts every external HTTP(S) anchor in the supplied GeoHack HTML. */
    fun parse(html: String): List<GeoHackProviderLinks> {
        data class MutableProvider(
            val displayName: String,
            val map: MutableList<GeoHackPageLink> = mutableListOf(),
            val satellite: MutableList<GeoHackPageLink> = mutableListOf(),
            val other: MutableList<GeoHackPageLink> = mutableListOf()
        )

        val providers = linkedMapOf<String, MutableProvider>()
        for (match in anchorRegex.findAll(html)) {
            val rawUrl = match.groups[1]?.value
                ?: match.groups[2]?.value
                ?: match.groups[3]?.value
                ?: continue
            val url = decodeHtml(rawUrl.trim()).replace("&amp;", "&")
            // Link checker: if the URL includes numbers (coordinates/parameters), add it. Otherwise skip.
            if (!url.any { it.isDigit() }) continue
            val uri = runCatching { URI(url) }.getOrNull() ?: continue
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: continue
            if (scheme != "http" && scheme != "https") continue
            val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: continue

            val label = decodeHtml(match.groups[4]?.value.orEmpty())
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifEmpty { host }

            val provider = providers.getOrPut(host) { MutableProvider(providerName(host)) }
            val option = GeoHackPageLink(label = label, url = uri.toASCIIString())
            val searchable = "$label ${uri.path.orEmpty()} ${uri.query.orEmpty()}".lowercase(Locale.ROOT)
            when {
                listOf("satellite", "satellite view", "aerial", "imagery", "orthophoto").any(searchable::contains) ->
                    provider.satellite += option
                listOf("map", "maps", "street", "topographic", "topo").any(searchable::contains) ->
                    provider.map += option
                else -> provider.other += option
            }
        }

        return providers.values.map { provider ->
            GeoHackProviderLinks(
                provider = provider.displayName,
                map = provider.map.toList(),
                satellite = provider.satellite.toList(),
                other = provider.other.toList()
            )
        }
    }

    private fun providerName(host: String): String {
        val knownNames = mapOf(
            "google.com" to "Google",
            "google.co.uk" to "Google",
            "bing.com" to "Bing",
            "openstreetmap.org" to "OpenStreetMap",
            "maps.wikimedia.org" to "Wikimedia Maps",
            "mapy.cz" to "Mapy.cz",
            "yandex.com" to "Yandex",
            "here.com" to "HERE",
            "arcgis.com" to "ArcGIS"
        )
        knownNames[host]?.let { return it }
        return host.substringBefore('.').replaceFirstChar { it.titlecase(Locale.ROOT) }
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
