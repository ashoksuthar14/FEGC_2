package eu.feg.ambient

import eu.feg.ambient.data.mock.MockDataSource
import eu.feg.ambient.data.model.MatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Reads the same files the app ships, straight off disk, so the test fails when the JSON
 * and the model drift apart (PRD section 9, step 3).
 */
class MockDataTest {

    private val source = MockDataSource { path ->
        File("src/main/assets/" + path).readText()
    }

    @Test
    fun `matches json parses with enough fixtures`() {
        val matches = source.matches()
        assertTrue(
            "expected 45+ matches, got " + matches.size,
            matches.size >= 45,
        )
    }

    @Test
    fun `at least ten matches are live`() {
        val live = source.matches().count { it.state == MatchState.LIVE }
        assertTrue("expected 10+ live matches, got " + live, live >= 10)
    }

    @Test
    fun `live matches carry a score, a minute and a period`() {
        source.matches().filter { it.state == MatchState.LIVE }.forEach { match ->
            assertTrue(match.id + " has no score", match.homeScore != null && match.awayScore != null)
            assertTrue(match.id + " has no minute", match.minute != null)
            assertTrue(match.id + " has no period", match.period != null)
        }
    }

    @Test
    fun `odds stay inside the observed range`() {
        val odds = source.matches().flatMap { it.markets }.flatMap { it.outcomes }.map { it.odds }
        assertTrue("no odds parsed", odds.isNotEmpty())
        assertTrue("odds below 1.02: " + odds.min(), odds.min() >= 1.02)
        assertTrue("odds above 90.00: " + odds.max(), odds.max() <= 90.0)
    }

    @Test
    fun `every match points at a league that exists`() {
        val leagueIds = source.leagues().map { it.id }.toSet()
        val orphans = source.matches().filterNot { it.leagueId in leagueIds }.map { it.id }
        assertEquals("matches with no league", emptyList<String>(), orphans)
    }

    @Test
    fun `every match has a Match market with three outcomes`() {
        source.matches().forEach { match ->
            val main = match.markets.firstOrNull { it.name == "Match" }
            assertTrue(match.id + " has no Match market", main != null)
            assertEquals(match.id + " Match market size", 3, main!!.outcomes.size)
        }
    }

    @Test
    fun `sports carry the counts seen in the screenshots`() {
        val sports = source.sports().associateBy { it.name }
        assertEquals(314, sports.getValue("Football").totalCount)
        assertEquals(22, sports.getValue("Basketball").totalCount)
        assertEquals(50, sports.getValue("Football").liveCount)
    }
}
