package com.example.cemo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Using system default (sans-serif) as a clean baseline.
// To use a custom font (e.g. Google Fonts), add the dependency:
//   implementation("androidx.compose.ui:ui-text-google-fonts:<version>")
// then swap FontFamily.Default for your chosen font family.

val CemoTypography = Typography(
    // Large display — e.g. "CEMO Monitor" on login
    displayMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Bold,
        fontSize    = 28.sp,
        lineHeight  = 36.sp,
        letterSpacing = (-0.5).sp
    ),

    // Screen titles in TopAppBar / drawer
    headlineMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Bold,
        fontSize    = 20.sp,
        lineHeight  = 28.sp
    ),

    // Card titles, section headers
    titleMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 16.sp,
        lineHeight  = 24.sp
    ),

    // Body text — history entries, descriptions
    bodyMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Normal,
        fontSize    = 14.sp,
        lineHeight  = 20.sp
    ),

    // Labels — metric sub-labels, timestamps
    labelSmall = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Normal,
        fontSize    = 10.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.5.sp
    ),

    // Button text
    labelLarge = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Bold,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        letterSpacing = 1.sp
    )
)