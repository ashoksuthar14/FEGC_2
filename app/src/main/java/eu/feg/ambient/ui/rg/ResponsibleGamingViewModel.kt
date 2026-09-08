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
