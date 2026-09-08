package eu.feg.ambient

import eu.feg.ambient.ambient.narrator.NarratedText
import eu.feg.ambient.ambient.narrator.NarratorEngine
import eu.feg.ambient.ambient.narrator.NarratorGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The spoken line is held to the same rule as the two visible ones.
 *
 * It matters more here, not less. A price on a lock screen is seen by whoever is holding the
 * phone; a price read aloud is heard by everyone in the room, and the customer had no chance
 * to decide who that was. So a spoken variant that names an amount must be rejected exactly
 * as a headline naming one would be — even when the two visible lines are impeccable.
 */
class SpokenTextGuardTest {

    private fun withSpoken(spoken: String) = NarratedText(
        headline = "Liverpool 1–0 · 61'",
        detail = "Your Liverpool pick is still alive. 29 minutes left.",
        spokenText = spoken,
        engine = NarratorEngine.TEMPLATE,
        latencyMs = 0,
    )

    private val leaks = listOf(
        "Liverpool are one nil up. Your bet is worth forty-two euros fifty.",
        "Two of your three legs have won, at odds of 3.50.",
        "Liverpool are one nil up. Cash out now for €42.50.",
        "Your stake is still running with twenty-nine minutes to go.",
        "Prošla su dva izbora, a kvota je 3,50.",
        "Liverpool vode jedan nula. Isplata je moguća odmah.",
    )

    @Test
    fun `a spoken line naming odds or money is rejected`() {
        leaks.forEach { spoken ->
            val text = withSpoken(spoken)
            assertFalse(
                "this must never be read out loud: " + spoken,
                NarratorGuard.check(text),
            )
            assertNotNull(
                "and the guard must name the rule that caught it",
                NarratorGuard.violation(text),
            )
        }
    }

    @Test
    fun `a clean spoken line passes`() {
        val text = withSpoken(
            "Liverpool are one nil up, sixty-one minutes played. Two of your three legs " +
                "have won, with twenty-nine minutes to go.",
        )
        assertTrue(NarratorGuard.violation(text) ?: "clean", NarratorGuard.check(text))
    }
}
