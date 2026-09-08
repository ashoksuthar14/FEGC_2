package eu.feg.ambient.ui.diagnostics

import androidx.lifecycle.ViewModel
import eu.feg.ambient.ambient.narrator.NanoState
import eu.feg.ambient.core.AppContainer
import kotlinx.coroutines.flow.StateFlow

class AiDiagnosticsViewModel(private val container: AppContainer) : ViewModel() {

    val nanoState: StateFlow<NanoState> = container.nanoAvailability.state

    val mlKitVersion: String = AppContainer.ML_KIT_GENAI_VERSION

    val activeEngineLabel: String
        get() = when (nanoState.value) {
            is NanoState.Available -> "Ladder: Nano → Template"
            else -> "Template only"
        }

    fun check() = container.nanoAvailability.check()

    fun download() = container.nanoAvailability.download()
}
