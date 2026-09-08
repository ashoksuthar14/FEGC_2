package eu.feg.ambient.ambient.narrator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Prompt construction and output parsing, kept out of the narrators so both can be tested
 * without a model on the machine running the tests.
 *
 * The prompt is deliberately short. Gemma 3 270M given a JSON blob and a long rule list
 * ignored the two-line format and invented scores; a compact fact sentence plus one worked
 * example is far easier for a small model to imitate.
 */
object NanoPrompt {

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
        prettyPrint = false
    }

    private fun toneDescription(tone: Tone): String = when (tone) {
        Tone.PLAIN -> "neutral and factual"
        Tone.WITTY -> "light and human, one small flourish"
        Tone.STATS -> "lead with the number that matters"
        Tone.ONE_LINER -> "as short as possible"
    }

    private fun languageName(language: NarratorLanguage): String = when (language) {
        NarratorLanguage.EN -> "English"
        NarratorLanguage.HR -> "Croatian"
    }

    /**
     * The facts as one compact line. Nulls and empty lists simply do not appear, so the
     * model is never shown a field it has to reason about the absence of.
     */
    fun factsText(facts: MomentFacts): String {
        val parts = mutableListOf<String>()

        if (facts.homeTeam != null && facts.awayTeam != null) {
            val score = if (facts.homeScore != null && facts.awayScore != null) {
                " " + facts.homeScore + "–" + facts.awayScore + " "
            } else {
                " v "
            }
            parts += facts.homeTeam + score + facts.awayTeam
        }
        facts.minute?.let { parts += it.toString() + " minutes played" }
        facts.minutesRemaining?.let { parts += it.toString() + " minutes left" }
        facts.scorer?.let { parts += "scorer " + it }
        facts.myLegDescription?.let { parts += "your pick: " + it }
        if (facts.legsWon != null && facts.legsTotal != null) {
            parts += facts.legsWon.toString() + " of " + facts.legsTotal + " picks won"
        }
        facts.legsLost?.takeIf { it > 0 }?.let { parts += it.toString() + " lost" }
        facts.followedTeam?.let { parts += "you follow " + it }
        facts.kickoffInMinutes?.let { parts += "kick-off in " + it + " minutes" }
        if (facts.digestItems.isNotEmpty()) parts += facts.digestItems.joinToString(", ")

        return if (parts.isEmpty()) "nothing new" else parts.joinToString(". ") + "."
    }

    /** Kept for the diagnostics view: the same facts, in the shape a reviewer can audit. */
    fun factsJson(facts: MomentFacts): String {
        val encoded = json.encodeToJsonElement(MomentFacts.serializer(), facts).jsonObject
        val pruned = encoded.filterNot { (_, value) ->
            (value is JsonPrimitive && value.content.isBlank()) ||
                (value is JsonArray && value.isEmpty())
        }
        return JsonObject(pruned).toString()
    }

    /** A worked example in the target language, so the model has a shape to copy. */
    private fun example(language: NarratorLanguage): String = when (language) {
        NarratorLanguage.EN ->
            "HEADLINE: Arsenal 2–1 · 70'\n" +
                "DETAIL: Your Arsenal win pick is still alive. 20 minutes left."
        NarratorLanguage.HR ->
            "HEADLINE: Arsenal 2–1 · 70'\n" +
                "DETAIL: Tvoj izbor Arsenal još je u igri. Još 20 minuta."
    }

    fun build(facts: MomentFacts, tone: Tone, language: NarratorLanguage): String =
        "Write exactly two lines about this football moment.\n" +
            "Use only the facts below. Do not invent scores, teams, times or results.\n" +
            "Never mention betting, money or advice.\n" +
            "Language: " + languageName(language) + ". Tone: " + toneDescription(tone) + ".\n\n" +
            "Example:\n" + example(language) + "\n\n" +
            "Facts: " + factsText(facts) + "\n\n" +
            "Now write the two lines, and nothing else:"

    /**
     * Pulls the two visual lines out by prefix. Anything else the model volunteers is
     * ignored; a missing prefix counts as unparseable and sends the caller to the template.
     *
     * SPOKEN is deliberately not required here — see [parseSpoken].
     */
    fun parse(raw: String): Pair<String, String>? {
        var headline: String? = null
        var detail: String? = null
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                headline == null && trimmed.startsWith("HEADLINE:", ignoreCase = true) ->
                    headline = trimmed.removePrefix("HEADLINE:").removePrefix("headline:").trim()
                detail == null && trimmed.startsWith("DETAIL:", ignoreCase = true) ->
                    detail = trimmed.removePrefix("DETAIL:").removePrefix("detail:").trim()
            }
        }
        val h = headline?.trim()?.trim('"')
        val d = detail?.trim()?.trim('"')
        if (h.isNullOrBlank() || d.isNullOrBlank()) return null
        // The example is in the prompt; a model that simply echoes it has told us nothing.
        if (h.startsWith("Arsenal 2–1")) return null
        return h to d
    }

    /**
     * The spoken line, when the model produced one.
     *
     * Missing SPOKEN is not a parse failure, and that asymmetry is deliberate. Gemma 3 270M
     * is already at the edge of its ability holding a two-line format; making a third line
     * mandatory would send generations that were perfectly good to the template over a line
     * the template can write itself. So the caller composes the spoken variant from the
     * facts when this returns null, and the customer gets the model's headline with a
     * dependable sentence underneath it rather than neither.
     */
    fun parseSpoken(raw: String): String? {
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("SPOKEN:", ignoreCase = true)) {
                val spoken = trimmed
                    .removePrefix("SPOKEN:")
                    .removePrefix("spoken:")
                    .trim()
                    .trim('"')
                return spoken.takeIf { it.isNotBlank() && !it.startsWith("Arsenal are two one") }
            }
        }
        return null
    }
}
