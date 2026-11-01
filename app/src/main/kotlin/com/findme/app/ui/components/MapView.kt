package com.findme.app.ui.components

import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun MapView(
    latitude: Double,
    longitude: Double,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.loadsImagesAutomatically = true
                
                val htmlContent = generateMapHtml(latitude, longitude, isDarkMode)
                loadDataWithBaseURL("https://app.local", htmlContent, "text/html", "utf-8", null)
            }
        },
        update = { webView ->
            val htmlContent = generateMapHtml(latitude, longitude, isDarkMode)
            webView.loadDataWithBaseURL("https://app.local", htmlContent, "text/html", "utf-8", null)
        }
    )
}

private fun generateMapHtml(latitude: Double, longitude: Double, isDarkMode: Boolean): String {
    val tileLayer = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    val attribution = "&copy; <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors"
    val bgColor = if (isDarkMode) "#1a1a1a" else "#ffffff"

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css" />
            <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js"></script>
            <style>
                html, body { margin: 0; padding: 0; height: 100%; width: 100%; background-color: $bgColor; }
                #map { position: absolute; top: 0; bottom: 0; left: 0; right: 0; background-color: $bgColor; }
                .leaflet-container { background-color: $bgColor; width: 100%; height: 100%; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                const map = L.map('map').setView([$latitude, $longitude], 14);
                
                L.tileLayer('$tileLayer', {
                    attribution: '$attribution',
                    maxZoom: 19,
                    subdomains: 'abc'
                }).addTo(map);
                
                L.marker([$latitude, $longitude])
                    .addTo(map)
                    .bindPopup('Location: $latitude, $longitude')
                    .openPopup();
                    
                L.circleMarker([$latitude, $longitude], {
                    radius: 100,
                    fillColor: '#0066ff',
                    color: '#0066ff',
                    weight: 2,
                    opacity: 0.3,
                    fillOpacity: 0.1
                }).addTo(map);

                // Ensure Leaflet recalculates size after AndroidView layout measurement
                setTimeout(function() {
                    map.invalidateSize();
                }, 300);
            </script>
        </body>
        </html>
    """.trimIndent()
}
