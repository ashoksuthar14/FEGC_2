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
enum class Ownership { ON_MY_SLIP, DECIDES_A_LEG, FOLLOWED_TEAM, NEITHER }

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
