package io.github.patrickric.akkuhealthalarm.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.patrickric.akkuhealthalarm.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7A5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2D3),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4B635A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE9DC),
    onSecondaryContainer = Color(0xFF072019),
    tertiary = Color(0xFF3E6374),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC2E8FC),
    onTertiaryContainer = Color(0xFF001F2A),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4FAF6),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF4FAF6),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEDF5F0),
    surfaceContainer = Color(0xFFE7EFEA),
    surfaceContainerHigh = Color(0xFFE1EAE4),
    surfaceContainerHighest = Color(0xFFDBE4DE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FDAB5),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513D),
    onPrimaryContainer = Color(0xFFA6F2D3),
    secondary = Color(0xFFB2CCC1),
    onSecondary = Color(0xFF1E352D),
    secondaryContainer = Color(0xFF344C43),
    onSecondaryContainer = Color(0xFFCDE9DC),
    tertiary = Color(0xFFA6CCE0),
    onTertiary = Color(0xFF0A3444),
    tertiaryContainer = Color(0xFF254B5C),
    onTertiaryContainer = Color(0xFFC2E8FC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DF),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF404944),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
)

/** Amber for "above the range" and warnings; Material has no such role. */
@Immutable
data class ExtraColors(
    val caution: Color,
    val onCaution: Color,
    val cautionContainer: Color,
    val onCautionContainer: Color,
    /** Line colour for the current in charts while charging (orange like the charging bolt). */
    val charge: Color,
    /** Line colour for the current in charts while running on battery. */
    val discharge: Color,
)

private val LightExtra = ExtraColors(Color(0xFFB86E00), Color.White, Color(0xFFFFDDB3), Color(0xFF2A1800), Color(0xFFE8590C), Color(0xFF1565C0))
private val DarkExtra = ExtraColors(Color(0xFFFFB95C), Color(0xFF462A00), Color(0xFF5C3C00), Color(0xFFFFDDB3), Color(0xFFFF9E5E), Color(0xFF6FA8FF))

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = AppShapes,
            content = content,
        )
    }
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
