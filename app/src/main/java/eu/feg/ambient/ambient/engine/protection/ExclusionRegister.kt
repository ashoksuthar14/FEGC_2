package eu.feg.ambient.ambient.engine.protection

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * The national self-exclusion register, as far as this app is concerned.
 *
 * WHY AN INTERFACE: in production this is a network call to a state-operated service with an
 * SLA, an outage history and a legal weight none of our own code has. The rest of the app must
 * never learn that difference — [DefaultProtectionEvaluator] is written against a register that
 * can be slow, stale or down, so the synthetic one we demo with exercises exactly the paths the
 * real one will.
 *
 * READ-ONLY BY DESIGN. [check] is the only operation the app performs in normal use. A register
 * an app can quietly write to is a register a bug can quietly empty, so the single write door
 * ([submit]) exists solely for the panic button and takes a request the user themselves made.
 */
interface ExclusionRegister {

    suspend fun check(playerRef: String): RegisterResult

    /**
     * Files a self-exclusion. Returns true when the register accepted it.
     *
     * The caller must not wait for this before protecting the user: a filing can take minutes
     * to propagate, and the protection has to hold from the moment the button is pressed.
     */
    suspend fun submit(request: RegisterEntryRequest): Boolean
}

/**
 * The register's answer, or its absence.
 *
 * [Failed] is a first-class variant rather than an exception because "we could not ask" is a
 * real answer with a real consequence — it blocks — and an exception is too easy to swallow at
 * a call site that only wanted a boolean.
 */
sealed interface RegisterResult {

    /** When we asked. The evaluator's cache window is measured from here, not from now. */
    val checkedAt: Instant

    val reason: String

    data class Checked(
        val excluded: Boolean,
        /**
         * How long this answer may be relied upon.
         *
         * For an active exclusion this is when the exclusion itself lapses; for a clean player
         * it is how long the register vouches for that. Either way, past it we know nothing,
         * and knowing nothing blocks.
         */
        val validUntil: Instant,
        override val reason: String,
        override val checkedAt: Instant,
    ) : RegisterResult

    data class Failed(
        override val reason: String,
        override val checkedAt: Instant,
    ) : RegisterResult
}

/**
 * A self-exclusion the user asked for, on its way to the register.
 *
 * Kept as a value rather than a fire-and-forget call so the panic button can hand it to the
 * ledger, the UI and the register from one place, and so a failed submission is still evidence
 * that the user asked.
 */
@Serializable
data class RegisterEntryRequest(
    val playerRef: String,
    val requestedAt: Instant,
    val until: Instant,
    /** Plain words; this is what an audit reads. */
    val reason: String,
)
