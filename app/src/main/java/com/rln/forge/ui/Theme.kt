package com.rln.forge.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Dark, calm, low-contrast theme. No bright accents, no gradients.
 */
private val ForgeColors = darkColorScheme(
    primary = Color(0xFF7FA8C9),
    onPrimary = Color(0xFF101418),
    secondary = Color(0xFF8A94A0),
    background = Color(0xFF14161A),
    onBackground = Color(0xFFE3E5E8),
    surface = Color(0xFF1C1F24),
    onSurface = Color(0xFFE3E5E8),
    surfaceVariant = Color(0xFF25282E),
    onSurfaceVariant = Color(0xFF9AA3AD),
    outline = Color(0xFF3A3F46),
    error = Color(0xFFD98C8C),
    onError = Color(0xFF1A1214)
)

@Composable
fun ForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ForgeColors,
        content = content
    )
}
