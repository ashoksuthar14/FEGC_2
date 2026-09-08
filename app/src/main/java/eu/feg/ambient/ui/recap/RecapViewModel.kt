package eu.feg.ambient.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.recap.Recap
import eu.feg.ambient.ambient.recap.RecapPeriod
import eu.feg.ambient.ambient.surfaces.SpeakResult
import eu.feg.ambient.core.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * The two recaps the inbox can show, rebuilt whenever anything they depend on changes.
 *
 * Three inputs, not one: the ledger (the counts), the protection state (BLOCKED and
 * UNVERIFIED withdraw the card) and the user state (the followed club). Combining them here
 * rather than polling means a customer who follows a club in Settings sees the recap change
 * when they come back to the inbox, with no refresh call anyone has to remember.
 *
 * Null is a real value in both flows: it means "no recap worth a card", and the screen
 * renders nothing rather than an empty card. See DefaultRecapBuilder for the threshold.
 */
class RecapViewModel(private val container: AppContainer) : ViewModel() {

    val month: StateFlow<Recap?> = recapFlow(RecapPeriod.MONTH)
    val season: StateFlow<Recap?> = recapFlow(RecapPeriod.SEASON)

    /** What the last speaker tap did, so the screen can say "phone is on silent" out loud. */
    private val _speakResult = MutableStateFlow<SpeakResult?>(null)
    val speakResult: StateFlow<SpeakResult?> = _speakResult

    /**
     * Through SpokenMoments, never the speaker directly, so the tap is a ledger row and a
     * reward like every other speak. Protection is re-checked inside; the card being on
     * screen is not proof it is still allowed to talk.
     */
    fun speak(recap: Recap) {
        viewModelScope.launch {
            _speakResult.value = container.spokenMoments.speak(
                recap.spokenText,
                Surface.IN_APP,
                momentType = SPOKEN_MOMENT_TYPE,
            )
        }
    }

    private fun recapFlow(period: RecapPeriod): StateFlow<Recap?> =
        combine(
            container.ledger.entries,
            container.protectionEvaluator.state,
            container.userStateRepository.state,
        ) { _, _, _ -> Unit }
            .map { container.recapBuilder.build(period, Clock.System.now()) }
            // WhileSubscribed rather than Eagerly: the build reads the whole ledger and asks
            // the narrator, and there is no reason to do that while the inbox is not open.
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private companion object {
        /**
         * Not a MomentType: the digest ranker tiers only the eight real types, so a recap
         * read aloud can never be summarised back into a later digest as a moment.
         */
        const val SPOKEN_MOMENT_TYPE = "RECAP"
    }
}
