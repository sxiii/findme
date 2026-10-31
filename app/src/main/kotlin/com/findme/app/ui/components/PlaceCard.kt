package com.findme.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.findme.app.data.PlaceInfo

@Composable
fun PlaceCard(placeInfo: PlaceInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Place Information",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Description", placeInfo.displayName)
                InfoRow("Address", placeInfo.address)
                InfoRow(
                    "Coordinates",
                    "${String.format("%.4f", placeInfo.coordinates.latitude)}, " +
                            "${String.format("%.4f", placeInfo.coordinates.longitude)}"
                )
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
