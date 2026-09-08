package eu.feg.ambient.ambient.engine.router

import eu.feg.ambient.ambient.engine.Decision
import eu.feg.ambient.ambient.engine.Moment
import eu.feg.ambient.ambient.engine.Router
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.engine.TimeBucket
import eu.feg.ambient.ambient.engine.ledger.ArmStat
import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.surfaces.ProtectionState
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.random.Random

/**
 * Thompson sampling over Beta(wins, losses) per (context, arm).
 *
 * Thompson rather than epsilon-greedy because exploration has to be cheap here: an epsilon high
 * enough to find a better tone inside a two-minute demo is also high enough to fire an obviously
 * wrong surface on stage. Thompson explores in proportion to how uncertain it actually is, so a
 * well-evidenced arm stops being second-guessed while a fresh one still gets its turn.
 *
 * Counters live in [Ledger], which is file-backed and applies the weekly x0.98 decay lazily on
 * read. Nothing here schedules a job: a decay that only matters when someone looks at the number
 * does not need WorkManager.
 */
class BanditRouter(
    private val ledger: Ledger,
    private val random: Random = Random.Default,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) : Router {

    /**
     * NOTE ON [Decision.protection]: the Router interface is not handed the protection state, so
     * every decision leaves here marked NORMAL and the engine, which did evaluate it, copies the
     * real value in before the row is written. The router must never be the thing that decides
     * whether a user is protected.
     */
    override suspend fun choose(moment: Moment, score: Double, allowed: Set<Surface>): Decision {
        val context = contextOf(moment)

        // PRUNE FIRST. Sampling a forbidden arm and discarding it afterwards would still bias the
        // outcome, because the arm that then gets used was never drawn against the full field.
        val candidates = Arms.armsForSurfaces(allowed).filter { it.surface != Surface.NOTHING }

        if (allowed.isEmpty()) {
            return silence(
                moment,
                score,
                "Nothing was shown because no surface was open at that minute - quiet hours, " +
                    "the alert budget or the protection state had closed all of them.",
            )
        }
        if (score < RewardTable.MIN_SCORE_TO_SPEAK) {
            return silence(
                moment,
                score,
                "Nothing was shown because this moment scored " + format(score) +
                    ", below the " + format(RewardTable.MIN_SCORE_TO_SPEAK) +
                    " bar an interruption has to clear.",
            )
        }
        if (candidates.isEmpty()) {
            return silence(
                moment,
                score,
                "Nothing was shown because the only surface still open was silence itself.",
            )
        }

        // SAMPLE SECOND, over the pruned set only.
        var best = candidates.first()
        var bestDraw = -1.0
        for (arm in candidates) {
            val draw = drawFor(context, arm.id)
            if (draw > bestDraw) {
                bestDraw = draw
                best = arm
            }
        }

        return Decision(
            momentId = moment.id,
            score = score,
            surface = best.surface,
            tone = best.tone,
            armId = best.id,
            sampled = bestDraw,
            protection = ProtectionState.NORMAL,
            reason = "Chose " + phrase(best) + " because that combination has done best for " +
                context + " so far (drew " + format(bestDraw) + " against " +
                candidates.size + " open options).",
        )
    }

    /** Positive rewards become wins, negative ones become losses of the same magnitude. */
    override suspend fun reward(armId: String, context: String, r: Double) {
        if (r == 0.0) return
        applyReward(context, armId, r)
    }

    /**
     * Mute. The gesture is aimed at the context, not at the arm that happened to fire, so every
     * speaking arm takes the hit. The NOTHING arm is deliberately spared: punishing silence
     * because the user just asked for silence would teach exactly the wrong lesson.
     *
     * This writes 48 rows and [Ledger.upsertArm] persists on each one. Mute is a rare, deliberate
     * gesture, so the churn is acceptable, and the alternative is a batch API the ledger has not got.
     */
    suspend fun rewardAllInContext(context: String, r: Double) {
        if (r == 0.0) return
        for (arm in Arms.speakingArms()) {
            applyReward(context, arm.id, r)
        }
    }

    /**
     * Each arm's current posterior mean, best first, for the bandit debug view. Sorted here so
     * the UI can take the top N without knowing anything about Beta distributions.
     */
    fun snapshot(context: String): List<Pair<String, Double>> =
        Arms.allArms()
            .map { arm ->
                val stat = statFor(context, arm.id)
                val a = stat.first + RewardTable.PRIOR_WINS
                val b = stat.second + RewardTable.PRIOR_LOSSES
                arm.id to a / (a + b)
            }
            .sortedByDescending { it.second }

    /** "GOAL_ON_SLIP|EVENING" - the counters this moment is allowed to learn from. */
    fun contextOf(moment: Moment): String {
        val hour = moment.createdAt.toLocalDateTime(zone).hour
        return Arms.contextBucket(moment.type, TimeBucket.of(hour))
    }

    // --- ledger -------------------------------------------------------------------------

    private fun applyReward(context: String, armId: String, r: Double) {
        val stat = statFor(context, armId)
        ledger.upsertArm(
            ArmStat(
                contextBucket = context,
                armId = armId,
                wins = stat.first + if (r > 0) r else 0.0,
                losses = stat.second + if (r < 0) -r else 0.0,
                updatedAt = clock.now().toEpochMilliseconds(),
            ),
        )
    }

    /**
     * Decayed counters, priors NOT applied. Writing the decayed values back on every reward is
     * what keeps the lazy decay honest: the stored number and its fresh timestamp always agree,
     * so the same week is never discounted twice.
     */
    private fun statFor(context: String, armId: String): Pair<Double, Double> {
        val raw = ledger.arm(context, armId) ?: return 0.0 to 0.0
        val decayed = ledger.decayed(raw, clock.now())
        return decayed.wins to decayed.losses
    }

    private fun drawFor(context: String, armId: String): Double {
        val stat = statFor(context, armId)
        return sampleBeta(
            stat.first + RewardTable.PRIOR_WINS,
            stat.second + RewardTable.PRIOR_LOSSES,
        )
    }

    // --- sampling -----------------------------------------------------------------------
    // Ported from the 13.0c spike, which is the version whose behaviour was actually measured.
    // The constants are Marsaglia-Tsang's, not arbitrary; do not tidy them.

    /** Beta(a,b) as the ratio of two Gamma draws. No library needed for this. */
    private fun sampleBeta(a: Double, b: Double): Double {
        val x = sampleGamma(a)
        val y = sampleGamma(b)
        return if (x + y == 0.0) 0.5 else x / (x + y)
    }

    /** Marsaglia-Tsang. Handles shape < 1 by the standard boost. */
    private fun sampleGamma(shape: Double): Double {
        if (shape < 1.0) {
            val u = random.nextDouble().coerceAtLeast(1e-12)
            return sampleGamma(shape + 1.0) * Math.pow(u, 1.0 / shape)
        }
        val d = shape - 1.0 / 3.0
        val c = 1.0 / Math.sqrt(9.0 * d)
        while (true) {
            var x: Double
            var v: Double
            do {
                x = gaussian()
                v = 1.0 + c * x
            } while (v <= 0)
            v = v * v * v
            val u = random.nextDouble()
            if (u < 1.0 - 0.0331 * x * x * x * x) return d * v
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) return d * v
        }
    }

    private fun gaussian(): Double {
        val u1 = random.nextDouble().coerceAtLeast(1e-12)
        val u2 = random.nextDouble()
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2)
    }

    // --- words --------------------------------------------------------------------------

    private fun silence(moment: Moment, score: Double, reason: String): Decision = Decision(
        momentId = moment.id,
        score = score,
        surface = Surface.NOTHING,
        tone = Arms.NOTHING.tone,
        armId = Arms.NOTHING_ID,
        sampled = 0.0,
        protection = ProtectionState.NORMAL,
        reason = reason,
    )

    /** Reads as a sentence on the transparency sheet, not as an enum triple. */
    private fun phrase(arm: Arm): String {
        val where = when (arm.surface) {
            Surface.LIVE_UPDATE -> "the lock screen"
            Surface.WIDGET -> "the home screen widget"
            Surface.IN_APP -> "an in-app card"
            Surface.ALERT -> "a notification"
            Surface.NOTHING -> "silence"
        }
        val voice = arm.tone.name.lowercase(Locale.US).replace('_', ' ')
        val timing = when (arm.timing) {
            Timing.IMMEDIATE -> "right away"
            Timing.NEXT_BREAK -> "at the next break in play"
            Timing.END_OF_MATCH -> "after the final whistle"
        }
        return where + ", in a " + voice + " voice, " + timing
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
}
