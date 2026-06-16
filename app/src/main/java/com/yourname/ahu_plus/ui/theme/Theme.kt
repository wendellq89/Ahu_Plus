package com.yourname.ahu_plus.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = AhuBlueDark,
    onPrimary = ColorTokens.DarkOnPrimary,
    primaryContainer = ColorTokens.DarkPrimaryContainer,
    onPrimaryContainer = AhuDarkOnSurface,
    secondary = AhuTeal,
    onSecondary = ColorTokens.DarkOnPrimary,
    secondaryContainer = ColorTokens.DarkSecondaryContainer,
    onSecondaryContainer = AhuDarkOnSurface,
    tertiary = AhuOrange,
    onTertiary = ColorTokens.DarkOnPrimary,
    background = AhuDarkBackground,
    onBackground = AhuDarkOnSurface,
    surface = AhuDarkSurface,
    onSurface = AhuDarkOnSurface,
    surfaceVariant = AhuDarkSurfaceVariant,
    onSurfaceVariant = AhuDarkOnSurfaceVariant,
    outline = AhuDarkOutline,
    outlineVariant = AhuDarkOutline.copy(alpha = 0.72f),
    error = ColorTokens.DarkError,
    errorContainer = ColorTokens.DarkErrorContainer,
)

private val LightColorScheme = lightColorScheme(
    primary = AhuBlue,
    onPrimary = ColorTokens.LightOnPrimary,
    primaryContainer = AhuBlueLight,
    onPrimaryContainer = ColorTokens.LightOnPrimaryContainer,
    secondary = AhuTeal,
    onSecondary = ColorTokens.LightOnPrimary,
    secondaryContainer = Color(0xFFD4F3EF),
    onSecondaryContainer = Color(0xFF063D38),
    tertiary = AhuOrange,
    onTertiary = ColorTokens.LightOnPrimary,
    background = AhuLightBackground,
    onBackground = AhuLightOnSurface,
    surface = AhuLightSurface,
    onSurface = AhuLightOnSurface,
    surfaceVariant = AhuLightSurfaceVariant,
    onSurfaceVariant = AhuLightOnSurfaceVariant,
    outline = AhuLightOutline,
    outlineVariant = AhuLightOutline.copy(alpha = 0.72f),
    error = AhuRed,
    errorContainer = Color(0xFFFFDAD7),
)

@Composable
fun Ahu_PlusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private object ColorTokens {
    val LightOnPrimary = androidx.compose.ui.graphics.Color.White
    val LightOnPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF102C5E)
    val DarkOnPrimary = androidx.compose.ui.graphics.Color(0xFF08111F)
    val DarkPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF24395F)
    val DarkSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF153F3B)
    val DarkError = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
    val DarkErrorContainer = androidx.compose.ui.graphics.Color(0xFF5F1515)
}
