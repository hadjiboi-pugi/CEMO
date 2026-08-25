package com.example.cemo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Five radii, and only five. Corner radius is a size signal: the larger the
 * element, the larger its corner. Reach for the role, not a number — a screen
 * that mixes eleven hand-picked radii reads as unresolved even when nothing is
 * individually wrong.
 */
val CemoShapes = Shapes(
    /** Badges, chips, tags. */
    extraSmall = RoundedCornerShape(8.dp),
    /** Buttons, inputs, small icon tiles. */
    small = RoundedCornerShape(12.dp),
    /** The default card. */
    medium = RoundedCornerShape(16.dp),
    /** Hero cards and bottom sheets. */
    large = RoundedCornerShape(20.dp),
    /** Dialogs. */
    extraLarge = RoundedCornerShape(28.dp)
)
