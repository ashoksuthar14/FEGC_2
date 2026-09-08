package eu.feg.ambient.ambient.loyalty

import android.content.Context
import android.util.Log
import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.surfaces.widget.WidgetStoreVersion
import eu.feg.ambient.data.repo.BetRepository
import eu.feg.ambient.data.repo.UserStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * The one place mission progress is computed, and the one place protection pauses it.
 *
 * ONE SUBSCRIBER, NOT A DOZEN CALLS. Nothing in the UI says "record a check-in": this class
 * watches the sources the app already keeps -- the user state, the placed bets, the ledger
 * and the protection state -- and recomputes every mission from scratch whenever any of
 * them moves. That is what makes progress a pure function of state ([MissionEvaluator])
 * instead of a tally that drifts the first time a screen forgets to call something, and it
 * is what lets the store stay monotonic: an absolute count can be written ten times and
 * mean the same thing each time.
 *
 * PROTECTION IS READ HERE. When the state is not NORMAL nothing is recorded and nothing is
 * awarded; what was already earned stays in the store, readable, untouched. In CALM that is
 * the whole message -- slowing down must never cost anyone a badge -- and in UNVERIFIED or
 * BLOCKED the tracker is inert. The screens never make this check, because a screen that
 * has to remember to pause is a screen that will one day forget.
 */
class MissionTracker(
    private val missionRepository: MissionRepository,
    private val badgeAwarder: BadgeAwarder,
    private val userStateRepository: UserStateRepository,
    private val betRepository: BetRepository,
    private val ledger: Ledger,
    private val protection: StateFlow<ProtectionState>,
    /**
     * Whether the mechanic accrues in this state. Wire it to LoyaltyRepository.accrues so
     * the tracker and the screens' "paused" flag cannot disagree about what CALM means.
     */
    private val accrues: (ProtectionState) -> Boolean = { it == ProtectionState.NORMAL },
    /**
     * Whether any PSK widget is on a home screen. Polled on each recompute rather than
     * observed, because the launcher tells nobody when a widget is placed. AppContainer
     * passes WidgetRefresher.anyPlaced, which asks AppWidgetManager -- the only party that
     * actually knows. Inferring it from our own state does not work: the engine refreshes
     * widgets whether or not any exist (updateAll on zero instances is a no-op), so a
     * refresh row in the ledger proves nothing.
     */
    private val widgetPlaced: () -> Boolean = { false },
    private val evaluator: MissionEvaluator = MissionEvaluator(),
    private val now: () -> Instant = { Clock.System.now() },
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {

    /** Serialises the collector against [recompute] calls from outside. */
    private val mutex = Mutex()

    /**
     * Subscribes once. Call it exactly once per process: the flows are shared, and a second
     * tracker would award nothing twice (the store forbids it) but would recompute everything
     * twice for no reason.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                userStateRepository.state,
                betRepository.placedBets,
                ledger.entries,
                protection,
                // The widget store is written on every widget draw and every widget action.
                // It is not a "widget placed" signal, but it is the closest thing to one that
                // is observable, and it makes the poll in [widgetPlaced] land within seconds
                // of the first card being drawn rather than on the next unrelated change.
                WidgetStoreVersion.flow,
            ) { _, _, _, guard, _ -> guard }
                .collect { guard -> mutex.withLock { recomputeLocked(guard) } }
        }
    }

    /**
     * Recomputes now, outside the flow. For the Lab, and for callers that know something
     * changed that no flow carries -- a widget being placed is the one case today.
     */
    suspend fun recompute() {
        mutex.withLock { recomputeLocked(protection.value) }
    }

    private suspend fun recomputeLocked(guard: ProtectionState) {
        // Paused. Not "record but do not award": recording would let a customer in CALM
        // watch a bar fill toward a badge they cannot receive, which is a nag in a
        // different font. The store is not touched at all.
        if (!accrues(guard)) return

        val signals = evaluator.signalsFrom(
            user = userStateRepository.state.value,
            bets = betRepository.placedBets.value,
            entries = ledger.entries.value,
            widgetPlaced = widgetPlaced(),
            timeZone = timeZone(),
        )
        val progress = evaluator.progress(signals)
        val at = now()

        for (mission in missionRepository.snapshot()) {
            val observed = progress[mission.type] ?: 0
            // Only ever forward. The store already refuses to go backwards, but writing a
            // completed mission again would also re-stamp its completedAt on every recompute,
            // and "completed just now" should mean exactly once.
            if (observed > mission.progress) {
                missionRepository.record(mission.id, observed, at)
            }
            // Asked on every pass, not only on the pass that crossed the line: a process
            // killed between record() and award() must not leave a completed mission with
            // no badge forever. award() returns null when the badge is held, so this is
            // cheap and cannot announce anything twice.
            if (observed >= mission.target || mission.isComplete) {
                val badge = badgeAwarder.onCompleted(mission)
                if (badge != null) Log.i(TAG, "badge " + badge.id + " for " + mission.id)
            }
        }
    }

    companion object {
        private const val TAG = "MissionTracker"

    }
}
