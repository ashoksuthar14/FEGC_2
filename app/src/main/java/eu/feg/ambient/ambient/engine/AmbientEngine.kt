package eu.feg.ambient.ambient.engine

import android.util.Log
import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.engine.ledger.LedgerEntry
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.Narrator
import eu.feg.ambient.ambient.surfaces.LegState
import eu.feg.ambient.ambient.surfaces.LegStatus
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.surfaces.SlipSurfaceState
import eu.feg.ambient.ambient.surfaces.SurfaceController
import eu.feg.ambient.ambient.surfaces.WidgetState
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The pipeline. Protection, then relevance, then budget, then the bandit, then words, then a
 * surface — and a ledger row at every exit, including the exits that render nothing.
 *
 * "Most events produce nothing, and the ledger says why for each one" is a claim the demo
 * makes out loud, so silence is recorded as deliberately as speech. An early return without a
 * row would be a hole in that claim.
 */
class AmbientEngine(
    private val protectionEvaluator: ProtectionEvaluator,
    private val momentBuilder: MomentBuilder,
    private val scorer: RelevanceScorer,
    private val attentionBudget: AttentionBudget,
    private val router: Router,
    private val narrator: Narrator,
    private val surfaceController: SurfaceController,
    private val ledger: Ledger,
    private val language: () -> NarratorLanguage = { NarratorLanguage.EN },
) {

    suspend fun onEvent(event: MatchEvent): Decision? {
        val protection = protectionEvaluator.evaluate()

        if (protection == ProtectionState.BLOCKED || protection == ProtectionState.UNVERIFIED) {
            // Protection precedes relevance, always. An excluded user's moment is never scored.
            recordSilence(event, protection, reason = reasonFor(protection))
            return null
        }

        val moment = momentBuilder.build(event, protection)
        if (moment == null) {
            recordSilence(event, protection, reason = "Not yours — no open leg and no followed team.")
            return null
        }

        val score = scorer.score(moment)
        val allowed = attentionBudget.allowedSurfaces(moment, protection)
        // The router is not handed the protection state, so every decision leaves it marked
        // NORMAL. The engine evaluated it, so the engine is what puts the truth back — without
        // this the ledger's compliance filter silently under-reports.
        val decision = router.choose(moment, score, allowed).copy(protection = protection)

        if (decision.surface == Surface.NOTHING) {
            record(moment, decision, shown = false)
            return decision
        }

        val text = runCatching {
            narrator.narrate(moment.facts, decision.tone, language())
        }.getOrNull()

        val state = surfaceState(moment, protection, text)
        when (decision.surface) {
            Surface.LIVE_UPDATE -> surfaceController.startLiveUpdate(state)
            Surface.WIDGET -> surfaceController.refreshWidget(WidgetState.Live(state))
            Surface.ALERT -> {
                val sent = text?.let {
                    surfaceController.postAlert(it.headline, it.detail, "mybets")
                } ?: false
                if (!sent) {
                    // The budget said no after the bandit chose it. Record the truth rather
                    // than pretending an alert went out.
                    record(moment, decision.copy(reason = "Alert budget was spent."), shown = false)
                    return decision
                }
            }
            // In-app moments land in the inbox, which the widget already reflects.
            Surface.IN_APP -> surfaceController.refreshWidget(WidgetState.Live(state))
            Surface.NOTHING -> Unit
        }

        record(moment, decision, shown = true)
        Log.i(TAG, "surfaced " + decision.surface + " for " + moment.type + " score=" + score)
        return decision
    }

    // --- feedback ---------------------------------------------------------------------

    /** Called when the user taps a surface. The delay decides how much the arm is rewarded. */
    suspend fun onTapped(entryId: String) {
        val entry = ledger.entries.value.firstOrNull { it.id == entryId } ?: return
        val age = Clock.System.now().toEpochMilliseconds() - entry.createdAt
        val reward = if (age > THIRTY_MINUTES) TAP_LATE else TAP
        ledger.markTapped(entryId)
        ledger.markRewarded(entryId, reward)
        router.reward(entry.armId, entry.contextBucket, reward)
    }

    suspend fun onDismissed(entryId: String) {
        val entry = ledger.entries.value.firstOrNull { it.id == entryId } ?: return
        val age = Clock.System.now().toEpochMilliseconds() - entry.createdAt
        val reward = if (age < TWO_SECONDS) DISMISS_FAST else DISMISS_LATE
        ledger.markDismissed(entryId)
        ledger.markRewarded(entryId, reward)
        router.reward(entry.armId, entry.contextBucket, reward)
    }

    suspend fun onThumbs(entryId: String, up: Boolean) {
        val entry = ledger.entries.value.firstOrNull { it.id == entryId } ?: return
        val reward = if (up) THUMBS_UP else THUMBS_DOWN
        ledger.markRewarded(entryId, reward)
        router.reward(entry.armId, entry.contextBucket, reward)
    }

    /**
     * The user opened the app of their own accord within the hour, and we had chosen silence.
     * Rewarding that is the "we are paid to stay quiet" claim, so it is a real reward path
     * rather than a comment in a slide.
     */
    suspend fun onAppOpenedUnprompted() {
        val hourAgo = Clock.System.now().toEpochMilliseconds() - ONE_HOUR
        ledger.entries.value
            .filter { it.surface == Surface.NOTHING.name && it.createdAt > hourAgo && it.reward == null }
            .forEach { entry ->
                ledger.markRewarded(entry.id, CORRECT_SILENCE)
                router.reward(entry.armId, entry.contextBucket, CORRECT_SILENCE)
            }
    }

    // --- ledger -----------------------------------------------------------------------

    private fun recordSilence(event: MatchEvent, protection: ProtectionState, reason: String) {
        val now = Clock.System.now()
        ledger.record(
            LedgerEntry(
                id = "led-" + now.toEpochMilliseconds() + "-" + event.matchId,
                momentId = "none",
                momentType = event.type.name,
                contextBucket = contextBucket(event.type.name, now),
                protection = protection.name,
                score = 0.0,
                surface = Surface.NOTHING.name,
                tone = "-",
                armId = "-",
                sampled = 0.0,
                reason = reason,
                shownAt = null,
                matchId = event.matchId,
                homeTeam = event.homeTeam,
                awayTeam = event.awayTeam,
                homeScore = event.homeScore,
                awayScore = event.awayScore,
                createdAt = now.toEpochMilliseconds(),
            ),
        )
    }

    private fun record(moment: Moment, decision: Decision, shown: Boolean) {
        val now = Clock.System.now()
        ledger.record(
            LedgerEntry(
                id = "led-" + now.toEpochMilliseconds() + "-" + moment.id,
                momentId = moment.id,
                momentType = moment.type.name,
                contextBucket = contextBucket(moment.type.name, now),
                protection = decision.protection.name,
                score = decision.score,
                surface = decision.surface.name,
                tone = decision.tone.name,
                armId = decision.armId,
                sampled = decision.sampled,
                reason = decision.reason,
                shownAt = if (shown) now.toEpochMilliseconds() else null,
                matchId = moment.matchId,
                homeTeam = moment.facts.homeTeam,
                awayTeam = moment.facts.awayTeam,
                homeScore = moment.facts.homeScore,
                awayScore = moment.facts.awayScore,
                createdAt = now.toEpochMilliseconds(),
            ),
        )
    }

    private fun reasonFor(protection: ProtectionState): String = when (protection) {
        ProtectionState.BLOCKED ->
            "Withheld: the exclusion register says this account is excluded."
        ProtectionState.UNVERIFIED ->
            "Withheld: age is not verified, so nothing is shown."
        else -> "Withheld."
    }

    private fun surfaceState(
        moment: Moment,
        protection: ProtectionState,
        text: eu.feg.ambient.ambient.narrator.NarratedText?,
    ): SlipSurfaceState = SlipSurfaceState(
        slipId = moment.slipId ?: moment.matchId,
        protection = protection,
        legs = listOf(
            LegState(
                description = moment.facts.myLegDescription ?: "Your pick",
                match = (moment.facts.homeTeam ?: "") + " – " + (moment.facts.awayTeam ?: ""),
                status = LegStatus.PENDING,
            ),
        ),
        activeMatch = (moment.facts.homeTeam ?: "") + " – " + (moment.facts.awayTeam ?: ""),
        homeTeam = moment.facts.homeTeam,
        awayTeam = moment.facts.awayTeam,
        homeScore = moment.facts.homeScore,
        awayScore = moment.facts.awayScore,
        minute = moment.facts.minute,
        period = moment.facts.period,
        minutesRemaining = moment.facts.minutesRemaining,
        narrated = text,
    )

    companion object {
        private const val TAG = "AmbientEngine"

        fun contextBucket(momentType: String, at: Instant): String {
            val hour = at.toLocalDateTime(TimeZone.currentSystemDefault()).hour
            return momentType + "|" + TimeBucket.of(hour).name
        }

        // Reward magnitudes live in RewardTable; these mirror the ones the engine applies
        // directly so the feedback paths read in one place.
        const val TAP = 1.0
        const val TAP_LATE = 0.5
        const val DISMISS_FAST = -1.0
        const val DISMISS_LATE = -0.3
        const val THUMBS_UP = 1.5
        const val THUMBS_DOWN = -1.5
        const val CORRECT_SILENCE = 0.5

        private const val TWO_SECONDS = 2_000L
        private const val THIRTY_MINUTES = 30L * 60 * 1000
        private const val ONE_HOUR = 60L * 60 * 1000
    }
}
