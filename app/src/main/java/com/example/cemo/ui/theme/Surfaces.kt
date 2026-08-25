package com.example.cemo.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/*
 * Surface hierarchy.
 *
 * The app previously painted every container with the same simulated-glass
 * treatment — gradients, frost overlays and reflections on cards, inputs, rows
 * and dialogs alike. Depth is relative: if everything floats, nothing does, and
 * the effect degrades into visual noise that competes with the readings.
 *
 * So there are three levels, and a screen should rarely need more than two:
 *
 *   Sunken  — recessed. Progress tracks, inset panels, wells.
 *   Card    — the default. A flat surface separated by a hairline, not a shadow.
 *   Raised  — reserved for the one element that must lead the screen.
 */
enum class SurfaceLevel { Sunken, Card, Raised }

/**
 * Applies a surface [level], defaulting to the theme's card shape.
 *
 * Separation comes from a hairline border in light theme (where shadows on a
 * near-white page read as smudges) and from a lighter fill in dark theme (where
 * borders read as seams) — the same idea expressed in the medium each theme
 * actually has available.
 */
@Composable
fun Modifier.cemoSurface(
    level: SurfaceLevel = SurfaceLevel.Card,
    shape: Shape = MaterialTheme.shapes.medium
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = MaterialTheme.isDark

    val fill = when (level) {
        SurfaceLevel.Sunken -> scheme.surfaceVariant
        SurfaceLevel.Card   -> scheme.surface
        SurfaceLevel.Raised -> if (dark) scheme.surfaceVariant else scheme.surface
    }

    // Only a raised card in light theme earns a shadow; everywhere else
    // separation is carried by fill and hairline.
    val raisedInLight = level == SurfaceLevel.Raised && !dark
    val outlined = level == SurfaceLevel.Card || (level == SurfaceLevel.Raised && dark)

    return this
        .then(if (raisedInLight) Modifier.shadow(3.dp, shape, clip = false) else Modifier)
        .clip(shape)
        .background(fill)
        .then(if (outlined) Modifier.border(Size.hairline, scheme.outline, shape) else Modifier)
}
