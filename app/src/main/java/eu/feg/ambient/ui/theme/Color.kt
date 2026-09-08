package eu.feg.ambient.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The complete PSK palette, sampled from the screenshots (PRD section 3.1).
 * These are the only colours in the app. Nothing outside this file may declare a Color.
 */
@Immutable
data class PskColors(
    /**
     * Top app bar, primary buttons, active tab indicator.
     *
     * Sampled as #1852BE; nudged to the nearest lightness that clears WCAG 3:1 as a
     * non-text accent on the app background (it was 2.75:1). The minimum nudge that passes,
     * found by search rather than by eye, so the palette stays PSK's -- see ContrastTest.
     */
    val brandBlue: Color = Color(0xFF2A60C3),
    /** Secondary nav strip, pressed state. */
    val brandBlueDark: Color = Color(0xFF1647A6),
    /** Promo hero backgrounds. */
    val brandBlueDeep: Color = Color(0xFF011576),
    /** App background — the dominant colour of the whole product. */
    val background: Color = Color(0xFF0E0E10),
    /** Cards, league section bodies. */
    val surface: Color = Color(0xFF17171C),
    /** Section headers, chips. */
    val surfaceVariant: Color = Color(0xFF22222A),
    /** Match rows, odds buttons, bet slip rows. */
    val surfaceRaised: Color = Color(0xFF3B3B43),
    /** Odds button fill. */
    val oddsCell: Color = Color(0xFF36363F),
    /** Bet slip container. */
    val betslipPanel: Color = Color(0xFF2E2E38),
    /** Casino jackpot bars, TOP markers. */
    val jackpotYellow: Color = Color(0xFFF8C102),
    /** Jackpot bar gradient end. */
    val jackpotYellowDim: Color = Color(0xFFDDB905),
    val textPrimary: Color = Color(0xFFFFFFFF),
    /** Meta text, kickoff times, counts. */
    val textSecondary: Color = Color(0xFFA9A9B4),
    /** Odds up, won. */
    val positive: Color = Color(0xFF3BC66B),
    /** Odds down, lost. */
    val negative: Color = Color(0xFFE5484D),
)
