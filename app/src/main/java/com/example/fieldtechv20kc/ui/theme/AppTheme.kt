package com.example.fieldtechv20kc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AppTheme(
    isDarkMode: Boolean = isSystemInDarkTheme(),
    accentColor: String = "#1976D2",
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isDarkMode -> darkColorScheme(
            primary = Color(android.graphics.Color.parseColor(accentColor)),
            primaryContainer = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.2f),
            onPrimary = Color.White,
            onPrimaryContainer = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.9f),
            secondary = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.7f),
            secondaryContainer = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.5f),
            onSecondary = Color.White,
            onSecondaryContainer = Color.White,
            tertiary = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.6f),
            tertiaryContainer = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.15f),
            onTertiary = Color.White,
            onTertiaryContainer = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.7f),
            error = Color(0xFFFF6B6B),
            onError = Color.White,
            errorContainer = Color(0xFFFF6B6B).copy(alpha = 0.1f),
            onErrorContainer = Color(0xFFFF6B6B),
            background = Color(0xFF121212),
            onBackground = Color(0xFFE1E1E1),
            surface = Color(0xFF1E1E1E),
            onSurface = Color(0xFFE1E1E1),
            surfaceVariant = Color(0xFF2D2D2D),
            onSurfaceVariant = Color(0xFFB3B3B3),
            outline = Color(0xFF666666),
            outlineVariant = Color(0xFF404040)
        )
        else -> lightColorScheme(
            primary = Color(android.graphics.Color.parseColor(accentColor)),
            primaryContainer = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.1f),
            onPrimary = Color.White,
            onPrimaryContainer = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.9f),
            secondary = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.7f),
            secondaryContainer = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.5f),
            onSecondary = Color.White,
            onSecondaryContainer = Color.White,
            tertiary = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.6f),
            tertiaryContainer = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.08f),
            onTertiary = Color.White,
            onTertiaryContainer = Color(android.graphics.Color.parseColor(accentColor)).copy(alpha = 0.7f),
            error = Color(0xFFD32F2F),
            onError = Color.White,
            errorContainer = Color(0xFFD32F2F).copy(alpha = 0.1f),
            onErrorContainer = Color(0xFFD32F2F),
            background = Color(0xFFFFFBFE),
            onBackground = Color(0xFF1C1B1F),
            surface = Color(0xFFFFFBFE),
            onSurface = Color(0xFF1C1B1F),
            surfaceVariant = Color(0xFFE7E0EC),
            onSurfaceVariant = Color(0xFF49454F),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFCAC4D0)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
