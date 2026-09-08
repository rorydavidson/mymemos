package com.keltruc.mymemos.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Warm paper neutrals with a deep pine accent. Surfaces step gently so cards read as
// paper on a desk rather than boxes on a grid.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBE0),
    onPrimaryContainer = Color(0xFF0B2E25),
    secondary = Color(0xFF8A5A2B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF6E3CF),
    onSecondaryContainer = Color(0xFF3A2410),
    tertiary = Color(0xFF3F6375),
    tertiaryContainer = Color(0xFFD5E8F2),
    onTertiaryContainer = Color(0xFF12303E),
    background = Color(0xFFF1ECE4),
    onBackground = Color(0xFF1E1B17),
    surface = Color(0xFFF1ECE4),
    onSurface = Color(0xFF1E1B17),
    surfaceVariant = Color(0xFFE6DFD5),
    onSurfaceVariant = Color(0xFF5B5650),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F3ED),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE7E1D8),
    surfaceContainerHighest = Color(0xFFDDD6CB),
    outline = Color(0xFF8B857D),
    outlineVariant = Color(0xFFDCD4C9),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8ED9C2),
    onPrimary = Color(0xFF003729),
    primaryContainer = Color(0xFF1B5446),
    onPrimaryContainer = Color(0xFFCDEBE0),
    secondary = Color(0xFFE6BE95),
    onSecondary = Color(0xFF3A2410),
    secondaryContainer = Color(0xFF55391F),
    onSecondaryContainer = Color(0xFFF6E3CF),
    tertiary = Color(0xFFA7CCE0),
    tertiaryContainer = Color(0xFF2C4A5B),
    onTertiaryContainer = Color(0xFFD5E8F2),
    background = Color(0xFF15130F),
    onBackground = Color(0xFFEDE7DF),
    surface = Color(0xFF15130F),
    onSurface = Color(0xFFEDE7DF),
    surfaceVariant = Color(0xFF2A2721),
    onSurfaceVariant = Color(0xFFC5BEB4),
    surfaceContainerLowest = Color(0xFF0F0D0A),
    surfaceContainerLow = Color(0xFF1B1915),
    surfaceContainer = Color(0xFF211E19),
    surfaceContainerHigh = Color(0xFF2A2721),
    surfaceContainerHighest = Color(0xFF34302A),
    outline = Color(0xFF8F887F),
    outlineVariant = Color(0xFF3B3731),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MyMemosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
