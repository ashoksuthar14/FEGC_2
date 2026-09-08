package eu.feg.ambient.ambient.engine.moment

import eu.feg.ambient.ambient.engine.Moment
import eu.feg.ambient.ambient.engine.Ownership
import eu.feg.ambient.ambient.engine.RelevanceScorer
import eu.feg.ambient.ambient.narrator.MomentType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

/**
 * Four terms of arithmetic, in this order:
 *
 *   score = (base + decides + urgency) x repeat-decay,  clamped to 0..1
 *
 *   base      ON_MY_SLIP and DECIDES_A_LEG 0.6, FOLLOWED_TEAM 0.3, NEITHER 0.0
 *   decides   +0.3 when this event settles a leg
 *   urgency   0.0 up to [URGENCY_FROM_MINUTES] left, then linear to +0.2 at full time
 *   decay     x0.4 for an immediate repeat of the same type on the same match, rising
 *             linearly back to x1.0 once [REPEAT_WINDOW] has passed
 *
 * Deterministic and auditable on purpose. A learned score would rank better and could not be
 * read back to a regulator, or to the user tapping "Why this?", in one sentence. [explain]
 * renders exactly the terms above, so the sentence cannot drift from the number: both come
 * from the same [Breakdown].
 *
 * The urgency curve is a ramp rather than a step because minute 79 and minute 80 are the same
 * situation, and a step there would make two identical moments rank a surface apart.
 */
class DefaultRelevanceScorer(
    private val clock: () -> Instant = { Clock.System.now() },
) : RelevanceScorer {

    /**
     * When we last scored each match-and-type pair. Keyed on both, not on type alone: a goal
     * in a second match is news, not a repeat. In memory only — a decay that survived a
     * restart would silence the first moment after a crash, which is the worst time to be
     * quiet. Bounded so a long session cannot grow it without limit.
     */
    private val lastScoredAt = LinkedHashMap<String, Instant>()

    /** The arithmetic behind the last score for a moment, so [explain] never recomputes it. */
    private val lastBreakdown = LinkedHashMap<String, Breakdown>()

    @Synchronized
    override fun score(moment: Moment): Double {
        val breakdown = computeBreakdown(moment, clock())
        lastScoredAt[key(moment)] = breakdown.now
        lastBreakdown[moment.id] = breakdown
        prune()
        return breakdown.total
    }

    /** Insertion-ordered, so dropping from the front drops the oldest. */
    private fun prune() {
        while (lastScoredAt.size > MAX_RECENCY_ENTRIES) {
            lastScoredAt.remove(lastScoredAt.keys.first())
        }
        while (lastBreakdown.size > MAX_RECENCY_ENTRIES) {
            lastBreakdown.remove(lastBreakdown.keys.first())
        }
    }

    /**
     * A plain sentence for the transparency sheet. Reads the breakdown recorded by [score],
     * so calling it afterwards cannot report a different number than the one that was acted
     * on — recomputing here would find the decay clock reset by [score] itself.
     */
    @Synchronized
    fun explain(moment: Moment): String {
        val b = lastBreakdown[moment.id] ?: computeBreakdown(moment, clock())
        val parts = mutableListOf<String>()

        parts += when (moment.ownership) {
            Ownership.DECIDES_A_LEG, Ownership.ON_MY_SLIP -> "it is on your slip (" + two(b.base) + ")"
            Ownership.FOLLOWED_TEAM -> "you follow a team in it (" + two(b.base) + ")"
            Ownership.MINE -> "it is about you rather than a match (" + two(b.base) + ")"
            Ownership.NEITHER -> "you own no part of it (0.00)"
        }
        if (b.decides > 0.0) {
            parts += "it settles one of your legs (+" + two(b.decides) + ")"
        }
        if (b.urgency > 0.0) {
            parts += "there are " + b.minutesRemaining + " minutes left (+" + two(b.urgency) + ")"
        }
        if (b.decay < 1.0) {
            parts += "we said something similar " + b.repeatMinutes + " minutes ago (x" + two(b.decay) + ")"
        }

        val why = parts.joinToString(", ")
        return "Scored " + two(b.total) + " because " + why + "."
    }

    // ---- arithmetic ------------------------------------------------------------------

    private fun computeBreakdown(moment: Moment, now: Instant): Breakdown {
        val base = when (moment.ownership) {
            Ownership.ON_MY_SLIP, Ownership.DECIDES_A_LEG -> BASE_ON_SLIP
            Ownership.FOLLOWED_TEAM -> BASE_FOLLOWED
            Ownership.MINE -> BASE_MINE
            Ownership.NEITHER -> 0.0
        }
        val decides = if (decidesALeg(moment)) DECIDES_BONUS else 0.0

        val minutesRemaining = moment.facts.minutesRemaining
        val urgency = urgencyFor(minutesRemaining)

        val since = lastScoredAt[key(moment)]
        val elapsed = if (since == null) null else (now - since)
        val decay = when {
            elapsed == null -> 1.0
            elapsed >= REPEAT_WINDOW -> 1.0
            // Linear from REPEAT_FLOOR at zero elapsed back to 1.0 at the window's edge.
            else -> REPEAT_FLOOR + (1.0 - REPEAT_FLOOR) * (elapsed / REPEAT_WINDOW)
        }

        val total = ((base + decides + urgency) * decay).coerceIn(0.0, 1.0)
        return Breakdown(
            now = now,
            base = base,
            decides = decides,
            urgency = urgency,
            decay = decay,
            minutesRemaining = minutesRemaining ?: 0,
            repeatMinutes = if (elapsed == null) 0 else (elapsed.inWholeSeconds / 60.0).roundToInt(),
            total = total,
        )
    }

    /**
     * Zero until the last [URGENCY_FROM_MINUTES] minutes, then a straight line to
     * [URGENCY_MAX] at the whistle. Nothing before the closing stretch is urgent; a slip two
     * minutes from settling is the whole reason this product interrupts anyone.
     */
    private fun urgencyFor(minutesRemaining: Int?): Double {
        if (minutesRemaining == null) return 0.0
        val clamped = minutesRemaining.coerceAtLeast(0)
        if (clamped >= URGENCY_FROM_MINUTES) return 0.0
        val closeness = (URGENCY_FROM_MINUTES - clamped).toDouble() / URGENCY_FROM_MINUTES
        return URGENCY_MAX * closeness
    }

    private fun decidesALeg(moment: Moment): Boolean =
        moment.ownership == Ownership.DECIDES_A_LEG ||
            moment.type == MomentType.LEG_DECIDED ||
            moment.type == MomentType.LEG_LOST

    private fun key(moment: Moment): String = moment.matchId + "/" + moment.type.name

    private fun two(value: Double): String {
        val rounded = (value * 100).roundToInt()
        val whole = rounded / 100
        val fraction = rounded % 100
        return whole.toString() + "." + (if (fraction < 10) "0" else "") + fraction
    }

    /** Every term of one score, kept so the sentence and the number cannot disagree. */
    private data class Breakdown(
        val now: Instant,
        val base: Double,
        val decides: Double,
        val urgency: Double,
        val decay: Double,
        val minutesRemaining: Int,
        val repeatMinutes: Int,
        val total: Double,
    )

    private companion object {
        const val BASE_ON_SLIP = 0.6
        const val BASE_FOLLOWED = 0.3

        /**
         * Loyalty: the lowest thing that still speaks.
         *
         * IT WAS 0.25, AND THAT WAS A BUG rather than a strict setting. A loyalty moment gets
         * no DECIDES_BONUS and no urgency -- minutesRemaining is null for a badge -- so its
         * total IS its base, and RewardTable.MIN_SCORE_TO_SPEAK is 0.30 and gates every
         * surface, not merely alerts. At 0.25 every badge the app ever awarded was routed to
         * NOTHING, for ever: the ledger filled with correct-sounding silence rows and two
         * moment types' worth of copy could not be reached by any code path.
         *
         * So it clears the bar with a little headroom, and stays well under a goal on the
         * customer's own slip. Clearing the bar is not the same as interrupting: it makes the
         * moment eligible, and the attention budget still decides whether an alert is one of
         * the surfaces on offer. That division is what "missions never nag" actually rests on
         * -- not on a score too low to be heard at all.
         */
        const val BASE_MINE = 0.35
        const val DECIDES_BONUS = 0.3

        /** The closing stretch. Before this, urgency contributes nothing. */
        const val URGENCY_FROM_MINUTES = 30
        const val URGENCY_MAX = 0.2

        /** How hard an immediate repeat is punished, and how long the punishment lasts. */
        const val REPEAT_FLOOR = 0.4
        val REPEAT_WINDOW = 10.minutes

        const val MAX_RECENCY_ENTRIES = 64
    }
}
