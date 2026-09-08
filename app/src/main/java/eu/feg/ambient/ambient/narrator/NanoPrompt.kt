package eu.feg.ambient.ambient.narrator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Prompt construction and output parsing, kept out of [NanoNarrator] so both can be tested
 * without a model on the machine running the tests.
 */
object NanoPrompt {

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
        prettyPrint = false
    }

    private fun toneDescription(tone: Tone): String = when (tone) {
        Tone.PLAIN -> "neutral and factual"
        Tone.WITTY -> "light and human, one small flourish, never sarcastic, never smug"
        Tone.STATS -> "lead with the number that matters"
        Tone.ONE_LINER -> "as short as possible, telegraphic"
    }

    private fun languageName(language: NarratorLanguage): String = when (language) {
        NarratorLanguage.EN -> "English"
        NarratorLanguage.HR -> "Croatian"
    }

    /**
     * The facts as the model sees them, with nulls and empty lists dropped.
     *
     * The Narrator Lab shows this string verbatim: it is the evidence that no odd, stake or
     * identity ever reaches the model, so it must be the same string the prompt uses.
     */
    fun factsJson(facts: MomentFacts): String {
        val encoded = json.encodeToJsonElement(MomentFacts.serializer(), facts).jsonObject
        val pruned = encoded.filterNot { (_, value) ->
            (value is JsonPrimitive && value.content.isBlank()) ||
                (value is kotlinx.serialization.json.JsonArray && value.jsonArray.isEmpty())
        }
        return JsonObject(pruned).toString()
    }

    fun build(facts: MomentFacts, tone: Tone, language: NarratorLanguage): String =
        """
You write one short status line for a sports app's lock screen.

RULES
- Use ONLY the facts given below. Never add teams, scores, players, times or opinions.
- Never mention odds, stakes, money, bonuses, payouts, or betting advice.
- Never tell the reader to do anything.
- Write in ${languageName(language)}.
- Tone: ${toneDescription(tone)}
- Output EXACTLY two lines and nothing else:
HEADLINE: <at most 60 characters>
DETAIL: <at most 120 characters>

FACTS
${factsJson(facts)}

Output EXACTLY two lines and nothing else.
        """.trimIndent()

    /**
     * Pulls the two lines out by prefix. Anything else the model volunteers is ignored;
     * a missing prefix counts as unparseable and sends the caller to the template.
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
        return h to d
    }
}
