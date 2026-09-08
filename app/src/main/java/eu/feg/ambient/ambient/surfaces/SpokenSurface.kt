package eu.feg.ambient.ambient.surfaces

/**
 * What each surface says when it is asked to speak.
 *
 * Pure functions over [SlipSurfaceState] so the compliance rules below can be asserted by a
 * JVM test with no Android and no TTS engine anywhere near them. Every caller — the
 * notification action, the widget button, the Surface Lab — goes through here, so there is
 * one place where "what may be said out loud" is decided.
 */
object SpokenSurface {

    /**
     * The sentence for this slip, or null when there must not be a speaker button at all.
     *
     * The three protection states are three different rules, not three levels of the same
     * one:
     *  - NORMAL      the narrated spoken line, or a plain sentence built from the score.
     *  - CALM        the match and nothing else. No legs, no progress, no sense of a result
     *                approaching — a customer who has asked us to cool down must not be
     *                handed the same excitement in a different medium.
     *  - UNVERIFIED  null. There is no moment, so there is nothing to read and no button.
     *  - BLOCKED     null, for the same reason.
     */
    fun forSlip(state: SlipSurfaceState): String? = when (state.protection) {
        ProtectionState.UNVERIFIED, ProtectionState.BLOCKED -> null
        ProtectionState.CALM -> calmLine(state)
        ProtectionState.NORMAL -> state.narrated?.spokenText ?: plainLine(state)
    }

    /** Score and minute. Deliberately flat, because flat is the point of Calm Mode. */
    private fun calmLine(state: SlipSurfaceState): String {
        val score = scoreSentence(state) ?: state.activeMatch ?: "A match is in progress."
        val minute = state.minute?.let { " " + it + " minutes played." } ?: ""
        return score + minute
    }

    /**
     * The stand-in when no narrator has run yet. It says the same things the card shows, in
     * whole words: a spoken surface must never fall back to reading the chip text, which is
     * where "two slash three tick" comes from in the first place.
     */
    private fun plainLine(state: SlipSurfaceState): String {
        val score = scoreSentence(state)
        val progress = state.legsWon.toString() + " of your " + state.legsTotal +
            " picks " + (if (state.legsWon == 1) "is" else "are") + " home"
        val tail = when {
            state.settled -> ". Nothing is still running."
            state.minutesRemaining != null -> ", with " + state.minutesRemaining + " minutes left."
            else -> "."
        }
        return (score?.let { it + " " } ?: "") + progress + tail
    }

    private fun scoreSentence(state: SlipSurfaceState): String? {
        val home = state.homeTeam ?: return null
        val away = state.awayTeam ?: return null
        val homeScore = state.homeScore ?: return null
        val awayScore = state.awayScore ?: return null
        return home + " " + homeScore + ", " + away + " " + awayScore + "."
    }

    /** Whether a surface should draw the speaker control at all. */
    fun canSpeak(protection: ProtectionState): Boolean =
        protection == ProtectionState.NORMAL || protection == ProtectionState.CALM
}
