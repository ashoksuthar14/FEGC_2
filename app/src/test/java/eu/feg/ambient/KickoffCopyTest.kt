package eu.feg.ambient

import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.NarratedText
import eu.feg.ambient.ambient.narrator.NarratorEngine
import eu.feg.ambient.ambient.narrator.NarratorGuard
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.TemplateNarrator
import eu.feg.ambient.ambient.narrator.Tone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The kick-off moment is a fact about a followed club, not a reason to open the app.
 *
 * "You're missing your team's game" is an inducement: it needs marketing consent, it is
 * restricted in some markets, and it must never reach an at-risk customer. The guard
 * rejects that framing whichever narrator produced it, and every template for the moment
 * has to clear the guard it would be judged by.
 */
class KickoffCopyTest {

    private fun text(
        detail: String,
        spoken: String = "Hajduk kick off against Rijeka in forty minutes.",
    ) = NarratedText(
        headline = "Hajduk kick off in 40 minutes",
        detail = detail,
        spokenText = spoken,
        engine = NarratorEngine.TEMPLATE,
        latencyMs = 0,
    )

    /** Inducement words, checked on top of the guard so a guard regression is named here. */
    private val forbidden = listOf(
        "missing", "don't miss", "don’t miss", "miss out", "last chance", "bet now",
        "back them", "ne propusti", "propuštaš", "prilika", "kladi se",
    )

    @Test
    fun `don't miss out is rejected`() {
        assertNotNull(NarratorGuard.violation(text("Don't miss out on Hajduk – Rijeka.")))
    }

    @Test
    fun `you're missing your team's game is rejected`() {
        assertNotNull(NarratorGuard.violation(text("You're missing your team's game.")))
        assertNotNull(NarratorGuard.violation(text("Hajduk – Rijeka.", "Ne propusti Hajduk protiv Rijeke.")))
    }

    @Test
    fun `the missing leg is still ordinary english`() {
        // The bare word is not the offence; telling the customer they are missing something is.
        assertNull(NarratorGuard.violation(text("The missing leg is Hajduk to win.")))
    }

    @Test
    fun `every kickoff template clears the guard`() = runTest {
        val narrator = TemplateNarrator()
        val failures = mutableListOf<String>()

        // Both venues: the followed club at home and away.
        listOf("Hajduk" to "Rijeka", "Rijeka" to "Hajduk").forEach { (home, away) ->
            val facts = MomentFacts(
                type = MomentType.KICKOFF_FOLLOWED,
                homeTeam = home,
                awayTeam = away,
                followedTeam = "Hajduk",
                kickoffInMinutes = 40,
            )
            Tone.entries.forEach { tone ->
                NarratorLanguage.entries.forEach { language ->
                    val result = narrator.narrate(facts, tone, language)
                    val body = (result.headline + " " + result.detail + " " + result.spokenText).lowercase()
                    val label = tone.name + "/" + language.name + " | " + body
                    NarratorGuard.violation(result)?.let { failures += label + " -> " + it }
                    forbidden.filter { body.contains(it) }.forEach { failures += label + " -> says " + it }
                }
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
