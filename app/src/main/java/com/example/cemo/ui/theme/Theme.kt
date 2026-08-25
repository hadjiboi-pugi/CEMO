package com.example.cemo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.cemo.AppState
import com.example.cemo.ThemeMode

// ── Light scheme ──────────────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary              = PrimaryGreen,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFD7F0E1),
    onPrimaryContainer   = Color(0xFF04381D),

    secondary            = LightOnSurfaceVariant,
    onSecondary          = Color.White,
    secondaryContainer   = LightSurfaceVariant,
    onSecondaryContainer = LightOnSurface,

    error                = AllocationRed,
    onError              = Color.White,
    errorContainer       = Color(0xFFFCE4E2),
    onErrorContainer     = Color(0xFF5C1310),

    background           = LightBackground,
    onBackground         = LightOnBackground,
    surface              = LightSurface,
    onSurface            = LightOnSurface,
    surfaceVariant       = LightSurfaceVariant,
    onSurfaceVariant     = LightOnSurfaceVariant,
    outline              = LightOutline,
    outlineVariant       = Color(0xFFEAF0ED),
)

// ── Dark scheme ───────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary              = PrimaryGreenOnDark,
    onPrimary            = Color(0xFF00301A),
    primaryContainer     = Color(0xFF10432A),
    onPrimaryContainer   = Color(0xFFA7F3C6),

    secondary            = DarkOnSurfaceVariant,
    onSecondary          = Color(0xFF00301A),
    secondaryContainer   = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,

    error                = Color(0xFFF87171),
    onError              = Color(0xFF450A0A),
    errorContainer       = Color(0xFF4C1513),
    onErrorContainer     = Color(0xFFFECACA),

    background           = DarkBackground,
    onBackground         = DarkOnBackground,
    surface              = DarkSurface,
    onSurface            = DarkOnSurface,
    surfaceVariant       = DarkSurfaceVariant,
    onSurfaceVariant     = DarkOnSurfaceVariant,
    outline              = DarkOutline,
    outlineVariant       = Color(0xFF232E28),
)

// ── Data palette plumbing ─────────────────────────────────────────────────────

val LocalDataPalette = staticCompositionLocalOf { LightDataPalette }

/** Reading and chart colours for the active theme. */
val MaterialTheme.dataPalette: DataPalette
    @Composable @ReadOnlyComposable get() = LocalDataPalette.current

private val LocalIsDarkTheme = staticCompositionLocalOf { false }

/** True when the app is rendering its dark scheme, however that was chosen. */
val MaterialTheme.isDark: Boolean
    @Composable @ReadOnlyComposable get() = LocalIsDarkTheme.current

// ── App theme ─────────────────────────────────────────────────────────────────
@Composable
fun CemoTheme(
    content: @Composable () -> Unit
) {
    val themeMode by AppState.themeMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()

    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemDark
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val dataPalette = if (darkTheme) DarkDataPalette else LightDataPalette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            WindowCompat.setDecorFitsSystemWindows(window, false)

            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalDataPalette provides dataPalette,
        LocalIsDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = CemoTypography,
            shapes      = CemoShapes
        ) {
            Surface(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                color = colorScheme.background
            ) {
                content()
            }
        }
    }
}
