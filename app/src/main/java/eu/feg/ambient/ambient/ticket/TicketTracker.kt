package eu.feg.ambient.ambient.ticket

import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.engine.ledger.LedgerEntry
import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.model.PlacedBet
import eu.feg.ambient.data.repo.BetRepository

/**
 * "Track on my lock screen", and the whole of what it does.
 *
 * It hands the slip to [BetRepository.track], which adds it and fires the same BetPlaced the
 * app's own slip fires. From there the coordinator asks for notification permission with the
 * rationale it always uses, posts the Live Update, refreshes the widget, and the engine takes
 * every later moment -- none of which knows the slip was paper. This class deliberately holds
 * no reference to a SurfaceController: the day it needs one, the scanned slip has stopped
 * being an ordinary bet and something upstream is wrong.
 *
 * The ledger row is the only retail-specific thing here. It exists so the compliance view and
 * the metrics can tell a shop slip from an app one; nothing renders it.
 */
class TicketTracker(
    private val betRepository: BetRepository,
    private val ledger: Ledger,
    private val clock: MatchClock,
) {
    /** True if the slip is now tracked; false if it already was. Never two slips for one code. */
    suspend fun track(bet: PlacedBet, code: String = bet.id.removePrefix("retail-")): Boolean {
        val added = betRepository.track(bet)
        if (!added) return false
        val now = clock.now().toEpochMilliseconds()
        val first = bet.legs.firstOrNull()
        ledger.record(
            LedgerEntry(
                id = "ret-" + now + "-" + bet.id,
                momentId = bet.id,
                momentType = "TICKET_SCANNED",
                contextBucket = "RETAIL",
                protection = "NORMAL",
                score = 0.0,
                surface = Surface.IN_APP.name,
                tone = "-",
                armId = "-",
                sampled = 0.0,
                reason = "Retail slip " + code + " scanned and tracked; source RETAIL.",
                shownAt = now,
                tappedAt = now,
                matchId = first?.matchId,
                createdAt = now,
            ),
        )
        return true
    }
}
