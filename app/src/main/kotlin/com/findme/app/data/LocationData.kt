package com.findme.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocationCoordinates(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class ReverseGeocodeResult(
    @SerialName("address")
    val address: AddressInfo,
    val lat: String,
    val lon: String,
    @SerialName("display_name")
    val displayName: String
)

@Serializable
data class AddressInfo(
    val road: String? = null,
    val suburb: String? = null,
    val city: String? = null,
    val town: String? = null,
    val county: String? = null,
    val state: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    @SerialName("country_code")
    val countryCode: String? = null
)

data class PlaceInfo(
    val coordinates: LocationCoordinates,
    val address: String,
    val description: String,
    val displayName: String
)

@Serializable
data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList()
)

@Serializable
data class OverpassElement(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String> = emptyMap()
)

data class NearbyPoi(
    val name: String,
    val category: String,
    val distanceMeters: Double = 0.0
)
