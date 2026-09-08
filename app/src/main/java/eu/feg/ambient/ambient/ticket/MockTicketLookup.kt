package eu.feg.ambient.ambient.ticket

import android.content.Context
import eu.feg.ambient.data.model.BetStatus
import eu.feg.ambient.data.repo.BetRepository
import eu.feg.ambient.data.repo.MatchRepository
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

/**
 * Demo implementation of the [TicketLookup] seam: the "retail API" is `assets/mock/tickets.json`.
 *
 * Decision order matters and is deliberate:
 *  1. unknown code          -> [TicketLookupResult.NotFound]
 *  2. leg on unknown match  -> [TicketLookupResult.Invalid]  (nothing downstream could settle it)
 *  3. already tracked       -> [TicketLookupResult.AlreadyTracked]
 *  4. not OPEN              -> [TicketLookupResult.Settled]   (shown, never tracked)
 *  5. otherwise             -> [TicketLookupResult.Found]
 *
 * The already-tracked check lives HERE, before any caller can hand a bet to
 * [BetRepository.track]. Duplicated slips in earlier builds came from checking after the
 * insert; the repository still refuses a second track as a backstop, but the scanner should
 * never get as far as asking.
 *
 * The lookup never writes anything: whether a Found slip gets tracked is the scanner's call.
 */
class MockTicketLookup private constructor(
    private val readJson: () -> String,
    private val matchRepository: MatchRepository,
    private val betRepository: BetRepository,
    private val clock: () -> Instant,
) : TicketLookup {

    /** Production shape: reads the asset the app ships. */
    constructor(
        context: Context,
        matchRepository: MatchRepository,
        betRepository: BetRepository,
        clock: () -> Instant,
    ) : this(
        readJson = {
            context.assets.open("mock/tickets.json").bufferedReader().use { it.readText() }
        },
        matchRepository = matchRepository,
        betRepository = betRepository,
        clock = clock,
    )

    // Same leniency as MockDataSource, so a hand-edited fixture with an extra field does not
    // take the scanner down mid-demo.
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Parsed once, on first scan, so the camera screen opens without touching the disk. */
    private val ticketsByCode: Map<String, MockTicket> by lazy {
        json.decodeFromString<MockTicketFile>(readJson())
            .tickets
            .associateBy { normalise(it.code) }
    }

    override suspend fun lookup(t: ScannedTicket): TicketLookupResult {
        val code = normalise(t.code)
        val ticket = ticketsByCode[code] ?: return TicketLookupResult.NotFound

        // A leg nobody can settle would sit PENDING forever, so refuse the whole slip rather
        // than track a bet that can never resolve.
        val known = matchRepository.matches.value
        if (ticket.legs.any { leg -> known.none { it.id == leg.matchId } }) {
            return TicketLookupResult.Invalid("Unknown match on this ticket")
        }

        val betId = TicketToBet.betIdFor(ticket.code)
        if (betRepository.isTracked(betId)) return TicketLookupResult.AlreadyTracked(betId)

        val bet = TicketToBet.toPlacedBet(ticket, clock())
        return if (ticket.status != BetStatus.OPEN) {
            TicketLookupResult.Settled(bet)
        } else {
            TicketLookupResult.Found(bet)
        }
    }

    companion object {
        /**
         * Barcode readers and humans disagree about case and whitespace; the fixture is the
         * canonical form, so both sides meet in the middle.
         */
        fun normalise(code: String): String = code.trim().uppercase()

        /** Test shape: the fixture as a string, no Android Context required. */
        fun fromJson(
            json: String,
            matchRepository: MatchRepository,
            betRepository: BetRepository,
            clock: () -> Instant,
        ): MockTicketLookup = MockTicketLookup({ json }, matchRepository, betRepository, clock)
    }
}
