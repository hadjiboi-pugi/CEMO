package com.example.cemo.ui.theme

import androidx.compose.ui.unit.dp

/*
 * A 4dp grid. Every gap, pad and inset in the app comes from this object.
 *
 * The point is rhythm: when spacing is picked per call site you get 6dp next to
 * 10dp next to 14dp, and the eye reads the drift as sloppiness long before it
 * can name it. Related things share a step; unrelated things differ by at least
 * two.
 */
object Space {
    /** 4dp — icon-to-label, tight pairs. */
    val xs = 4.dp
    /** 8dp — inside a row of related items. */
    val sm = 8.dp
    /** 12dp — between stacked fields. */
    val md = 12.dp
    /** 16dp — screen gutter, gap between sibling cards. */
    val lg = 16.dp
    /** 20dp — padding inside a card. */
    val card = 20.dp
    /** 24dp — between content groups. */
    val xl = 24.dp
    /** 32dp — between major sections. */
    val xxl = 32.dp
}

/**
 * Fixed sizes that must agree across screens.
 */
object Size {
    /** Minimum touch target — Material accessibility floor. */
    val touchTarget = 48.dp
    /** Inline icon beside body text. */
    val iconSmall = 16.dp
    /** Standard icon in a list row or button. */
    val icon = 20.dp
    /** Leading icon tile in a list row. */
    val iconTile = 40.dp
    /** Hairline border. */
    val hairline = 1.dp
    /** Height of a chart plotted in a card. */
    val chart = 140.dp
}
