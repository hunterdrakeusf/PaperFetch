package com.example.ui.theme

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
    primary = TealAccentLight,
    onPrimary = OceanNavy,
    primaryContainer = DeepNavy,
    onPrimaryContainer = IceWhite,
    secondary = SoftBlue,
    onSecondary = OceanNavy,
    secondaryContainer = SlateBlue,
    onSecondaryContainer = Color.White,
    tertiary = AmberWarning,
    onTertiary = OceanNavy,
    background = NeutralDarkBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = NeutralDarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = NeutralDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = RoseError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = TealAccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F5EE),
    onPrimaryContainer = Color(0xFF003830),
    secondary = SlateBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = OceanNavy,
    tertiary = AmberWarning,
    onTertiary = Color.White,
    background = NeutralLightBackground,
    onBackground = NeutralTextPrimary,
    surface = NeutralLightSurface,
    onSurface = NeutralTextPrimary,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = NeutralTextSecondary,
    error = RoseError,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false so our distinctive scientific palette is always featured
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
