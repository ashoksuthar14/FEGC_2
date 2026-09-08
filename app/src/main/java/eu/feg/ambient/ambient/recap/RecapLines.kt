package eu.feg.ambient.ambient.recap

import eu.feg.ambient.ambient.narrator.NarratorGuard
import eu.feg.ambient.ambient.narrator.NarratorLanguage

/**
 * The words on a recap, before and after the narrator.
 *
 * The narrator is given the facts as digest items and asked for a PLAIN catch-up, because
 * AWAY_DIGEST is the one moment type whose template is "join the facts and say them", which
 * is exactly what a recap is. What the template gets wrong is the frame: its headline and
 * spoken preamble say "While you were away", and a recap is not a catch-up — it is a gift.
 * So the frame is written here and the narrator supplies the body.
 *
 * Every string in this file is checked by [NarratorGuard] downstream, so a wording change
 * that introduces a money word fails the guard rather than reaching a card.
 */
internal object RecapLines {

    /** Localised, because it is the one line on the card that is ours rather than a count. */
    fun headline(period: RecapPeriod, language: NarratorLanguage): String = when (language) {
        NarratorLanguage.EN -> when (period) {
            RecapPeriod.MONTH -> "Your month with PSK"
            RecapPeriod.SEASON -> "Your season with PSK"
        }
        NarratorLanguage.HR -> when (period) {
            RecapPeriod.MONTH -> "Tvoj mjesec uz PSK"
            RecapPeriod.SEASON -> "Tvoja sezona uz PSK"
        }
    }

    /**
     * The facts as fragments, in the order they matter: how much, how well, who, how often.
     *
     * English only, as the digest's items are — DigestRanker writes its fragments in English
     * regardless of narrator language, and a recap that localised its fragments while the
     * digest did not would be the odd one out. The headline carries the language.
     */
    fun items(counts: RecapCounter.Counts, period: RecapPeriod): List<String> {
        val items = mutableListOf<String>()
        items += plural(counts.matchesFollowed, "match", "matches") + " followed"

        // A "prediction" is a leg outcome, never money — see RecapCounter. Zero of zero is
        // not a fact worth a fragment, so a period with no decided legs simply omits it.
        if (counts.predictionsTotal > 0) {
            items += plural(counts.predictionsRight, "prediction", "predictions") + " right"
        }

        val top = counts.topTeam
        if (top != null) {
            // "Every single week" has to be true to be worth saying. A month is four weeks;
            // a team followed four or more times in one earned the line, fewer did not.
            val weeks = period.windowDays / 7
            items += when {
                counts.topTeamCount >= weeks -> top + ", every single week"
                counts.topTeamCount >= 2 -> top + ", " + counts.topTeamCount + " times"
                else -> top
            }
        }

        if (counts.longestStreak >= 2) {
            items += "checked in " + counts.longestStreak + " days running"
        }
        return items
    }

    /**
     * The narrator's spoken line with its catch-up preamble swapped for the recap's own.
     *
     * The narrator is trusted for the body — it is the same sentence the digest would speak,
     * and on the model rungs of the ladder it may be a better one than the template's. Only
     * the first clause is ours to replace, and only when it is the one we know the template
     * writes; anything else is left exactly as the narrator said it.
     */
    fun spoken(headline: String, narratedSpoken: String): String {
        var body = narratedSpoken.trim()
        for (preamble in AWAY_PREAMBLES) {
            if (body.startsWith(preamble, ignoreCase = true)) {
                body = body.substring(preamble.length).trim()
                break
            }
        }
        return clamp(headline + ". " + body, NarratorGuard.MAX_SPOKEN)
    }

    private fun plural(count: Int, one: String, many: String): String =
        count.toString() + " " + (if (count == 1) one else many)

    /** Trims on a word boundary where possible, the way TemplateNarrator does. */
    private fun clamp(text: String, max: Int): String {
        if (text.length <= max) return text
        val cut = text.take(max)
        val lastSpace = cut.lastIndexOf(' ')
        return if (lastSpace > max / 2) cut.take(lastSpace).trimEnd(',', '.', ' ') else cut.trimEnd()
    }

    /** SpokenLinesEn and SpokenLinesHr, AWAY_DIGEST, Tone.PLAIN — the only tone a recap uses. */
    private val AWAY_PREAMBLES = listOf("While you were away.", "Dok te nije bilo.")
}
