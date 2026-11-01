package com.findme.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class LocationRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<PlaceInfo> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "FindMe/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val result = json.decodeFromString(ReverseGeocodeResult.serializer(), body)

                val address = buildAddressString(result.address)
                val placeInfo = PlaceInfo(
                    coordinates = LocationCoordinates(latitude, longitude),
                    address = address,
                    description = result.displayName,
                    displayName = result.displayName
                )

                Result.success(placeInfo)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun buildAddressString(address: AddressInfo): String {
        val parts = mutableListOf<String>()
        address.road?.let { parts.add(it) }
        address.suburb?.let { parts.add(it) }
        address.city?.let { parts.add(it) }
        address.town?.let { parts.add(it) }
        address.postcode?.let { parts.add(it) }
        address.country?.let { parts.add(it) }
        return parts.joinToString(", ")
    }

    suspend fun getNearbyPois(latitude: Double, longitude: Double): Result<List<NearbyPoi>> =
        withContext(Dispatchers.IO) {
            try {
                val query = "[out:json];node(around:500,$latitude,$longitude)[name];out body 20;"
                val url = "https://overpass-api.de/api/interpreter"

                val body = okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse("application/x-www-form-urlencoded"),
                    "data=" + java.net.URLEncoder.encode(query, "UTF-8")
                )

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("User-Agent", "FindMe/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }

                val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))
                val overpassResult = json.decodeFromString(OverpassResponse.serializer(), responseBody)

                val pois = overpassResult.elements.mapNotNull { elem ->
                    val name = elem.tags["name"] ?: return@mapNotNull null
                    val category = elem.tags["amenity"] ?: elem.tags["shop"] ?: elem.tags["tourism"] ?: elem.tags["leisure"] ?: "poi"
                    val dist = calculateDistanceMeters(latitude, longitude, elem.lat, elem.lon)
                    NearbyPoi(name = name, category = category.replaceFirstChar { it.uppercase() }, distanceMeters = dist)
                }.sortedBy { it.distanceMeters }

                Result.success(pois)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
