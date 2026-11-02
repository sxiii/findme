package com.findme.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.findme.app.ui.screens.LocationScreen
import com.findme.app.ui.theme.FindmeTheme

@Composable
fun FindmeApp() {
    val isDarkMode = isSystemInDarkTheme()
    FindmeTheme(isDarkMode = isDarkMode) {
        LocationScreen(isDarkMode = isDarkMode)
    }
}
