package eu.feg.ambient

import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.NanoPrompt
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NanoPromptTest {

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

    @Test
    fun `facts json omits nulls and empty lists`() {
        val json = NanoPrompt.factsJson(facts)
        assertTrue(json.contains("Liverpool"))
        assertFalse("nulls must not be sent", json.contains("null"))
        assertFalse("empty lists must not be sent", json.contains("digestItems"))
        assertFalse(json.contains("scorer"))
    }

    @Test
    fun `facts json can never carry a price or an identity`() {
        // Not a behaviour test so much as a standing check on the contract: if someone adds
        // a field to MomentFacts, this is where it shows up.
        val json = NanoPrompt.factsJson(facts).lowercase()
        listOf("odds", "stake", "balance", "payout", "bonus", "account", "user", "eur", "€")
            .forEach { forbidden ->
                assertFalse("facts leaked " + forbidden, json.contains(forbidden))
            }
    }

    @Test
    fun `prompt states the language and the tone`() {
        val prompt = NanoPrompt.build(facts, Tone.WITTY, NarratorLanguage.HR)
        assertTrue(prompt.contains("Language: Croatian."))
        assertTrue(prompt.contains("light and human"))
        assertTrue(prompt.contains("HEADLINE:"))
        assertTrue(prompt.contains("DETAIL:"))
    }

    @Test
    fun `prompt ends by asking for the two lines, to resist drift`() {
        val prompt = NanoPrompt.build(facts, Tone.PLAIN, NarratorLanguage.EN)
        assertTrue(prompt.trimEnd().endsWith("Now write the two lines, and nothing else:"))
    }

    @Test
    fun `prompt is short enough for a small model`() {
        val prompt = NanoPrompt.build(facts, Tone.PLAIN, NarratorLanguage.EN)
        assertTrue(
            "prompt grew to " + prompt.length + " chars; a 270M model loses the format",
            prompt.length < 700,
        )
    }

    @Test
    fun `facts read as a sentence rather than json`() {
        val text = NanoPrompt.factsText(facts)
        assertTrue(text, text.contains("Liverpool 1–0 Ipswich"))
        assertTrue(text, text.contains("your pick: Liverpool win"))
        assertTrue(text, text.contains("2 of 3 picks won"))
        assertFalse("no json braces in the prompt facts", text.contains("{"))
    }

    @Test
    fun `absent facts simply do not appear`() {
        val text = NanoPrompt.factsText(MomentFacts(type = MomentType.SLIP_SETTLED))
        assertFalse(text, text.contains("null"))
        assertFalse(text, text.contains("minutes played"))
    }

    @Test
    fun `an echoed example is treated as no answer`() {
        val echoed = NanoPrompt.parse(
            "HEADLINE: Arsenal 2–1 · 70'\nDETAIL: Your Arsenal win pick is still alive.",
        )
        assertNull("the model just copied the example", echoed)
    }

    @Test
    fun `parses a clean two-line answer`() {
        val parsed = NanoPrompt.parse("HEADLINE: Liverpool 1–0 · 61'\nDETAIL: 29 minutes left.")
        assertEquals("Liverpool 1–0 · 61'", parsed?.first)
        assertEquals("29 minutes left.", parsed?.second)
    }

    @Test
    fun `ignores commentary the model volunteers around the two lines`() {
        val parsed = NanoPrompt.parse(
            "Sure! Here you go:\nHEADLINE: Half time · 1–1\nDETAIL: 45 to play.\nHope that helps!",
        )
        assertEquals("Half time · 1–1", parsed?.first)
        assertEquals("45 to play.", parsed?.second)
    }

    @Test
    fun `strips wrapping quotes and tolerates lowercase prefixes`() {
        val parsed = NanoPrompt.parse("headline: \"HT 1–1\"\ndetail: \"45 to play.\"")
        assertEquals("HT 1–1", parsed?.first)
        assertEquals("45 to play.", parsed?.second)
    }

    @Test
    fun `a missing prefix is unparseable`() {
        assertNull(NanoPrompt.parse("HEADLINE: only one line"))
        assertNull(NanoPrompt.parse("DETAIL: only the detail"))
        assertNull(NanoPrompt.parse("just some prose with no prefixes at all"))
        assertNull(NanoPrompt.parse(""))
    }

    @Test
    fun `an empty value is unparseable`() {
        assertNull(NanoPrompt.parse("HEADLINE:\nDETAIL: 45 to play."))
        assertNull(NanoPrompt.parse("HEADLINE: HT 1–1\nDETAIL:   "))
    }
}
