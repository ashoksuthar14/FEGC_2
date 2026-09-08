package eu.feg.ambient.ui.nav

import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.data.model.ArenaTip
import eu.feg.ambient.data.model.CasinoGame
import eu.feg.ambient.data.model.Promo
import eu.feg.ambient.data.model.RecentWin
import eu.feg.ambient.data.model.Selection

/**
 * Promos, casino games and Arena tips never change in Phase 1, so they are read once here
 * rather than through a StateFlow that would only ever emit the same value.
 */
class StaticContent(private val container: AppContainer) {

    val promos: List<Promo> = container.source.promos()
    val casinoGames: List<CasinoGame> = container.source.casinoGames()
    val recentWins: List<RecentWin> = container.source.recentWins()
    val arenaTips: List<ArenaTip> = container.source.arenaTips()

    /**
     * "Copy slip" loads a tipster's picks into the bet slip — the second path into the
     * Phase 2 trigger (PRD section 5.8).
     */
    fun copyTip(tipId: String) {
        val tip = arenaTips.firstOrNull { it.id == tipId } ?: return
        container.betRepository.clear()
        tip.matchIds.forEach { matchId ->
            val match = container.matchRepository.match(matchId) ?: return@forEach
            val market = match.markets.firstOrNull() ?: return@forEach
            val outcome = market.outcomes.minByOrNull { it.odds } ?: return@forEach
            container.betRepository.toggle(
                Selection(match.id, market.id, outcome.id, outcome.odds),
                match.home.name + " - " + match.away.name + " · " + market.name + " · " + outcome.label,
            )
        }
    }
}
