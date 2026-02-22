package com.example.accessu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val UofAColorScheme = lightColorScheme(
    primary = UofAGreen,
    onPrimary = Color.White,
    primaryContainer = UofAGreenDark,
    onPrimaryContainer = UofAGoldLight,
    secondary = UofAGold,
    onSecondary = UofAGreenDark,
    secondaryContainer = UofAGoldLight,
    onSecondaryContainer = UofAGreen,
    background = UofAWhite,
    onBackground = UofAGreenDark,
    surface = Color.White,
    onSurface = UofAGreenDark
)

@Composable
fun AccessUTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = UofAColorScheme,
        typography = Typography,
        content = content
    )
}