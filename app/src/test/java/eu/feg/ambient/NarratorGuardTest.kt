package eu.feg.ambient

import eu.feg.ambient.ambient.narrator.NarratedText
import eu.feg.ambient.ambient.narrator.NarratorEngine
import eu.feg.ambient.ambient.narrator.NarratorGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NarratorGuardTest {

    private fun text(headline: String, detail: String, spoken: String = CLEAN_SPOKEN) =
        NarratedText(headline, detail, spoken, NarratorEngine.TEMPLATE, 0)

    /** A spoken line that is not what any of these cases is testing. */
    private val CLEAN_SPOKEN =
        "Liverpool are one nil up after sixty-one minutes, with twenty-nine minutes to go."

    // --- things that must pass -------------------------------------------------------

    private val valid = listOf(
        text("Liverpool 1–0 · 61'", "Your Liverpool win pick is still alive. 29 minutes left."),
        text("That'll do · 1–0", "Two down, one to go. Sparta just needs to hold on."),
        text("1–0 · 61' · 2/3 legs", "Liverpool win needed. 29 minutes of normal time remain."),
        text("1–0 · 2/3 ✓", "29 min left."),
        text("While you were away", "2 legs won, Plzeň drew, Sparta kick off in 40 min."),
        text("Poluvrijeme · 2–1", "Varaždin 2–1 Istria 1961. Prvo poluvrijeme je gotovo."),
        text("Half time · 0–0", "Betis 0–0 Real Madrid. First half done."),
        text("3 minutes left · 2–2", "Genoa 2–2 Como. Your Genoa pick still needs to hold."),
        text("HT 1–1 · 1/3 legs", "Stuttgart 1–1 Cologne. 45 minutes to play."),
        text("Sparta · 40 min", "Sparta v Plzeň."),
    )

    @Test
    fun `ten realistic lines all pass`() {
        valid.forEach { line ->
            assertTrue(
                "should have passed: " + line.headline + " / " + NarratorGuard.violation(line),
                NarratorGuard.check(line),
            )
        }
    }

    @Test
    fun `team names containing a blocked word as a substring still pass`() {
        // "Betis" contains "bet"; "Ulm" does not contain "ulog" but guards the same idea.
        assertTrue(NarratorGuard.check(text("Betis 1–0 · 61'", "Betis lead at the break.")))
    }

    @Test
    fun `scores minutes and periods are not mistaken for amounts`() {
        assertTrue(NarratorGuard.check(text("1–0 · 61'", "1. poluvrijeme done. 29 minutes left.")))
        assertTrue(NarratorGuard.check(text("HT 10–0", "45 to play.")))
    }

    // --- one per blocked category ----------------------------------------------------

    @Test
    fun `blank headline fails`() {
        assertFalse(NarratorGuard.check(text("   ", "Something happened.")))
    }

    @Test
    fun `blank detail fails`() {
        assertFalse(NarratorGuard.check(text("Half time", "  ")))
    }

    @Test
    fun `over-long headline fails`() {
        assertFalse(NarratorGuard.check(text("x".repeat(61), "Fine.")))
    }

    @Test
    fun `over-long detail fails`() {
        assertFalse(NarratorGuard.check(text("Fine", "y".repeat(121))))
    }

    @Test
    fun `english money words fail`() {
        listOf(
            "Your bet is alive", "Great odds tonight", "Raise your stake",
            "A tidy wager", "Claim your bonus", "Time to cash out",
            "Nice payout coming", "Make a deposit", "The jackpot is up",
            "A guaranteed result", "Grab a free bet", "You could win money",
        ).forEach { phrase ->
            val line = text("Half time · 1–0", phrase + " now.")
            assertFalse("should have been blocked: " + phrase, NarratorGuard.check(line))
        }
    }

    @Test
    fun `croatian money words fail`() {
        listOf("kvota", "kvote", "oklada", "okladi", "ulog", "uloži", "isplata", "dobitak")
            .forEach { word ->
                val line = text("Poluvrijeme · 1–0", "Ovo je " + word + " za tebe.")
                assertFalse("should have been blocked: " + word, NarratorGuard.check(line))
            }
    }

    @Test
    fun `currency symbols fail`() {
        listOf("€10 to come", "worth $20", "£5 more", "EUR 12 total").forEach { phrase ->
            assertFalse("should have been blocked: " + phrase, NarratorGuard.check(text("HT", phrase)))
        }
    }

    @Test
    fun `two decimal numbers fail`() {
        assertFalse(NarratorGuard.check(text("Half time", "Priced at 2.50 right now.")))
        assertFalse(NarratorGuard.check(text("Half time", "Iznosi 2,50 ukupno.")))
    }

    @Test
    fun `violation names the rule that tripped`() {
        assertNotNull(NarratorGuard.violation(text("Half time", "Your bet is alive.")))
        assertTrue(
            NarratorGuard.violation(text("Half time", "Your bet is alive."))!!.contains("bet"),
        )
    }
}
