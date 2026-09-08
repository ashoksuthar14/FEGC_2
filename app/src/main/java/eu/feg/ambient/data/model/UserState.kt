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
    /**
     * Thirty-five, not the hundred and twenty it was.
     *
     * The protection evaluator calls Calm Mode at 80% of any limit, and the deposit picker
     * offers 50 as its lowest ceiling -- so with 120 already used, a customer setting a
     * sensible limit was instantly over it and the app calmed down on them mid-demo. That
     * behaviour is right and stays; what was wrong was seeding the account so that every
     * careful choice tripped it. At 35 each preset is a real choice, and the rule is still
     * demonstrable by picking 50.
     */
    val depositUsed: Double = 35.0,
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
    /**
     * The club whose colour themes the OS surfaces. Cosmetic and nothing else: no offer, no
     * limit and no protection decision reads this field, and none may start to. It lives on
     * UserState only because that is where the app already keeps one persisted preference.
     */
    val myClubId: String? = null,
    /**
     * Every club the customer follows, by id.
     *
     * SEPARATE FROM [myClubId] ON PURPOSE. myClubId is the one club whose colours theme the
     * OS surfaces -- a cosmetic choice, and there can only be one of it. Following is a
     * different question: which clubs' kick-offs are worth telling this customer about, and
     * the answer is a set. Collapsing the two would mean picking a second club silently
     * repainted the widget, which is not what "follow" means to anyone.
     *
     * Like myClubId it is cosmetic and informational: no offer, no limit and no protection
     * decision reads this field, and none may start to.
     */
    val followedClubIds: Set<String> = emptySet(),
)
