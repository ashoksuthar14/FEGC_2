package eu.feg.ambient.ambient.ticket

import eu.feg.ambient.data.model.PlacedBet
import kotlinx.datetime.Instant

/** What the barcode carried, and nothing more. The camera's job ends here. */
data class ScannedTicket(
    val code: String,
    /** CODE_128, QR_CODE, ... as ML Kit names them. */
    val format: String,
    val scannedAt: Instant,
)

sealed interface TicketLookupResult {
    /** A slip we can track. It is already an ordinary [PlacedBet]; nothing downstream knows. */
    data class Found(val bet: PlacedBet) : TicketLookupResult
    data object NotFound : TicketLookupResult
    data class AlreadyTracked(val betId: String) : TicketLookupResult
    /** Finished before it was scanned: shown, never tracked. */
    data class Settled(val bet: PlacedBet) : TicketLookupResult
    data class Invalid(val reason: String) : TicketLookupResult
}

/**
 * INTEGRATION SEAM. Turns a scanned code into a slip.
 *
 * PSK already scans paper slips from the branch and already owns the lookup behind it; in
 * production this interface is FEG's retail ticket API, and this is the one place the app
 * touches it. For the demo it reads assets/mock/tickets.json. It sits beside MomentSource and
 * UserStateProvider in the pitch's list of seams for the same reason: what comes back is an
 * ordinary [PlacedBet], so the engine, the narrator and every renderer work on a retail slip
 * without knowing it was ever paper.
 */
interface TicketLookup {
    suspend fun lookup(t: ScannedTicket): TicketLookupResult
}
