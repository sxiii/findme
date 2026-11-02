package com.findme.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.findme.app.data.LocationCoordinates
import com.findme.app.data.LocationRepository
import com.findme.app.data.NearbyPoi
import com.findme.app.data.PlaceInfo
import com.findme.app.ui.components.MapView
import com.findme.app.ui.components.PlaceCard
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun LocationScreen(isDarkMode: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { LocationRepository() }

    var location by remember { mutableStateOf<LocationCoordinates?>(null) }
    var placeInfo by remember { mutableStateOf<PlaceInfo?>(null) }
    var nearbyPois by remember { mutableStateOf<List<NearbyPoi>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (permissionGranted) {
            requestLocationUpdate(context, scope) { lat, lon ->
                location = LocationCoordinates(lat, lon)
                fetchPlaceInfo(scope, repository, lat, lon) { info, err ->
                    if (info != null) {
                        placeInfo = info
                        error = null
                    } else {
                        error = err
                    }
                    isLoading = false
                }
                scope.launch {
                    repository.getNearbyPois(lat, lon).onSuccess { pois ->
                        nearbyPois = pois
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = {
                isLoading = true
                error = null
                if (permissionGranted) {
                    requestLocationUpdate(context, scope) { lat, lon ->
                        location = LocationCoordinates(lat, lon)
                        fetchPlaceInfo(scope, repository, lat, lon) { info, err ->
                            if (info != null) {
                                placeInfo = info
                                error = null
                            } else {
                                error = err
                            }
                            isLoading = false
                        }
                        scope.launch {
                            repository.getNearbyPois(lat, lon).onSuccess { pois ->
                                nearbyPois = pois
                            }
                        }
                    }
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !isLoading
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isLoading) "Locating..." else "Get Location")
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        error?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        placeInfo?.let { info ->
            PlaceCard(placeInfo = info)
            MapView(
                latitude = info.coordinates.latitude,
                longitude = info.coordinates.longitude,
                isDarkMode = isDarkMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(300.dp)
            )

            if (nearbyPois.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Nearby Objects & POIs (OpenStreetMap)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        nearbyPois.take(15).forEach { poi ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        poi.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        poi.category,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Text(
                                    "${poi.distanceMeters.toInt()}m",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

private fun requestLocationUpdate(
    context: Context,
    scope: CoroutineScope,
    onLocation: (Double, Double) -> Unit
) {
    scope.launch {
        try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        onLocation(location.latitude, location.longitude)
                    }
                }.addOnFailureListener {
                    onLocation(40.7128, -74.0060) // Fallback to NYC for demo
                }
            }
        } catch (e: Exception) {
            onLocation(40.7128, -74.0060) // Fallback
        }
    }
}

private fun fetchPlaceInfo(
    scope: CoroutineScope,
    repository: LocationRepository,
    latitude: Double,
    longitude: Double,
    onResult: (PlaceInfo?, String?) -> Unit
) {
    scope.launch {
        val result = repository.reverseGeocode(latitude, longitude)
        result.onSuccess { info ->
            onResult(info, null)
        }.onFailure { error ->
            onResult(null, error.message ?: "Unknown error")
        }
    }
}
