package eu.feg.ambient.ui.loyalty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.feg.ambient.ambient.loyalty.Badge
import eu.feg.ambient.ambient.loyalty.LoyaltyRepository.RedeemResult
import eu.feg.ambient.ambient.loyalty.LoyaltyState
import eu.feg.ambient.ambient.loyalty.Mission
import eu.feg.ambient.ambient.loyalty.Perk
import eu.feg.ambient.core.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the last redeem attempt did, kept with the perk it was for so a stale result cannot be shown on another card. */
data class RedeemOutcome(val perkId: String, val result: RedeemResult)

/**
 * Missions and Rewards share one ViewModel because they share one snapshot.
 *
 * Nothing is computed here. Tier, progress, spendable badges and every redemption rule live
 * in the model and the repository, where the compliance tests can reach them; a screen that
 * worked out its own "3 more badges" would be a second place for the arithmetic to drift.
 */
class LoyaltyViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<LoyaltyState> = container.loyaltyRepository.state

    /** The catalogue is fixed for the process, so a plain list rather than a flow. */
    val perks: List<Perk> = container.loyaltyRepository.perks

    private val _redeemOutcome = MutableStateFlow<RedeemOutcome?>(null)
    val redeemOutcome: StateFlow<RedeemOutcome?> = _redeemOutcome

    /**
     * Off the main thread because the store writes its file synchronously. The result arrives
     * on the flow as one value with no intermediate "working" state: there is nothing to wait
     * for that a spinner could honestly represent, and a pause before a code appears reads as
     * a reveal.
     */
    fun redeem(perkId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { container.loyaltyRepository.redeem(perkId) }
            _redeemOutcome.value = RedeemOutcome(perkId, result)
        }
    }

    /** Called when the sheet closes, so reopening a card never starts on an old result. */
    fun clearRedeemOutcome() {
        _redeemOutcome.value = null
    }

    /**
     * The badge a mission will award, before it has been earned.
     *
     * The snapshot only carries earned badges, and an active mission card wants to show what
     * it is for; the catalogue is the one place the icon key becomes a drawable.
     */
    fun badgeFor(mission: Mission): Badge? =
        container.loyaltyCatalogue.mission(mission.id)?.let(container.loyaltyCatalogue::badgeFor)
}
