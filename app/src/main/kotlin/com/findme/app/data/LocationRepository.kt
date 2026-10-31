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
}
