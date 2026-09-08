package eu.feg.ambient.ambient.surfaces

import eu.feg.ambient.ambient.narrator.NarratedText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * What the protection layer has decided about this user right now.
 *
 * Renderers depend on this and never on UserState: step 13A replaces how it is derived
 * (register check, age assurance) without any surface needing to change.
 */
enum class ProtectionState { NORMAL, CALM, UNVERIFIED, BLOCKED }

enum class LegStatus { PENDING, WON, LOST, VOID }

data class LegState(
    /** "Liverpool to win" */
    val description: String,
    /** "Liverpool – Ipswich" */
    val match: String,
    val status: LegStatus,
)

/**
 * Everything a surface is allowed to show about a slip in flight.
 *
 * COMPLIANCE — this type carries no stake, no odds, no potential return and no balance, and
 * it must never gain one. Money does not reach an OS surface: not the lock screen, not the
 * always-on display, not the status chip, not the widget. The pitch's claim is that this is
 * structurally impossible rather than merely filtered, and that holds only while there is no
 * field here to carry a number. See MomentFacts for the same rule on the narrator side.
 */
data class SlipSurfaceState(
    val slipId: String,
    val protection: ProtectionState,
    val legs: List<LegState>,
    /** The match currently live, if any. */
    val activeMatch: String? = null,
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val minute: Int? = null,
    /** "1. poluvrijeme" */
    val period: String? = null,
    val minutesRemaining: Int? = null,
    /** The competition the live match is in -- the card's header names this, not the club. */
    val competition: String? = null,
    /** Region or tier under the competition: "ENG", "HNL". */
    val region: String? = null,
    /** Minutes of the goals so far, for the timeline. Empty means unknown; spread evenly. */
    val goalMinutes: List<Int> = emptyList(),
    /** From step 12's Narrator. */
    val narrated: NarratedText? = null,
    val settled: Boolean = false,
) {
    val legsWon: Int get() = legs.count { it.status == LegStatus.WON }
    val legsLost: Int get() = legs.count { it.status == LegStatus.LOST }
    val legsTotal: Int get() = legs.size

    /**
     * The status-bar chip, e.g. "2/3 ✓ · 61'". The chip is about 96dp wide and shows an icon
     * only once the text stops fitting, so this stays short by construction.
     */
    val chipText: String
        get() {
            val progress = legsWon.toString() + "/" + legsTotal
            return when {
                settled -> progress + " ✓"
                minute != null -> progress + " ✓ · " + minute + "'"
                else -> progress + " ✓"
            }
        }
}

sealed interface WidgetState {
    /**
     * N5. Shown when nothing is live, a recap exists for the period and it has not been seen.
     * Club-themed like every other card; unchanged in Calm Mode because it holds no money.
     */
    data class Recap(val recap: eu.feg.ambient.ambient.recap.Recap) : WidgetState

    data class PreMatch(
        val match: String,
        val kickoffIn: Duration,
        val legs: List<LegState>,
    ) : WidgetState

    data class Live(val slip: SlipSurfaceState) : WidgetState

    data class Settled(val slip: SlipSurfaceState) : WidgetState

    data class Digest(val headline: String, val detail: String, val since: Instant) : WidgetState

    data class Idle(val nextFixture: String?, val kickoff: Instant?) : WidgetState

    data class Protected(
        val protection: ProtectionState,
        val lastRegisterCheck: Instant?,
    ) : WidgetState
}

/**
 * What the surfaces actually did, as opposed to what we asked them to do.
 *
 * [lastPostWasPromoted] is read back from the system after posting rather than assumed:
 * a notification can meet every documented requirement and still not be promoted, and on
 * stage we would rather show the real answer than a hopeful one.
 */
data class SurfaceDiagnostics(
    val notificationPermissionGranted: Boolean = false,
    val canPostPromoted: Boolean = false,
    val lastPostWasPromoted: Boolean? = null,
    val alertsSentToday: Int = 0,
    val alertBudget: Int = 1,
    val activeLiveUpdateSlipId: String? = null,
    val lastWidgetUpdate: Instant? = null,
    val lastError: String? = null,
)

/**
 * The single door to every OS surface.
 *
 * The Surface Lab drives this by hand today; step 13B's Moment Engine calls exactly the same
 * methods. Nothing outside this package may touch a renderer directly, which is what makes
 * that swap a drop-in.
 */
interface SurfaceController {
    suspend fun startLiveUpdate(state: SlipSurfaceState)

    suspend fun updateLiveUpdate(state: SlipSurfaceState)

    suspend fun endLiveUpdate(slipId: String, settled: Boolean)

    suspend fun refreshWidget(state: WidgetState)

    /** Returns false when the 24h budget is spent — never silently swallowed. */
    suspend fun postAlert(headline: String, detail: String, deepLink: String): Boolean

    suspend fun refreshShortcuts(protection: ProtectionState)

    /**
     * What the widget is currently showing, or null if this process has not drawn it yet.
     *
     * A caller that wants to restyle the widget must repaint what is there rather than pick
     * a state again from scratch.
     */
    fun currentWidgetState(): WidgetState?

    /**
     * Re-reads permission and promotion state from the system. On the contract rather than
     * the implementation so the Lab never has to downcast — a downcast in `ui/` would be the
     * first crack in "the Moment Engine is a drop-in replacement".
     */
    fun refreshDiagnostics()

    /** Demo affordance: clears the 24h alert budget. Labelled as such wherever it is offered. */
    fun resetAlertBudget()

    val diagnostics: StateFlow<SurfaceDiagnostics>
}
