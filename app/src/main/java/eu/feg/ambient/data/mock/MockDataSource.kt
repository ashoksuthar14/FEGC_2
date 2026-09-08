package eu.feg.ambient.data.mock

import android.content.Context
import eu.feg.ambient.data.model.ArenaTip
import eu.feg.ambient.data.model.CasinoGame
import eu.feg.ambient.data.model.League
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.Promo
import eu.feg.ambient.data.model.RecentWin
import eu.feg.ambient.data.model.Sport
import kotlinx.serialization.json.Json

/**
 * Reads the fixture files out of `assets/mock`. There is no network in Phase 1 (PRD
 * section 2.3), so this is the only place data enters the app.
 */
class MockDataSource(private val assets: AssetReader) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun sports(): List<Sport> = json.decodeFromString(assets.read("mock/sports.json"))

    fun leagues(): List<League> = json.decodeFromString(assets.read("mock/leagues.json"))

    fun matches(): List<Match> = json.decodeFromString(assets.read("mock/matches.json"))

    fun promos(): List<Promo> = json.decodeFromString(assets.read("mock/promos.json"))

    fun casinoGames(): List<CasinoGame> = json.decodeFromString(assets.read("mock/casino_games.json"))

    fun recentWins(): List<RecentWin> = json.decodeFromString(assets.read("mock/recent_wins.json"))

    fun arenaTips(): List<ArenaTip> = json.decodeFromString(assets.read("mock/arena_tips.json"))

    /** Indirection so unit tests can feed the same JSON without an Android Context. */
    fun interface AssetReader {
        fun read(path: String): String
    }

    companion object {
        fun fromContext(context: Context): MockDataSource =
            MockDataSource { path -> context.assets.open(path).bufferedReader().use { it.readText() } }
    }
}
