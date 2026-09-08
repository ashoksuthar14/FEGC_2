package eu.feg.ambient.ui.diagnostics

import androidx.lifecycle.ViewModel
import eu.feg.ambient.ambient.narrator.EngineState
import eu.feg.ambient.ambient.narrator.LiteRtEngineHolder
import eu.feg.ambient.ambient.narrator.NanoState
import eu.feg.ambient.core.AppContainer
import kotlinx.coroutines.flow.StateFlow

class AiDiagnosticsViewModel(private val container: AppContainer) : ViewModel() {

    val nanoState: StateFlow<NanoState> = container.nanoAvailability.state

    val mlKitVersion: String = AppContainer.ML_KIT_GENAI_VERSION

    val engineState: StateFlow<EngineState> = container.liteRtEngine.state

    val modelPath: String = container.liteRtEngine.modelFile.absolutePath

    val modelPresent: Boolean get() = container.liteRtEngine.modelFile.exists()

    val modelSizeMb: Long get() = container.liteRtEngine.modelSizeMb

    val liteRtVersion: String = LiteRtEngineHolder.RUNTIME_VERSION

    val backendLabel: String = container.liteRtEngine.backendLabel

    fun reinitialize() = container.liteRtEngine.initialize()

    val localGenerationEnabled: Boolean get() = container.localGenerationEnabled

    /** Off by default: generation terminates the process on this device. */
    fun toggleLocalGeneration() {
        container.localGenerationEnabled = !container.localGenerationEnabled
    }

    val activeEngineLabel: String
        get() = buildList {
            if (nanoState.value is NanoState.Available) add("Nano")
            if (container.localGenerationEnabled && engineState.value is EngineState.Ready) {
                add("Local Gemma")
            }
            add("Template")
        }.joinToString(" → ")

    fun check() = container.nanoAvailability.check()

    fun download() = container.nanoAvailability.download()
}
