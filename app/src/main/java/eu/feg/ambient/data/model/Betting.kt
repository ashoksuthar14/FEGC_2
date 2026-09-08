package eu.feg.ambient.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** PRD section 6 — the bet slip and its aftermath. */

@Serializable
enum class SlipType { PLAIN, SYSTEM }

@Serializable
enum class BetStatus { OPEN, WON, LOST, VOID }

/**
 * Where a slip was placed. RETAIL is a paper slip from a branch, scanned in; everything
 * downstream treats the two identically, and this field exists so the ledger and the metrics
 * can tell them apart -- not so any surface can.
 */
@Serializable
enum class BetSource { APP, RETAIL }

@Serializable
enum class LegStatus { PENDING, WON, LOST, VOID }

@Serializable
data class Selection(
    val matchId: String,
    val marketId: String,
    val outcomeId: String,
    val oddsAtPick: Double,
)

@Serializable
data class BetSlip(
    val id: String,
    val selections: List<Selection> = emptyList(),
    val stake: Double = 0.0,
    val type: SlipType = SlipType.PLAIN,
) {
    val totalOdds: Double get() = selections.fold(1.0) { acc, s -> acc * s.oddsAtPick }
    val possiblePayout: Double get() = stake * totalOdds
}

/**
 * One line of a placed bet. Phase 2 turns these into the progress segments of the
 * lock-screen Live Update, which is why [matchId] and [status] are both carried here.
 */
@Serializable
data class Leg(
    val matchId: String,
    val description: String,
    val odds: Double,
    val status: LegStatus = LegStatus.PENDING,
)

@Serializable
data class PlacedBet(
    val id: String,
    val legs: List<Leg>,
    val stake: Double,
    val totalOdds: Double,
    val placedAt: Instant,
    val status: BetStatus = BetStatus.OPEN,
    val source: BetSource = BetSource.APP,
) {
    val possiblePayout: Double get() = stake * totalOdds
    val settledLegs: Int get() = legs.count { it.status != LegStatus.PENDING }
}
