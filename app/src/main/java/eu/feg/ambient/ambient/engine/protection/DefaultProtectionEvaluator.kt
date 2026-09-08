package eu.feg.ambient.ambient.engine.protection

import eu.feg.ambient.ambient.engine.ProtectionEvaluator
import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.engine.ledger.RegisterCheck
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.surfaces.anyAtOrAbove
import eu.feg.ambient.data.model.RiskState
import eu.feg.ambient.data.repo.UserStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The gate every moment passes before anything else looks at it.
 *
 * FAIL CLOSED. A missing, expired or failed register check is treated exactly as a positive
 * one: BLOCKED. This is the load-bearing claim of the whole pitch — Croatian law does not care
 * that our network was down — and it is the property the unit test pins. Any future change here
 * that turns an unknown answer into NORMAL is a compliance regression, not a bug fix.
 *
 * The 15-minute cache exists so a live match does not hammer the register once per event; it is
 * a cache of a *successful* answer only. Nothing here caches a failure into silence-by-default,
 * and nothing extends a window because a refresh was inconvenient.
 */
class DefaultProtectionEvaluator(
    private val register: ExclusionRegister,
    private val ageAssurance: AgeAssurance,
    private val userStateRepository: UserStateRepository,
    private val ledger: Ledger,
    private val scope: CoroutineScope,
    private val playerRef: String = DEMO_PLAYER,
    private val clock: () -> Instant = { Clock.System.now() },
) : ProtectionEvaluator {

    /**
     * Starts BLOCKED, not NORMAL.
     *
     * Before the first check has come back we have no basis to say anything, and the honest
     * reading of "missing check ⇒ excluded" is that the opening value is the blocked one. The
     * init block fires a check immediately, so this lasts milliseconds in practice.
     */
    private val _state = MutableStateFlow(ProtectionState.BLOCKED)
    val state: StateFlow<ProtectionState> = _state.asStateFlow()

    private val mutex = Mutex()

    private var cached: RegisterResult? = null

    /**
     * Set by [requestSelfExclusion]. In memory only, and deliberately so: it covers the seconds
     * between the user pressing the button and the register acknowledging the filing. The
     * durable half of that protection is the panic window on UserState and the register entry
     * itself, both of which survive process death.
     */
    private var localBlockUntil: Instant? = null

    init {
        scope.launch { checkNow() }
        // Protection has to move the instant its inputs move — step 14E refreshes surfaces off
        // this flow rather than waiting for the next match tick.
        scope.launch { userStateRepository.state.collect { deriveFromCache() } }
        scope.launch { ageAssurance.verified.collect { deriveFromCache() } }
    }

    override suspend fun evaluate(): ProtectionState {
        mutex.withLock { if (!isUsable(cached)) fetch() }
        return deriveFromCache()
    }

    /** Forces a round trip regardless of the cache. The Lab and the panic button use this. */
    suspend fun checkNow(): ProtectionState {
        mutex.withLock { fetch() }
        return deriveFromCache()
    }

    /** For the widget's Protected state: when the register last actually answered. */
    fun lastCheckedAt(): Instant? =
        ledger.lastCheck()?.let { Instant.fromEpochMilliseconds(it.checkedAt) }

    /**
     * The panic button.
     *
     * Files the exclusion AND blocks locally in the same breath. A register write is a network
     * call that may take minutes to propagate, and the person who just pressed this button needs
     * to be protected now — waiting for an acknowledgement to change their state would leave the
     * gap exactly where it must not exist. The returned request is the receipt.
     */
    fun requestSelfExclusion(playerRef: String = this.playerRef): RegisterEntryRequest {
        val now = clock()
        val until = now + SELF_EXCLUSION
        val request = RegisterEntryRequest(
            playerRef = playerRef,
            requestedAt = now,
            until = until,
            reason = "User requested self-exclusion",
        )

        localBlockUntil = until
        userStateRepository.panicFor(until)
        _state.value = ProtectionState.BLOCKED

        scope.launch {
            register.submit(request)
            // Re-read rather than assume: if the filing did not land we want the ledger to show
            // a register that still says nothing, with the local block carrying the protection.
            checkNow()
        }
        return request
    }

    // --- internals ---------------------------------------------------------------------

    private suspend fun fetch() {
        val result = runCatching { register.check(playerRef) }.getOrElse {
            RegisterResult.Failed(
                reason = "Register threw: " + (it.message ?: it.javaClass.simpleName),
                checkedAt = clock(),
            )
        }
        cached = result
        record(result)
    }

    /**
     * One row per query, not per evaluation: a cache hit asks the register nothing, and a row
     * claiming a check that never happened would be the wrong kind of paperwork.
     */
    private fun record(result: RegisterResult) {
        val checkedAt = result.checkedAt.toEpochMilliseconds()
        val row = when (result) {
            is RegisterResult.Checked -> RegisterCheck(
                id = UUID.randomUUID().toString(),
                registerRef = playerRef,
                checkedAt = checkedAt,
                validUntil = result.validUntil.toEpochMilliseconds(),
                excluded = result.excluded,
                reason = result.reason,
            )
            // Recorded as excluded because that is how we treated the user. The reason says why.
            is RegisterResult.Failed -> RegisterCheck(
                id = UUID.randomUUID().toString(),
                registerRef = playerRef,
                checkedAt = checkedAt,
                validUntil = checkedAt,
                excluded = true,
                reason = "Fail-closed, register check failed: " + result.reason,
            )
        }
        ledger.recordCheck(row)
    }

    /** True only for a successful answer still inside both its own and our validity window. */
    private fun isUsable(result: RegisterResult?): Boolean {
        val checked = result as? RegisterResult.Checked ?: return false
        return clock() < expiryOf(checked)
    }

    private fun expiryOf(checked: RegisterResult.Checked): Instant {
        val ours = checked.checkedAt + CACHE_TTL
        return if (checked.validUntil < ours) checked.validUntil else ours
    }

    private suspend fun deriveFromCache(): ProtectionState {
        val next = derive(cached, ageAssurance.isVerified())
        _state.value = next
        return next
    }

    private fun derive(result: RegisterResult?, ageVerified: Boolean): ProtectionState {
        val now = clock()
        val user = userStateRepository.state.value

        val registerBlocks = when (result) {
            null -> true
            is RegisterResult.Failed -> true
            is RegisterResult.Checked -> result.excluded || now >= expiryOf(result)
        }
        val locallyBlocked = localBlockUntil?.let { it > now } == true ||
            user.riskState == RiskState.SELF_EXCLUDED

        return when {
            registerBlocks || locallyBlocked -> ProtectionState.BLOCKED
            !ageVerified -> ProtectionState.UNVERIFIED
            user.riskState == RiskState.AT_RISK -> ProtectionState.CALM
            user.panicUntil?.let { it > now } == true -> ProtectionState.CALM
            user.limits.anyAtOrAbove(CALM_LIMIT_FRACTION) -> ProtectionState.CALM
            else -> ProtectionState.NORMAL
        }
    }

    companion object {
        const val DEMO_PLAYER = "demo-player"

        /** Long enough to survive a match, short enough that a filing lands within the half. */
        val CACHE_TTL = 15.minutes

        /** The panic button is a cool-off, not a permanent exclusion. */
        val SELF_EXCLUSION = 48.hours

        /** 80% of a deposit, loss or time limit is where the tone changes. */
        const val CALM_LIMIT_FRACTION = 0.8
    }
}
