package net.kravos.weather.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.kravos.weather.data.ThemeMode

val Sky = Color(0xFF4FA3F7)

private val Dark = darkColorScheme(
    primary = Sky,
    onPrimary = Color(0xFF00213F),
    primaryContainer = Color(0xFF0E3A66),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFFBBC7DB),
    secondaryContainer = Color(0xFF2B3747),
    tertiary = Color(0xFFFFB86B),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE2E6EC),
    surface = Color(0xFF11151B),
    onSurface = Color(0xFFE2E6EC),
    surfaceVariant = Color(0xFF232A33),
    onSurfaceVariant = Color(0xFFB8C1CC),
    surfaceContainerLowest = Color(0xFF0A0D11),
    surfaceContainerLow = Color(0xFF151A21),
    surfaceContainer = Color(0xFF19202A),
    surfaceContainerHigh = Color(0xFF212833),
    surfaceContainerHighest = Color(0xFF2A323E),
    outline = Color(0xFF7E8894),
    outlineVariant = Color(0xFF353D48),
)

private val Light = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4FF),
    onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF535F70),
    tertiary = Color(0xFFB25F00),
    background = Color(0xFFF6F8FB),
    surface = Color(0xFFF9FAFC),
    surfaceContainerLow = Color(0xFFF0F3F7),
    surfaceContainer = Color(0xFFEAEEF3),
    surfaceContainerHigh = Color(0xFFE4E8EE),
    surfaceContainerHighest = Color(0xFFDDE2E9),
)

private val Type = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 96.sp, letterSpacing = (-3).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

@Composable
fun WeatherTheme(mode: ThemeMode, dynamic: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = Type, content = content)
}
