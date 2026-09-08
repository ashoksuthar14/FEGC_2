package eu.feg.ambient

import eu.feg.ambient.ambient.ticket.MockTicketFile
import eu.feg.ambient.ambient.ticket.MockTicketLookup
import eu.feg.ambient.ambient.ticket.ScannedTicket
import eu.feg.ambient.ambient.ticket.TicketLookupResult
import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.mock.MockDataSource
import eu.feg.ambient.data.model.BetSource
import eu.feg.ambient.data.repo.BetRepository
import eu.feg.ambient.data.repo.MatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The scanner's happy path against the shipped fixtures: the demo code resolves to a slip
 * that looks exactly like an app-placed one, and scanning the same paper twice is refused
 * before anything could create a second bet.
 */
class TicketLookupTest {

    private val ticketsJson = File("src/main/assets/mock/tickets.json").readText()
    private val source = MockDataSource { path -> File("src/main/assets/" + path).readText() }

    private val scanTime = Instant.parse("2026-09-08T18:00:00Z")

    private val fixedClock = object : MatchClock {
        override val ticks: Flow<Unit> = emptyFlow()
        override fun now(): Instant = scanTime
    }

    private fun scanned(code: String) = ScannedTicket(code = code, format = "CODE_128", scannedAt = scanTime)

    private fun newLookup(scope: CoroutineScope, betRepository: BetRepository): MockTicketLookup =
        MockTicketLookup.fromJson(
            json = ticketsJson,
            matchRepository = MatchRepository(source, fixedClock, scope),
            betRepository = betRepository,
            clock = { scanTime },
        )

    @Test
    fun `demo ticket resolves to a retail bet with the legs from the fixture`() = runTest {
        val betRepository = BetRepository(fixedClock)
        val result = newLookup(backgroundScope, betRepository).lookup(scanned("PSK-DEMO-0001"))

        assertTrue("expected Found, got " + result, result is TicketLookupResult.Found)
        val bet = (result as TicketLookupResult.Found).bet

        // Read the expectation straight from the fixture so the test tracks the JSON, not a
        // copy of it.
        val expected = Json { ignoreUnknownKeys = true }
            .decodeFromString<MockTicketFile>(ticketsJson)
            .tickets.single { it.code == "PSK-DEMO-0001" }

        assertEquals("retail-PSK-DEMO-0001", bet.id)
        assertEquals(BetSource.RETAIL, bet.source)
        assertEquals(expected.legs.size, bet.legs.size)
        assertEquals(expected.legs.map { it.matchId }, bet.legs.map { it.matchId })
        assertEquals(expected.legs.map { it.description }, bet.legs.map { it.description })
        assertEquals(expected.legs.fold(1.0) { acc, l -> acc * l.odds }, bet.totalOdds, 0.0001)
        assertTrue("nothing is tracked by a lookup", betRepository.placedBets.value.isEmpty())
    }

    @Test
    fun `scanning the same paper twice is refused as already tracked`() = runTest {
        val betRepository = BetRepository(fixedClock)
        val lookup = newLookup(backgroundScope, betRepository)

        val first = lookup.lookup(scanned("PSK-DEMO-0001")) as TicketLookupResult.Found
        assertTrue(betRepository.track(first.bet))

        // Lower case and stray whitespace are what a hand-typed code looks like.
        val second = lookup.lookup(scanned("  psk-demo-0001 "))

        assertEquals(TicketLookupResult.AlreadyTracked(first.bet.id), second)
        assertEquals("still exactly one bet", 1, betRepository.placedBets.value.size)
    }
}
