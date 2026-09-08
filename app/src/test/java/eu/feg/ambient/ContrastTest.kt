package eu.feg.ambient

import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.identity.contrastRatio
import eu.feg.ambient.ui.theme.PskColors
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 AA, as arithmetic.
 *
 * Contrast is not a matter of taste and it should never be checked by eye: every pair the
 * app actually draws is listed here with the ratio it needs, the table is printed so it can
 * go on a slide, and the build fails if a token drifts. This is the one file that makes the
 * "WCAG 2.1 AA ground rule" a fact about the code rather than a sentence in a deck.
 *
 * The luminance formula linearises each sRGB channel before weighting — the 0.03928/12.92
 * branch — which is the step that, when skipped, makes everything look like it fails.
 */
class ContrastTest {

    private val psk = PskColors()

    /** 0xAARRGGBB, as written in Color.kt. */
    private fun argb(c: androidx.compose.ui.graphics.Color): Long = c.value.toLong() ushr 32

    private fun relativeLuminance(argb: Long): Double {
        fun channel(v: Long): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun contrastRatio(fg: Long, bg: Long): Double {
        val lf = relativeLuminance(fg)
        val lb = relativeLuminance(bg)
        return (maxOf(lf, lb) + 0.05) / (minOf(lf, lb) + 0.05)
    }

    private data class Pair(val fg: String, val bg: String, val fgC: Long, val bgC: Long, val min: Double, val use: String)

    private fun table(): List<Pair> {
        val surfaces = listOf(
            "background" to psk.background, "surface" to psk.surface, "surfaceVariant" to psk.surfaceVariant,
            "surfaceRaised" to psk.surfaceRaised, "betslipPanel" to psk.betslipPanel,
        )
        val rows = mutableListOf<Pair>()
        for ((n, bg) in surfaces) {
            rows += Pair("textPrimary", n, argb(psk.textPrimary), argb(bg), 4.5, "body")
            rows += Pair("textSecondary", n, argb(psk.textSecondary), argb(bg), 4.5, "body")
        }
        // The brief names accent / calm / stop. In this palette those are brandBlue, positive
        // and negative: the accent, the won/calm green, and the lost/stop red.
        for ((n, bg) in surfaces.take(2)) {
            rows += Pair("brandBlue (accent)", n, argb(psk.brandBlue), argb(bg), 3.0, "large/non-text")
            rows += Pair("positive (calm)", n, argb(psk.positive), argb(bg), 4.5, "body")
            rows += Pair("negative (stop)", n, argb(psk.negative), argb(bg), 4.5, "body")
        }
        rows += Pair("jackpotYellow", "surface", argb(psk.jackpotYellow), argb(psk.surface), 4.5, "body")
        rows += Pair("textPrimary", "brandBlue (buttons)", argb(psk.textPrimary), argb(psk.brandBlue), 4.5, "body")
        return rows
    }

    @Test
    fun `every foreground and background pair the app draws clears WCAG AA`() {
        val failures = mutableListOf<String>()
        println()
        println(String.format("%-22s %-22s %6s %5s  %s", "foreground", "background", "ratio", "min", "use"))
        for (p in table()) {
            val r = contrastRatio(p.fgC, p.bgC)
            val ok = r >= p.min
            println(String.format("%-22s %-22s %6.2f %5.1f  %-14s %s", p.fg, p.bg, r, p.min, p.use, if (ok) "PASS" else "FAIL"))
            if (!ok) failures += p.fg + " on " + p.bg + " = " + String.format("%.2f", r) + " (needs " + p.min + ")"
        }
        // N6: the initials on every club badge.
        for (club in ClubThemes.all + ClubThemes.Default) {
            val r = contrastRatio(club.onPrimary, club.primary)
            val rOnDark = contrastRatio(club.accentOnDark, psk.surface)
            println(String.format("%-22s %-22s %6.2f %5.1f  %-14s %s", "onPrimary", club.name + " badge", r, 4.5, "badge initials", if (r >= 4.5) "PASS" else "FAIL"))
            println(String.format("%-22s %-22s %6.2f %5.1f  %-14s %s", "accentOnDark", club.name + " on surface", rOnDark, 4.5, "club name", if (rOnDark >= 4.5) "PASS" else "FAIL"))
            if (r < 4.5) failures += club.name + " badge initials = " + String.format("%.2f", r)
            if (rOnDark < 4.5) failures += club.name + " accentOnDark on surface = " + String.format("%.2f", rOnDark)
        }
        println()
        assertTrue("contrast failures:\n  " + failures.joinToString("\n  "), failures.isEmpty())
    }
}
