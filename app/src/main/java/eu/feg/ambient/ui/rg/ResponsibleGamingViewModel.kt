package eu.feg.ambient.ui.rg

import androidx.lifecycle.ViewModel
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.data.model.QuietHours
import eu.feg.ambient.data.model.RiskState
import eu.feg.ambient.data.model.UserState
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration.Companion.hours

/** Every write to UserState goes through here — Phase 2's safety gate has one source. */
class ResponsibleGamingViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<UserState> = container.userStateRepository.state

    fun setRiskState(risk: RiskState) = container.userStateRepository.setRiskState(risk)

    fun setRealityCheck(minutes: Int) = container.userStateRepository.setRealityCheck(minutes)

    /**
     * Sets the customer's own deposit ceiling.
     *
     * This screen drew three limit bars and had no way to change any of them, so the limits
     * were decoration: UserStateRepository.setLimits had no caller anywhere in the app. A
     * responsible-gambling tool that cannot be operated is worse than an absent one, and the
     * N7 mission that rewards setting a limit could never have completed without this.
     *
     * The used figure is left alone. Lowering a ceiling below what has already gone through
     * is the customer's business and is exactly what someone cutting down would do; rewriting
     * their history to make the bar look tidy would be us editing their record.
     */
    fun setDepositLimit(amount: Double) {
        val current = container.userStateRepository.state.value.limits
        container.userStateRepository.setLimits(current.copy(depositLimit = amount))
    }

    fun setQuietHours(quietHours: QuietHours?) =
        container.userStateRepository.setQuietHours(quietHours)

    fun setMatchUpdates(enabled: Boolean) {
        container.userStateRepository.setConsents(
            state.value.consents.copy(matchUpdates = enabled),
        )
    }

    fun setOffers(enabled: Boolean) {
        container.userStateRepository.setConsents(state.value.consents.copy(offers = enabled))
    }

    fun panicFor48Hours() {
        container.userStateRepository.panicFor(container.clock.now() + 48.hours)
    }
}
