package com.example.cemo.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand (fixed, same in both themes) ───────────────────────────────────────
val PrimaryGreen  = Color(0xFF00B050) // Will pop beautifully against the new dark backgrounds
val MetricRed     = Color(0xFFFF7043)
val MetricBlue    = Color(0xFF42A5F5)
val ImpactGold    = Color(0xFFFFD54F)
val AllocationRed = Color(0xFFE53935)

// ── Light palette ─────────────────────────────────────────────────────────────
val LightBackground       = Color(0xFFD6E8DF)
val LightSurface          = Color(0xFFF4FAF7)
val LightSurfaceVariant   = Color(0xFFDDEDE6)
val LightOnBackground     = Color(0xFF006400)
val LightOnSurface        = Color(0xFF1C1C1E)
val LightOnSurfaceVariant = Color(0xFF4A6358)
val LightOutline          = Color(0xFFB0C4BB)

// ── Improved Dark palette ─────────────────────────────────────────────────────
val DarkBackground        = Color(0xFF0E1411)   // Deeper, more neutral near-black.
val DarkSurface           = Color(0xFF151E19)   // Subtle elevation, less muddy.
val DarkSurfaceVariant    = Color(0xFF1E2B24)   // Richer depth for cards/sheets without being too bright.
val DarkOnBackground      = Color(0xFFE4ECE6)   // Crisp off-white. Replaces the harsh green for readable text.
val DarkOnSurface         = Color(0xFFE4ECE6)   // Matches background text for consistent readability.
val DarkOnSurfaceVariant  = Color(0xFFA1B3A8)   // Muted grey-green for secondary text/icons.
val DarkOutline           = Color(0xFF3F5448)   // Clean, visible borders that don't distract.