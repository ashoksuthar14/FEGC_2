package eu.feg.ambient.ambient.engine

import eu.feg.ambient.ambient.engine.ledger.LedgerEntry

/**
 * Turns a ledger row into a sentence a non-engineer can read.
 *
 * This is the transparency answer, so it is a pure function of a recorded row rather than
 * something reconstructed live: it must be able to explain a decision made an hour ago, on
 * evidence that was true at the time, without re-running the engine.
 */
object Explain {

    fun why(entry: LedgerEntry, history: List<LedgerEntry> = emptyList()): String {
        val moment = humanMoment(entry.momentType)
        val time = entry.contextBucket.substringAfter("|", "").lowercase().ifBlank { "today" }

        if (entry.surface == Surface.NOTHING.name) {
            return moment + ", " + time + ". " + entry.reason
        }

        val evidence = evidenceFor(entry, history)
        val surface = humanSurface(entry.surface)
        val tone = entry.tone.lowercase().replace("_", " ")

        return moment + ", " + time + ". " + evidence +
            " so this went to " + surface + " in " + tone + " tone."
    }

    /**
     * The learned part, in words. Counts come from the ledger itself, so the sentence cannot
     * claim a preference the record does not support.
     */
    private fun evidenceFor(entry: LedgerEntry, history: List<LedgerEntry>) : String {
        val sameContext = history.filter { it.contextBucket == entry.contextBucket }
        if (sameContext.size < 2) {
            return "We have not seen many of these yet, so the choice is still exploring —"
        }

        val sameSurface = sameContext.filter { it.surface == entry.surface }
        val taps = sameSurface.count { it.tappedAt != null }
        val shown = sameSurface.count { it.shownAt != null }

        val ignoredTone = sameContext
            .filter { it.tone != entry.tone && it.dismissedAt != null }
            .groupBy { it.tone }
            .maxByOrNull { it.value.size }

        val first = if (shown > 0) {
            "You opened " + humanSurface(entry.surface) + " " + taps + " of " + shown + " times"
        } else {
            "You have engaged with " + humanSurface(entry.surface) + " before"
        }

        val second = ignoredTone?.let {
            " and ignored " + it.key.lowercase() + " ones " + it.value.size + " times,"
        } ?: ","

        return first + second
    }

    private fun humanMoment(type: String): String = when (type) {
        "GOAL_ON_SLIP" -> "Goal on your slip"
        "LEG_DECIDED" -> "One of your picks landed"
        "LEG_LOST" -> "One of your picks went"
        "SLIP_SETTLED" -> "Your slip was settled"
        "KICKOFF_FOLLOWED" -> "A team you follow kicked off"
        "HALFTIME" -> "Half time"
        "MINUTES_REMAINING" -> "Late in the match"
        "AWAY_DIGEST" -> "You had been away"
        else -> type.lowercase().replace("_", " ")
    }

    private fun humanSurface(surface: String): String = when (surface) {
        Surface.LIVE_UPDATE.name -> "the lock screen"
        Surface.WIDGET.name -> "the home-screen widget"
        Surface.IN_APP.name -> "the app inbox"
        Surface.ALERT.name -> "an alert"
        Surface.NOTHING.name -> "nothing"
        else -> surface.lowercase()
    }
}
