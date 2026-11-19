package com.morsemessage.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Brass = Color(0xFFF4B942)
private val Navy = Color(0xFF12263A)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F4E79),
    secondary = Color(0xFF8A5A00),
    tertiary = Brass,
)

private val DarkColors = darkColorScheme(
    primary = Brass,
    secondary = Color(0xFF9CC3E6),
    background = Navy,
    surface = Navy,
)

@Composable
fun MorseMessageTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
