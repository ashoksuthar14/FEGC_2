package eu.feg.ambient

import eu.feg.ambient.ambient.narrator.LadderNarrator
import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.NarratedText
import eu.feg.ambient.ambient.narrator.Narrator
import eu.feg.ambient.ambient.narrator.NarratorEngine
import eu.feg.ambient.ambient.narrator.NarratorGuard
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.TemplateNarrator
import eu.feg.ambient.ambient.narrator.Tone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ladder is what makes Path C safe: on a device with no Gemini Nano, every one of these
 * failure modes has to end in usable text rather than an exception or an empty lock screen.
 */
class LadderNarratorTest {

    private val facts = MomentFacts(
        type = MomentType.GOAL_ON_SLIP,
        homeTeam = "Liverpool",
        awayTeam = "Ipswich",
        homeScore = 1,
        awayScore = 0,
        minute = 61,
        legsTotal = 3,
        legsWon = 2,
        myLegDescription = "Liverpool win",
        minutesRemaining = 29,
    )

    private val template = TemplateNarrator()

    /** Narrator is a plain interface, not a fun interface, so this stays an object. */
    private fun rung(block: suspend () -> NarratedText) = object : Narrator {
        override suspend fun narrate(
            facts: MomentFacts,
            tone: Tone,
            language: NarratorLanguage,
        ): NarratedText = block()
    }

    @Test
    fun `a rung that throws falls through to the template`() = runTest {
        val exploding = rung { error("AICore is not having a good day") }
        val result = LadderNarrator(listOf(exploding, template))
            .narrate(facts, Tone.PLAIN, NarratorLanguage.EN)

        assertEquals(NarratorEngine.TEMPLATE, result.engine)
        assertTrue(NarratorGuard.check(result))
    }

    @Test
    fun `a rung whose output trips the guard falls through to the template`() = runTest {
        val leaky = rung {
            NarratedText(
                headline = "Cash out now",
                detail = "Your bet is worth €42.50 — guaranteed payout.",
                spokenText = "Cash out now for forty-two euros fifty.",
                engine = NarratorEngine.NANO,
                latencyMs = 12,
            )
        }
        val result = LadderNarrator(listOf(leaky, template))
            .narrate(facts, Tone.PLAIN, NarratorLanguage.EN)

        assertEquals("a leaked price must never reach the surface", NarratorEngine.TEMPLATE, result.engine)
        assertTrue(NarratorGuard.check(result))
    }

    @Test
    fun `an over-long rung output falls through to the template`() = runTest {
        val verbose = rung {
            NarratedText("x".repeat(200), "y".repeat(400), "z".repeat(500), NarratorEngine.NANO, 30)
        }
        val result = LadderNarrator(listOf(verbose, template))
            .narrate(facts, Tone.PLAIN, NarratorLanguage.EN)

        assertEquals(NarratorEngine.TEMPLATE, result.engine)
        assertTrue(result.headline.length <= NarratorGuard.MAX_HEADLINE)
    }

    @Test
    fun `a clean rung is used and reports its own engine`() = runTest {
        val good = rung {
            NarratedText(
                "Liverpool 1–0 · 61'",
                "29 minutes left.",
                "Liverpool are one nil up, with twenty-nine minutes to go.",
                NarratorEngine.NANO,
                480,
            )
        }
        val result = LadderNarrator(listOf(good, template))
            .narrate(facts, Tone.PLAIN, NarratorLanguage.EN)

        assertEquals("the badge must tell the truth", NarratorEngine.NANO, result.engine)
        assertEquals("Liverpool 1–0 · 61'", result.headline)
    }

    @Test
    fun `the template-only ladder still produces text`() = runTest {
        val result = LadderNarrator(listOf(template)).narrate(facts, Tone.WITTY, NarratorLanguage.HR)
        assertEquals(NarratorEngine.TEMPLATE, result.engine)
        assertTrue(NarratorGuard.check(result))
    }

    @Test
    fun `every moment and tone survives a hostile first rung`() = runTest {
        val hostile = rung { error("boom") }
        val ladder = LadderNarrator(listOf(hostile, template))
        MomentType.entries.forEach { type ->
            Tone.entries.forEach { tone ->
                NarratorLanguage.entries.forEach { language ->
                    val result = ladder.narrate(MomentFacts(type = type), tone, language)
                    assertTrue(
                        type.name + "/" + tone.name + "/" + language.name + " -> " +
                            NarratorGuard.violation(result),
                        NarratorGuard.check(result),
                    )
                }
            }
        }
    }
}
