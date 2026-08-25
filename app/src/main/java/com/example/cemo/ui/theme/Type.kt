package com.example.cemo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * One type scale, used everywhere. Screens pick a role from this list — they do
 * not build TextStyles inline.
 *
 * Weight is deliberately restrained: Normal for prose, Medium for labels and
 * titles, SemiBold for headlines and the big readouts. Nothing is heavier.
 * When every element is Black, weight stops encoding hierarchy and the screen
 * reads as uniformly loud; hierarchy here comes from size and colour instead.
 *
 * Letter spacing follows the Material 3 metrics and is set once, here. The only
 * tracked-out style is [labelMedium], which section headers use in uppercase.
 */

private val Sans = FontFamily.Default

val CemoTypography = Typography(

    // ── Display — the one hero number per screen (e.g. current load) ─────────
    displayLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.25).sp
    ),
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp, lineHeight = 38.sp
    ),

    // ── Headline — screen titles ────────────────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp
    ),

    // ── Title — card and group headers ──────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),

    // ── Body — prose and list content ───────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),

    // ── Label — buttons, captions, metadata ─────────────────────────────────
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    /** Uppercase section headers. The one style carrying wide tracking. */
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    )
)

/**
 * Tabular-ish styling for live numbers: readings update every second, so the
 * hero figure gets its own role to keep it from being restyled per screen.
 */
val MetricValueStyle: TextStyle = CemoTypography.displayMedium
val MetricUnitStyle: TextStyle = CemoTypography.titleMedium
