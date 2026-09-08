package eu.feg.ambient

import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.NarratorEngine
import eu.feg.ambient.ambient.narrator.NarratorGuard
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.TemplateNarrator
import eu.feg.ambient.ambient.narrator.Tone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every MomentType x Tone x Language must clear the guard. That is 8 x 4 x 2 = 64. */
class TemplateNarratorTest {

    private val narrator = TemplateNarrator()

    /** Realistic facts per moment, using clubs already in the mock data. */
    private fun sample(type: MomentType) = MomentFacts(
        type = type,
        homeTeam = "Liverpool",
        awayTeam = "Ipswich",
        homeScore = 1,
        awayScore = 0,
        minute = 61,
        period = "2. poluvrijeme",
        scorer = "Salah",
        legsTotal = 3,
        legsWon = 2,
        legsLost = 0,
        myLegDescription = "Liverpool win",
        minutesRemaining = 29,
        followedTeam = "Sparta",
        kickoffInMinutes = 40,
        digestItems = listOf("2 legs won", "Betis drew", "Sparta kick off in 40 min"),
        habitHints = listOf("Followed for six weeks."),
    )

    @Test
    fun `all 64 combinations pass the guard`() = runTest {
        val failures = mutableListOf<String>()
        var count = 0

        MomentType.entries.forEach { type ->
            Tone.entries.forEach { tone ->
                NarratorLanguage.entries.forEach { language ->
                    count++
                    val result = narrator.narrate(sample(type), tone, language)
                    NarratorGuard.violation(result)?.let { reason ->
                        failures += type.name + "/" + tone.name + "/" + language.name +
                            " -> " + reason + " | " + result.headline + " | " + result.detail
                    }
                }
            }
        }

        // Twelve moment types now: N7 added three, and casino added SESSION_LENGTH -- the
        // same engine on the other vertical. The count is asserted rather than derived so
        // adding a type without writing its copy fails here loudly, which is this test's job.
        assertEquals("expected 96 combinations", 96, count)
        assertTrue(
            "guard rejected " + failures.size + " of 96:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `all 64 survive facts that are almost entirely null`() = runTest {
        val failures = mutableListOf<String>()
        MomentType.entries.forEach { type ->
            Tone.entries.forEach { tone ->
                NarratorLanguage.entries.forEach { language ->
                    val result = narrator.narrate(MomentFacts(type = type), tone, language)
                    NarratorGuard.violation(result)?.let { reason ->
                        failures += type.name + "/" + tone.name + "/" + language.name + " -> " + reason
                    }
                }
            }
        }
        assertTrue(
            "empty facts broke " + failures.size + " combinations:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `a very long team name cannot push the headline over the limit`() = runTest {
        val long = "Borussia Moenchengladbach Reserves And Academy Eleven"
        val result = narrator.narrate(
            sample(MomentType.GOAL_ON_SLIP).copy(homeTeam = long, myLegDescription = long),
            Tone.PLAIN,
            NarratorLanguage.EN,
        )
        assertTrue(result.headline.length <= NarratorGuard.MAX_HEADLINE)
        assertTrue(result.detail.length <= NarratorGuard.MAX_DETAIL)
        assertTrue(NarratorGuard.check(result))
    }

    @Test
    fun `the template narrator reports itself honestly`() = runTest {
        val result = narrator.narrate(sample(MomentType.HALFTIME), Tone.PLAIN, NarratorLanguage.EN)
        assertEquals(NarratorEngine.TEMPLATE, result.engine)
    }

    @Test
    fun `croatian keeps the app's period vocabulary`() = runTest {
        val result = narrator.narrate(sample(MomentType.HALFTIME), Tone.PLAIN, NarratorLanguage.HR)
        assertTrue(
            "expected Croatian half-time wording, got: " + result.headline + " / " + result.detail,
            (result.headline + result.detail).contains("oluvrijeme"),
        )
    }
}
