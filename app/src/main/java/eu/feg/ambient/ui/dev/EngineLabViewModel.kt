package eu.feg.ambient.ui.dev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.feg.ambient.ambient.engine.ledger.LedgerEntry
import eu.feg.ambient.ambient.engine.ledger.RegisterCheck
import eu.feg.ambient.ambient.engine.protection.DefaultProtectionEvaluator
import eu.feg.ambient.ambient.engine.protection.RegisterEntry
import eu.feg.ambient.ambient.engine.router.Arms
import eu.feg.ambient.ambient.engine.router.RewardTable
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.engine.TimeBucket
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.surfaces.SpeakResult
import eu.feg.ambient.core.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** One row of the register panel's table, plus the two flows that decide what it means. */
data class RegisterPanelState(
    val entries: List<RegisterEntry> = emptyList(),
    val protection: ProtectionState = ProtectionState.BLOCKED,
    val ageVerified: Boolean = true,
    val lastCheckedAt: Instant? = null,
    /** Last check plus the evaluator's 15-minute cache window — when the answer goes stale. */
    val nextDueAt: Instant? = null,
    val checking: Boolean = false,
    val notice: String? = null,
) {
    val demoPlayerListed: Boolean
        get() = entries.any { it.playerRef.equals(DefaultProtectionEvaluator.DEMO_PLAYER, true) }
}

data class WhyThisState(
    /** Filtered for display. */
    val rows: List<LedgerEntry> = emptyList(),
    /** Unfiltered, because Explain reads the whole history to justify one row. */
    val history: List<LedgerEntry> = emptyList(),
    val checks: List<RegisterCheck> = emptyList(),
    val complianceOnly: Boolean = false,
)

/** [mean] is the posterior mean the router would sample around; the counts are what earned it. */
data class ArmBar(
    val armId: String,
    val mean: Double,
    val wins: Double,
    val losses: Double,
)

data class BanditState(
    val context: String = EngineLabViewModel.DEFAULT_CONTEXT,
    val bars: List<ArmBar> = emptyList(),
    val lastGesture: String? = null,
)

/**
 * Step 13 Prompt 4. One ViewModel behind the Register Panel, "Why this?" and the Bandit Debug
 * screens, because all three read the same three objects — the register, the ledger and the
 * router — and splitting them would mean three copies of the same refresh plumbing.
 *
 * Nothing here sets [ProtectionState] directly. The register panel edits the *register*, and
 * the evaluator derives the state from it; that is the whole point of replacing the old
 * three-state radio button, which let a demo claim a protection it had not actually earned.
 */
class EngineLabViewModel(private val container: AppContainer) : ViewModel() {

    // --- spoken moments (N1) --------------------------------------------------------------

    /** What the last speaker tap did, so a screen can say "phone is on silent" out loud. */
    private val _speakResult = MutableStateFlow<SpeakResult?>(null)
    val speakResult: StateFlow<SpeakResult?> = _speakResult

    /**
     * Reads one row's sentence aloud. Never called except from a tap — there is no code path
     * here that speaks on its own, and there must not be one.
     */
    fun speak(text: String, surface: Surface = Surface.IN_APP) {
        viewModelScope.launch {
            _speakResult.value = container.spokenMoments.speak(text, surface)
        }
    }

    // --- register panel -------------------------------------------------------------------

    /**
     * The parts nothing else publishes as a flow. Register contents are read from disk on
     * demand and [DefaultProtectionEvaluator.lastCheckedAt] is a plain call, so they are
     * refreshed by hand after every action rather than observed.
     */
    private data class RegisterLocal(
        val entries: List<RegisterEntry> = emptyList(),
        val lastCheckedAt: Instant? = null,
        val checking: Boolean = false,
        val notice: String? = null,
    )

    private val registerLocal = MutableStateFlow(RegisterLocal())

    val register: StateFlow<RegisterPanelState> = combine(
        registerLocal,
        container.protectionEvaluator.state,
        container.ageAssurance.verified,
    ) { local, protection, ageVerified ->
        RegisterPanelState(
            entries = local.entries,
            protection = protection,
            ageVerified = ageVerified,
            lastCheckedAt = local.lastCheckedAt,
            nextDueAt = local.lastCheckedAt?.plus(DefaultProtectionEvaluator.CACHE_TTL),
            checking = local.checking,
            notice = local.notice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), RegisterPanelState())

    // --- why this -------------------------------------------------------------------------

    private val complianceOnly = MutableStateFlow(false)

    /** Checks are appended to the ledger without a flow, so they ride the same manual refresh. */
    private val checks = MutableStateFlow<List<RegisterCheck>>(emptyList())

    val whyThis: StateFlow<WhyThisState> = combine(
        container.ledger.entries,
        complianceOnly,
        checks,
    ) { entries, compliance, checkRows ->
        WhyThisState(
            rows = if (compliance) container.ledger.complianceOnly() else container.ledger.recent(),
            history = entries,
            checks = checkRows,
            complianceOnly = compliance,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), WhyThisState())

    // --- bandit debug ---------------------------------------------------------------------

    private val selectedContext = MutableStateFlow(DEFAULT_CONTEXT)

    private val lastGesture = MutableStateFlow<String?>(null)

    /**
     * Recomputed from [eu.feg.ambient.ambient.engine.ledger.Ledger.arms] rather than polled, so
     * a thumbs press moves the bars on the same frame the counter is written.
     */
    val bandit: StateFlow<BanditState> = combine(
        container.ledger.arms,
        selectedContext,
        lastGesture,
    ) { _, context, gesture ->
        BanditState(context = context, bars = barsFor(context), lastGesture = gesture)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), BanditState())

    init {
        refresh()
    }

    // --- register actions -----------------------------------------------------------------

    /** Re-reads both hand-polled sources. Called after every action and on resume. */
    fun refresh() {
        viewModelScope.launch {
            registerLocal.value = registerLocal.value.copy(
                entries = container.exclusionRegister.entries(),
                lastCheckedAt = container.protectionEvaluator.lastCheckedAt(),
            )
            checks.value = container.ledger.allChecks()
        }
    }

    /** The live demo gesture: put the demo player on the register and watch the app go quiet. */
    fun addDemoPlayer() = registerAction("Added demo-player to the register.") {
        container.exclusionRegister.add(
            playerRef = DefaultProtectionEvaluator.DEMO_PLAYER,
            until = Clock.System.now() + DefaultProtectionEvaluator.SELF_EXCLUSION,
            reason = "Added from the Register Panel",
        )
    }

    fun removeDemoPlayer() = registerAction("Removed demo-player from the register.") {
        container.exclusionRegister.remove(DefaultProtectionEvaluator.DEMO_PLAYER)
    }

    /** Throws the working copy away so the next check re-seeds from the shipped asset. */
    fun resetRegister() = registerAction("Register reset to the seed file.") {
        container.exclusionRegister.reset()
    }

    fun checkNow() = registerAction(null) { true }

    fun setAgeVerified(verified: Boolean) {
        container.ageAssurance.setVerified(verified)
    }

    // --- why this actions -----------------------------------------------------------------

    fun setComplianceOnly(only: Boolean) {
        complianceOnly.value = only
    }

    // --- bandit actions -------------------------------------------------------------------

    fun selectContext(context: String) {
        selectedContext.value = context
    }

    /**
     * Rewards the arm currently on top, through the router rather than through
     * [eu.feg.ambient.ambient.engine.AmbientEngine.onThumbs], because onThumbs needs a ledger
     * row to hang the reward on and this screen is deliberately usable before any moment has
     * fired. The magnitude is the same THUMBS_UP / THUMBS_DOWN either way.
     */
    fun rewardTopArm(up: Boolean) {
        val context = selectedContext.value
        val top = barsFor(context).firstOrNull() ?: return
        val reward = if (up) RewardTable.THUMBS_UP else RewardTable.THUMBS_DOWN
        viewModelScope.launch {
            container.router.reward(top.armId, context, reward)
            lastGesture.value = (if (up) "Rewarded " else "Punished ") + top.armId +
                " by " + reward + "."
        }
    }

    // --- internals ------------------------------------------------------------------------

    /**
     * Every register button has the same shape: do the thing, force a fresh check so the state
     * on screen is the state the evaluator would act on, then re-read.
     */
    private fun registerAction(notice: String?, block: suspend () -> Boolean) {
        viewModelScope.launch {
            registerLocal.value = registerLocal.value.copy(checking = true, notice = null)
            val ok = block()
            container.protectionEvaluator.checkNow()
            registerLocal.value = RegisterLocal(
                entries = container.exclusionRegister.entries(),
                lastCheckedAt = container.protectionEvaluator.lastCheckedAt(),
                checking = false,
                notice = if (ok) notice else "That write failed — the register file is unreadable.",
            )
            checks.value = container.ledger.allChecks()
        }
    }

    /**
     * Top [TOP_ARMS] only. All 49 arms fit on a screen as hairlines nobody can read from the
     * back of a room; six fit as bars a judge can see move.
     */
    private fun barsFor(context: String): List<ArmBar> =
        container.router.snapshot(context)
            .take(TOP_ARMS)
            .map { (armId, mean) ->
                val stat = container.ledger.arm(context, armId)?.let { container.ledger.decayed(it) }
                ArmBar(
                    armId = armId,
                    mean = mean,
                    wins = stat?.wins ?: 0.0,
                    losses = stat?.losses ?: 0.0,
                )
            }

    companion object {
        const val TOP_ARMS = 6

        /** The bar every arm starts at, drawn as a marker so movement is movement from somewhere. */
        val PRE_SEED_MEAN: Double =
            RewardTable.PRIOR_WINS / (RewardTable.PRIOR_WINS + RewardTable.PRIOR_LOSSES)

        val DEFAULT_CONTEXT: String =
            Arms.contextBucket(MomentType.GOAL_ON_SLIP, TimeBucket.EVENING)

        /** The buckets worth showing on stage — one per beat of the scripted demo. */
        val CONTEXTS: List<String> = listOf(
            Arms.contextBucket(MomentType.GOAL_ON_SLIP, TimeBucket.EVENING),
            Arms.contextBucket(MomentType.LEG_DECIDED, TimeBucket.EVENING),
            Arms.contextBucket(MomentType.SLIP_SETTLED, TimeBucket.NIGHT),
            Arms.contextBucket(MomentType.AWAY_DIGEST, TimeBucket.MORNING),
        )

        private const val STOP_TIMEOUT = 5_000L
    }
}
