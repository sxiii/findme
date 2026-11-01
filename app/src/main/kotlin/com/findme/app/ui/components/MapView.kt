package com.findme.app.ui.components

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    
                    loadUrl("file:///android_asset/map.html?lat=$latitude&lon=$longitude")
                }
            },
            update = { webView ->
                webView.evaluateJavascript("updateLocation($latitude, $longitude);", null)
            }
        )

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
