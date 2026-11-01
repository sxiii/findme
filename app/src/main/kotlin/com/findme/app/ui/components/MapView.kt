package com.findme.app.ui.components

import android.content.Intent
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun MapView(
    latitude: Double,
    longitude: Double,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true) {
                                errorMessage = "Web Error: ${error?.description}"
                            }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            consoleMessage?.let {
                                val msg = it.message()
                                val line = it.lineNumber()
                                if (it.messageLevel() == ConsoleMessage.MessageLevel.ERROR || msg.contains("Error", true) || msg.contains("JS_ERR", true)) {
                                    errorMessage = "JS Error: $msg (line $line)"
                                }
                            }
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.allowFileAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true
                    
                    val htmlContent = generateMapHtml(latitude, longitude, isDarkMode)
                    loadDataWithBaseURL("https://app.local", htmlContent, "text/html", "utf-8", null)
                }
            },
            update = { webView ->
                val htmlContent = generateMapHtml(latitude, longitude, isDarkMode)
                webView.loadDataWithBaseURL("https://app.local", htmlContent, "text/html", "utf-8", null)
            }
        )

        // Debug error banner if any
        errorMessage?.let { err ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = err,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Small compact button to open in external maps app
        SmallFloatingActionButton(
            onClick = {
                val gmmIntentUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(Location)")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                try {
                    context.startActivity(mapIntent)
                } catch (e: Exception) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=16/$latitude/$longitude"))
                    context.startActivity(browserIntent)
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                imageVector = Icons.Filled.OpenInNew,
                contentDescription = "Open in Maps"
            )
        }
    }
}

private fun generateMapHtml(latitude: Double, longitude: Double, isDarkMode: Boolean): String {
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
                window.onerror = function(msg, url, line, col, error) {
                    console.error("JS_ERR: " + msg + " at line " + line + ":" + col);
                };
                try {
                    var map = L.map('map').setView([$latitude, $longitude], 14);
                    
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        attribution: '&copy; OpenStreetMap contributors',
                        maxZoom: 19
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

                    setTimeout(function() {
                        map.invalidateSize();
                    }, 300);
                } catch (e) {
                    console.error("Leaflet Exception: " + e.message);
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}
