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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * N7's half of the guard: urgency is rejected, and the mission copy is not.
 *
 * The second half is the one that matters. A blocklist is easy to make strict and hard to
 * make right, and the failure mode that ships is not "hurry" getting through — it is the
 * streak badge "Three days in a row" being refused because someone matched "days" or "last".
 * So this test runs the real MISSION_COMPLETE and TIER_REACHED lines, every tone and both
 * languages, on the real mission titles from mock/missions.json, and insists they pass.
 */
class UrgencyGuardTest {

    private val narrator = TemplateNarrator()

    private fun text(headline: String, detail: String, spoken: String = CLEAN_SPOKEN) =
        NarratedText(headline, detail, spoken, NarratorEngine.TEMPLATE, 0)

    private val CLEAN_SPOKEN = "Badge earned: follow three teams. That is five badges."

    // --- urgency is rejected ---------------------------------------------------------

    private val urgencyEn = listOf(
        "This badge expires soon", "Hurry, the mission is closing", "Act now to keep it",
        "Don't wait for the weekend", "Don’t wait for the weekend", "Do not wait for the weekend",
        "The mission ends today", "Only today for this one", "Get it while it lasts",
        "Last chance for this badge",
    )

    private val urgencyHr = listOf(
        "Požuri, misija se zatvara", "Požurite, misija se zatvara", "Značka istječe u ponoć",
        "Ponuda uskoro ističe", "Značka ističe uskoro", "Samo danas za ovu značku",
        "Ne čekaj vikend", "Ne čekajte vikend", "Zadnja prilika za značku",
    )

    @Test
    fun `english urgency phrases are rejected in the detail`() {
        urgencyEn.forEach { phrase ->
            val line = text("Badge earned", phrase + ".")
            assertFalse("should have been blocked: " + phrase, NarratorGuard.check(line))
        }
    }

    @Test
    fun `croatian urgency phrases are rejected in the detail`() {
        urgencyHr.forEach { phrase ->
            val line = text("Značka osvojena", phrase + ".")
            assertFalse("should have been blocked: " + phrase, NarratorGuard.check(line))
        }
    }

    @Test
    fun `urgency is rejected in the spoken line as well`() {
        (urgencyEn + urgencyHr).forEach { phrase ->
            val line = text("Badge earned", "Follow three teams, done.", spoken = phrase + ".")
            assertFalse("should have been blocked when spoken: " + phrase, NarratorGuard.check(line))
        }
    }

    // --- legitimate copy is not -----------------------------------------------------

    @Test
    fun `a streak is not urgency`() {
        assertPasses(text("Badge earned · Three days in a row", "Three days. That's 5 badges. 3 more for Gold."))
        assertPasses(text("Three days in a row ✓", "Three days in a row done."))
        // "Three days in a row" spoken, with the count in words as SpokenFacts writes it.
        assertPasses(text("Three days ✓", "Done.", spoken = "Three days earned. Three days in a row done."))
    }

    @Test
    fun `the digest headline still passes`() {
        // "propuštaš" is blocked; "Propustio si" is the digest and must not be.
        assertPasses(text("Propustio si ponešto", "2 izbora prošla, Betis remizirao."))
    }

    @Test
    fun `no hurry and nothing expires is the opposite of urgency and passes`() {
        // The KEEP_STREAK mission's own description in mock/missions.json.
        assertPasses(text("Three days in a row", "Check in on three separate days. No hurry, and nothing expires."))
    }

    @Test
    fun `the deposit limit mission can be narrated`() {
        // "deposit" alone is money and stays blocked; "deposit limit" is the protective tool
        // the SET_A_LIMIT mission is named after and must be sayable when its badge lands.
        assertPasses(text("Badge earned · Set a deposit limit", "Limit set. That's 3 badges."))
        assertFalse(NarratorGuard.check(text("Badge earned", "Make a deposit to keep going.")))
    }

    @Test
    fun `mission complete lines pass in every tone and both languages`() = runTest {
        missionFacts.forEach { facts -> assertAllTonesPass(facts) }
    }

    @Test
    fun `tier reached lines pass in every tone and both languages`() = runTest {
        tierFacts.forEach { facts -> assertAllTonesPass(facts) }
    }

    @Test
    fun `croatian badge counts agree with their noun`() = runTest {
        val one = narrator.narrate(missionFacts[0].copy(badgeCount = 1, badgesToNextTier = 2), Tone.PLAIN, NarratorLanguage.HR)
        assertTrue(one.spokenText, one.spokenText.contains("jednu značku"))
        assertTrue(one.spokenText, one.spokenText.contains("dvije značke"))
        assertTrue(one.detail, one.detail.contains("1 značka"))

        val five = narrator.narrate(missionFacts[0].copy(badgeCount = 5), Tone.PLAIN, NarratorLanguage.HR)
        assertTrue(five.spokenText, five.spokenText.contains("pet znački"))
        assertFalse("machine Croatian: " + five.spokenText, five.spokenText.contains("pet značka"))
    }

    // --- fixtures --------------------------------------------------------------------

    /** Every mission title shipped in mock/missions.json, with realistic counts around it. */
    private val missionFacts = listOf(
        "Follow three teams" to "Three clubs",
        "Three days in a row" to "Three days",
        "Set a deposit limit" to "Limit set",
        "Put us on your home screen" to "On the home screen",
        "Scan a shop slip" to "Paper and glass",
        "Watch a match live" to "First whistle",
        "Five live check-ins" to "Regular",
        "Pick your club" to "Colours on",
    ).mapIndexed { index, (title, badge) ->
        MomentFacts(
            type = MomentType.MISSION_COMPLETE,
            missionTitle = title,
            badgeName = badge,
            badgeCount = index + 1,
            tierName = if (index + 1 >= 3) "Silver" else "Bronze",
            badgesToNextTier = if (index + 1 >= 3) 8 - (index + 1) else 3 - (index + 1),
        )
    }

    private val tierFacts = listOf(
        MomentFacts(type = MomentType.TIER_REACHED, badgeCount = 3, tierName = "Silver", badgesToNextTier = 5),
        MomentFacts(type = MomentType.TIER_REACHED, badgeCount = 8, tierName = "Gold", badgesToNextTier = 7),
        // The top: no next tier, and the line must say so rather than invent one.
        MomentFacts(type = MomentType.TIER_REACHED, badgeCount = 15, tierName = "Platinum", badgesToNextTier = null),
    )

    private suspend fun assertAllTonesPass(facts: MomentFacts) {
        Tone.entries.forEach { tone ->
            NarratorLanguage.entries.forEach { language ->
                val result = narrator.narrate(facts, tone, language)
                val reason = NarratorGuard.violation(result)
                assertTrue(
                    facts.type.name + " " + tone + " " + language + " rejected (" + reason + "): " +
                        result.headline + " / " + result.detail + " / " + result.spokenText,
                    reason == null,
                )
            }
        }
    }

    private fun assertPasses(line: NarratedText) {
        assertTrue(
            "should have passed: " + line.headline + " / " + line.detail + " / " + NarratorGuard.violation(line),
            NarratorGuard.check(line),
        )
    }
}
