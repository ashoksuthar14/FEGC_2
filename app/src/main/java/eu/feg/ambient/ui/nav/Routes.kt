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
    const val PROMO = "promo"
    const val SCAN_TICKET = "scan"
    const val GAME_LOADING = "game"
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
