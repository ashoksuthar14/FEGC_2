package eu.feg.ambient.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import eu.feg.ambient.core.AppContainer

object Routes {
    const val SPORT = "sport"
    const val LIVE = "live"
    const val CASINO = "casino"
    const val ARENA = "arena"
    const val MY_BETS = "mybets"
    const val MATCH = "match/{matchId}"
    const val BET_SLIP = "betslip"
    const val RESPONSIBLE_GAMING = "rg"

    /**
     * N7. Two routes, not one tabbed screen: missions are what you have done and rewards are
     * what that is worth, and the loyalty shortcut deep-links straight to the second. A tab
     * index in a deep link is the kind of thing that silently stops matching.
     */
    const val MISSIONS = "missions"
    const val REWARDS = "rewards"
    const val PROMO = "promo"
    const val SCAN_TICKET = "scan"
    /**
     * The game session. It carries the game id because the session is ABOUT a game -- the
     * reality check names it, and a route with no argument would have to guess.
     */
    const val GAME_LOADING = "game/{gameId}"

    fun game(gameId: String): String = "game/" + gameId
    const val AI_DIAGNOSTICS = "ai_diagnostics"
    const val NARRATOR_LAB = "narrator_lab"
    const val SURFACE_LAB = "surface_lab"
    const val REGISTER_PANEL = "register_panel"
    const val WHY_THIS = "why_this"
    const val BANDIT_DEBUG = "bandit_debug"

    fun match(matchId: String) = "match/" + matchId
}

/**
 * One factory for every ViewModel, since there is no DI framework. Each entry is a plain
 * constructor call against the [AppContainer] (PRD section 2.3).
 */
class PskViewModelFactory(
    private val container: AppContainer,
    private val build: (AppContainer) -> ViewModel,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        build(container) as T
}
