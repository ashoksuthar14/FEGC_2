package eu.feg.ambient.ambient.engine.router

/**
 * Every number the router learns from, in one block.
 *
 * These are demo dials. They are constants rather than a config file precisely so that they can
 * be retuned ten seconds before the pitch, recompiled, and understood at a glance by whoever is
 * doing the retuning. Nothing else in the engine may hard-code a reward magnitude.
 *
 * The signs carry the whole meaning: a tap is the user saying "yes, like that", a fast dismissal
 * is them saying "not like that", and silence that ends with them opening the app anyway is the
 * router being right to stay out of the way.
 */
object RewardTable {

    // --- what the user did ---------------------------------------------------------------

    /** They tapped it while it still mattered. The strongest ordinary signal there is. */
    const val TAP = 1.0

    /**
     * Tapped, but long after the moment passed. Still positive — they came back for it — but
     * half credit, because a surface that is only interesting an hour later did not need to
     * interrupt anyone.
     */
    const val TAP_LATE = 0.5

    /** Swiped away almost instantly. That is not indifference, it is a rejection. */
    const val DISMISS_FAST = -1.0

    /** Dismissed eventually. Mild: they read it, it just was not worth keeping. */
    const val DISMISS_LATE = -0.3

    /** Neither tapped nor dismissed before it expired. Weakly negative, not damning. */
    const val IGNORED = -0.2

    /** The explicit gestures. Deliberately the largest, because they are the least ambiguous. */
    const val THUMBS_UP = 1.5
    const val THUMBS_DOWN = -1.5

    /**
     * Mute. Applied to every speaking arm in the context, not just the one that fired, because
     * the user is not objecting to a tone — they are objecting to being spoken to at all here.
     */
    const val MUTED = -2.0

    /**
     * The NOTHING arm was chosen and the user opened the app within the hour anyway. They got
     * there on their own; the interruption would have bought nothing. Small, because we cannot
     * prove the alternative would have annoyed them.
     */
    const val CORRECT_SILENCE = 0.5

    // --- the windows those rules read ----------------------------------------------------

    /** Under this, a dismissal is a reflex rather than a judgement. */
    const val FAST_DISMISS_MILLIS = 2_000L

    /** Past this, a tap is a revisit rather than a response. */
    const val LATE_TAP_MILLIS = 30L * 60 * 1000

    /** How long after choosing silence an app open still counts as our silence being right. */
    const val SILENCE_CREDIT_MILLIS = 60L * 60 * 1000

    // --- cold start ----------------------------------------------------------------------

    /**
     * MEASURED, NOT GUESSED. The 13.0c spike (BanditSpikeTest) swept prior strengths and found
     * this pair is the one where an incumbent arm visibly flips after exactly two rewarding
     * gestures. That is the demo beat: "it learned that in thirty seconds, from two gestures."
     *
     * A weaker prior flipped on a single gesture, which on stage reads as randomness rather than
     * learning. A stronger one needed five or more and the beat died waiting.
     */
    const val PRIOR_WINS = 3.0
    const val PRIOR_LOSSES = 1.0

    /**
     * Below this relevance score the router stays quiet whatever the arms think. The bandit
     * optimises how to say things; it is never allowed to decide that something unimportant is
     * worth an interruption because a tone happens to be performing well.
     */
    const val MIN_SCORE_TO_SPEAK = 0.3

    // --- helpers -------------------------------------------------------------------------

    /** [shownAt] and [tappedAt] in epoch millis. */
    fun forTap(shownAt: Long, tappedAt: Long): Double =
        if (tappedAt - shownAt > LATE_TAP_MILLIS) TAP_LATE else TAP

    fun forDismiss(shownAt: Long, dismissedAt: Long): Double =
        if (dismissedAt - shownAt < FAST_DISMISS_MILLIS) DISMISS_FAST else DISMISS_LATE

    /** Silence only earns credit if the user turned up by themselves inside the window. */
    fun forSilence(decidedAt: Long, appOpenedAt: Long?): Double {
        if (appOpenedAt == null) return 0.0
        val gap = appOpenedAt - decidedAt
        return if (gap in 0..SILENCE_CREDIT_MILLIS) CORRECT_SILENCE else 0.0
    }
}
