package eu.feg.ambient.ambient.engine

import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.Tone
import eu.feg.ambient.ambient.surfaces.ProtectionState
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * The engine's vocabulary.
 *
 * MomentType and Tone are deliberately the narrator's own types rather than copies. Two enums
 * with the same name and meaning is how a codebase starts disagreeing with itself, and the
 * narrator's set is already the one the guard and the templates are written against.
 */

/** Where a decision can land. NOTHING is a real choice, not the absence of one. */
@Serializable
enum class Surface { LIVE_UPDATE, WIDGET, IN_APP, ALERT, NOTHING }

@Serializable
enum class TimeBucket {
    MORNING, DAY, EVENING, NIGHT;

    companion object {
        fun of(hour: Int): TimeBucket = when (hour) {
            in 5..10 -> MORNING
            in 11..16 -> DAY
            in 17..22 -> EVENING
            else -> NIGHT
        }
    }
}

/**
 * How much of this is the user's. The scorer leans on it, and NEITHER should never reach the
 * scorer at all — a moment the user does not own is not built.
 */
@Serializable
/**
 * Whose the moment is.
 *
 * MINE is the odd one out and is meant to be: every other case is a claim about a match --
 * the customer holds a leg on it, or follows a club in it. A badge is about the customer
 * themselves and there is no fixture to own, so ownership is settled by construction rather
 * than by a join. Without it a mission completion resolves to NEITHER and the engine drops
 * its own event as "not yours", which is both wrong and very hard to see.
 */
enum class Ownership { ON_MY_SLIP, DECIDES_A_LEG, FOLLOWED_TEAM, MINE, NEITHER }

/**
 * Moment types with no fixture behind them.
 *
 * A Live Update is built from a SlipSurfaceState -- teams, score, legs -- and these three have
 * none of those, so the card would be blank and would sit on the lock screen in place of a
 * slip that is actually running. The same reasoning stops the engine pushing them as a
 * WidgetState.Live.
 *
 * IT LIVES HERE, next to the types, because it grew by one twice and the second time the new
 * type was added to the moment builder and to the scorer and not to the list -- which is a
 * bug with no symptom except a blank card on somebody's lock screen.
 */
val NOT_ABOUT_A_MATCH: Set<MomentType> = setOf(
    MomentType.MISSION_COMPLETE,
    MomentType.TIER_REACHED,
    MomentType.SESSION_LENGTH,
)

/**
 * What a loyalty event carries, for the two moment types that are not about football.
 *
 * Kept as one nested value rather than five more nullable columns on [MatchEvent], because
 * [MatchEvent] is a football event and five loyalty fields sitting beside `homeScore` would
 * make it look like something else. Counts and names only -- there is nothing here that could
 * hold a stake, a bonus or the cash value of a perk, which is the same rule MomentFacts
 * follows and for the same reason.
 */
/**
 * A gaming session, for the one moment type that is not about a match.
 *
 * Minutes and a name. There is no stake here, no spin count, no balance and no result,
 * because a reality check is about time spent and nothing else -- and a field for any of
 * those would be the first step toward a card that comments on how the session is going.
 */
data class SessionFacts(
    val minutes: Int,
    val gameName: String?,
)

data class LoyaltyFacts(
    val missionTitle: String? = null,
    val badgeName: String? = null,
    val badgeCount: Int? = null,
    val tierName: String? = null,
    val badgesToNextTier: Int? = null,
)

/** What the simulator emits. Raw, before anything decides whether it matters. */
data class MatchEvent(
    val matchId: String,
    val type: MomentType,
    val minute: Int,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: Int,
    val awayScore: Int,
    val scorer: String? = null,
    val period: String? = null,
    /**
     * Minutes until kick-off, for KICKOFF_FOLLOWED and nothing else.
     *
     * Carried on the event rather than recomputed downstream because the moment is raised
     * inside a window, not at an instant: KickoffMomentSource fires anywhere in the minute
     * either side of forty, and "kick off in 40 minutes" read out at 38 is a small lie the
     * customer can check against their own clock.
     */
    val kickoffInMinutes: Int? = null,
    /** Set for MISSION_COMPLETE and TIER_REACHED, null for everything else. */
    val loyalty: LoyaltyFacts? = null,
    /** Set for SESSION_LENGTH. Like [loyalty], it means "this event is not about football". */
    val session: SessionFacts? = null,
    val at: Instant,
)

/** A [MatchEvent] joined with what the user owns. Null means it was never worth building. */
data class Moment(
    val id: String,
    val type: MomentType,
    /** Reused unchanged from step 12 — and it still carries no money. */
    val facts: MomentFacts,
    val slipId: String?,
    val matchId: String,
    val ownership: Ownership,
    val createdAt: Instant,
)

/**
 * What the engine decided and why. Every field here ends up in a ledger row, because
 * "Why this?" has to be answerable for a decision that has already happened.
 */
data class Decision(
    val momentId: String,
    val score: Double,
    val surface: Surface,
    val tone: Tone,
    val armId: String,
    val sampled: Double,
    val protection: ProtectionState,
    /** Plain words, for the transparency sheet. Not a log line. */
    val reason: String,
)
