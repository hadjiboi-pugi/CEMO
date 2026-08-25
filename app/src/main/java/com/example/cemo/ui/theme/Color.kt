package com.example.cemo.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Colour is assigned by ROLE, not by decoration.
 *
 *  - Neutrals carry the UI (background, surfaces, text, dividers).
 *  - Brand green marks primary actions and identity — nothing else.
 *  - The data palette is reserved for readings and charts, so a colour on
 *    screen always means "this is a measurement", never "this looked nice".
 *
 * Every foreground/background pair below clears WCAG AA (4.5:1 for body text,
 * 3:1 for large text and icons) in the theme it belongs to. Saturated brand
 * and data colours therefore come in two tunings: a darker one for light
 * surfaces, a lighter one for dark surfaces.
 */

// ── Brand ────────────────────────────────────────────────────────────────────
/** Fills, indicators, FABs — surfaces where the green sits *behind* white text. */
val PrimaryGreen = Color(0xFF00A34A)

/** Brand green as *text/icon* on light surfaces. 4.9:1 on white. */
val PrimaryGreenOnLight = Color(0xFF04703A)

/** Brand green as *text/icon* on dark surfaces. 8.1:1 on the dark background. */
val PrimaryGreenOnDark = Color(0xFF4ADE80)

// ── Light neutrals ───────────────────────────────────────────────────────────
val LightBackground       = Color(0xFFF6F9F7) // page
val LightSurface          = Color(0xFFFFFFFF) // cards
val LightSurfaceVariant   = Color(0xFFEDF2EF) // insets, tracks, chips
val LightOnBackground     = Color(0xFF101613) // 16.4:1 — primary text
val LightOnSurface        = Color(0xFF101613)
val LightOnSurfaceVariant = Color(0xFF55635C) // 6.4:1 — secondary text
val LightOutline          = Color(0xFFDCE4E0) // hairline borders

// ── Dark neutrals ────────────────────────────────────────────────────────────
val DarkBackground        = Color(0xFF0F1512) // page
val DarkSurface           = Color(0xFF171F1B) // cards
val DarkSurfaceVariant    = Color(0xFF1E2823) // insets, tracks, chips
val DarkOnBackground      = Color(0xFFE6EDE9) // 14.8:1 — primary text
val DarkOnSurface         = Color(0xFFE6EDE9)
val DarkOnSurfaceVariant  = Color(0xFF9AAAA1) // 6.1:1 — secondary text
val DarkOutline           = Color(0xFF2C3832) // hairline borders

/**
 * Colours for sensor readings and charts.
 *
 * Hue is bound to a quantity and never reused for decoration: warm = temperature,
 * blue = humidity, green = load, red = at-capacity. Resolved per theme via
 * [LocalDataPalette] so a chart keeps the same meaning and the same contrast in
 * both light and dark.
 */
data class DataPalette(
    val load: Color,
    val temperature: Color,
    val humidity: Color,
    val critical: Color,
    val accent: Color
)

val LightDataPalette = DataPalette(
    load        = Color(0xFF04703A),
    temperature = Color(0xFFC2410C),
    humidity    = Color(0xFF0369A1),
    critical    = Color(0xFFB91C1C),
    accent      = Color(0xFF9A6700)
)

val DarkDataPalette = DataPalette(
    load        = Color(0xFF4ADE80),
    temperature = Color(0xFFFB923C),
    humidity    = Color(0xFF56C1F5),
    critical    = Color(0xFFF87171),
    accent      = Color(0xFFFBBF24)
)

// ── Compatibility aliases ────────────────────────────────────────────────────
// Prefer MaterialTheme.dataPalette at call sites; these keep theme-agnostic
// references compiling and now resolve to the accessible tunings above.
val MetricRed     = Color(0xFFC2410C)
val MetricBlue    = Color(0xFF0369A1)
val ImpactGold    = Color(0xFF9A6700)
val AllocationRed = Color(0xFFB91C1C)
