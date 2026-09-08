package eu.feg.ambient.ambient.ticket

import eu.feg.ambient.data.model.BetSource
import eu.feg.ambient.data.model.BetStatus
import eu.feg.ambient.data.model.Leg
import eu.feg.ambient.data.model.LegStatus
import eu.feg.ambient.data.model.PlacedBet
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Wire shape of one slip in `assets/mock/tickets.json`. Stake and odds live here because they
 * are part of the bet model, not because any surface shows them (none does).
 *
 * This is a separate type from [PlacedBet] on purpose: the retail API will not speak our
 * domain model, and keeping the mapping in one place ([TicketToBet]) is what lets the demo
 * swap the JSON for FEG's real endpoint without touching anything downstream.
 */
@Serializable
data class MockTicket(
    val code: String,
    val legs: List<MockTicketLeg>,
    val stake: Double,
    val status: BetStatus = BetStatus.OPEN,
    /** When the branch printed the slip. Absent on a slip we only know from the scan. */
    val placedAt: Instant? = null,
)

@Serializable
data class MockTicketLeg(
    val matchId: String,
    val description: String,
    val odds: Double,
    val status: LegStatus = LegStatus.PENDING,
)

/** The top-level object of `tickets.json`. */
@Serializable
data class MockTicketFile(val tickets: List<MockTicket> = emptyList())

/**
 * A scanned slip becomes an ordinary [PlacedBet] so that the engine, the narrator and every
 * renderer work on it unchanged -- none of them has a retail branch, and none of them should.
 * The only traces of paper are the [PlacedBet.id] prefix and [BetSource.RETAIL], which exist
 * for the ledger and the metrics, not for any surface.
 */
object TicketToBet {

    /** Stable id per paper code, so scanning the same slip twice resolves to the same bet. */
    fun betIdFor(code: String): String = "retail-" + code

    /**
     * @param now the scan time; used as [PlacedBet.placedAt] only when the ticket carries no
     *   timestamp of its own, because "when was this placed" should mean the branch's clock
     *   whenever the branch told us.
     */
    fun toPlacedBet(ticket: MockTicket, now: Instant): PlacedBet = PlacedBet(
        id = betIdFor(ticket.code),
        legs = ticket.legs.map { leg ->
            Leg(
                matchId = leg.matchId,
                description = leg.description,
                odds = leg.odds,
                status = leg.status,
            )
        },
        stake = ticket.stake,
        // Same fold BetSlip.totalOdds uses, so an app slip and a paper slip with the same
        // legs report the same price.
        totalOdds = ticket.legs.fold(1.0) { acc, leg -> acc * leg.odds },
        placedAt = ticket.placedAt ?: now,
        status = ticket.status,
        source = BetSource.RETAIL,
    )
}
