package eu.feg.ambient.data.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * PRD section 6. Phase 2's safety gate is a pure function of this type and reads nothing
 * else, so the shape here is load-bearing — do not fold fields together.
 */

@Serializable
enum class RiskState { NORMAL, AT_RISK, SELF_EXCLUDED }

/** The PRD calls this `Market` (CZ/SK/PL/RO/HR); renamed to avoid colliding with betting markets. */
@Serializable
enum class MarketCountry { CZ, SK, PL, RO, HR }

@Serializable
data class Limits(
    val depositLimit: Double = 500.0,
    val depositUsed: Double = 120.0,
    val lossLimit: Double = 200.0,
    val lossUsed: Double = 45.0,
    /** Minutes. */
    val timeLimit: Int = 180,
    val timeUsed: Int = 62,
)

/** Two switches, never one. Phase 2 checks them independently. */
@Serializable
data class Consents(
    val matchUpdates: Boolean = true,
    val offers: Boolean = false,
)

@Serializable
data class QuietHours(
    val start: LocalTime,
    val end: LocalTime,
)

@Serializable
data class UserState(
    val riskState: RiskState = RiskState.NORMAL,
    val panicUntil: Instant? = null,
    val limits: Limits = Limits(),
    val consents: Consents = Consents(),
    /** PRD types this as ClosedRange<LocalTime>, which has no serializer; same two bounds. */
    val quietHours: QuietHours? = null,
    val realityCheckMinutes: Int = 60,
    val market: MarketCountry = MarketCountry.HR,
    val balance: Double = 250.00,
)
