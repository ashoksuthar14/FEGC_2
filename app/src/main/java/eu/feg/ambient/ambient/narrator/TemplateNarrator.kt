package eu.feg.ambient.ambient.narrator

/**
 * The pre-rendered view of [MomentFacts] the line tables read from. Every value is already
 * a string with a sensible stand-in, so a table entry never has to handle a null.
 */
internal class Facts(private val f: MomentFacts) {
    val type: MomentType get() = f.type
    val home: String = f.homeTeam ?: "Home"
    val away: String = f.awayTeam ?: "Away"
    /** En dash, matching the way scores read on the Live screen. */
    val score: String = (f.homeScore ?: 0).toString() + "–" + (f.awayScore ?: 0)
    val min: String = (f.minute ?: 0).toString() + "'"
    val total: String = (f.legsTotal ?: 0).toString()
    val won: String = (f.legsWon ?: 0).toString()
    val lost: String = (f.legsLost ?: 0).toString()
    val legs: String = won + "/" + total
    val left: String = ((f.legsTotal ?: 0) - (f.legsWon ?: 0) - (f.legsLost ?: 0))
        .coerceAtLeast(0).toString()
    val leg: String = f.myLegDescription ?: "your pick"
    val remaining: String = (f.minutesRemaining ?: (90 - (f.minute ?: 0)).coerceAtLeast(0)).toString()
    val followed: String = f.followedTeam ?: home
    val kickoff: String = (f.kickoffInMinutes ?: 0).toString()
    val habit: String = f.habitHints.firstOrNull() ?: "One to keep an eye on."
    val digest: String = f.digestItems.takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
        ?.let { if (it.endsWith(".")) it else it + "." }
        ?: "Nothing new since you left."
}

internal interface TemplateLines {
    fun lines(f: Facts, tone: Tone): Pair<String, String>
}

/**
 * The narrator that cannot fail. It has no model, no network and no timeout, so it is both
 * the default and the last rung of the ladder Phase 2 builds on top.
 *
 * Output is clamped to the guard's limits rather than merely checked against them: a long
 * team name must not be able to push a headline over 60 characters.
 */
class TemplateNarrator : Narrator {

    override suspend fun narrate(
        facts: MomentFacts,
        tone: Tone,
        language: NarratorLanguage,
    ): NarratedText {
        val started = System.nanoTime()
        val table: TemplateLines = when (language) {
            NarratorLanguage.EN -> TemplateLinesEn
            NarratorLanguage.HR -> TemplateLinesHr
        }
        val (headline, detail) = table.lines(Facts(facts), tone)
        return NarratedText(
            headline = clamp(headline, NarratorGuard.MAX_HEADLINE),
            detail = clamp(detail, NarratorGuard.MAX_DETAIL),
            engine = NarratorEngine.TEMPLATE,
            latencyMs = (System.nanoTime() - started) / 1_000_000,
        )
    }

    /** Trims on a word boundary where possible, so a clamp never ends mid-word. */
    private fun clamp(text: String, max: Int): String {
        val trimmed = text.trim()
        if (trimmed.length <= max) return trimmed
        val cut = trimmed.take(max)
        val lastSpace = cut.lastIndexOf(' ')
        return if (lastSpace > max / 2) cut.take(lastSpace).trimEnd(',', '.', ' ') else cut.trimEnd()
    }
}
