package eu.feg.ambient.ambient.narrator

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where the LiteRT-LM engine is in its life cycle. */
sealed interface EngineState {
    data class NotPresent(val path: String) : EngineState
    data object Initializing : EngineState
    data class Ready(val initMs: Long) : EngineState
    data class Failed(val reason: String) : EngineState

    val label: String
        get() = when (this) {
            is NotPresent -> "Model file not on device"
            Initializing -> "Initializing…"
            is Ready -> "Ready (" + initMs + " ms)"
            is Failed -> "Failed"
        }
}

/**
 * Owns exactly one [Engine] for the whole process.
 *
 * Unlike Gemini Nano, nothing here asks the OS for permission: we ship the runtime and the
 * weights, so the only requirements are a CPU and the file being present. Building an engine
 * is expensive and [Engine.initialize] can take several seconds, hence the singleton and the
 * cache directory.
 */
class LiteRtEngineHolder(
    context: Context,
    private val scope: CoroutineScope,
    modelFileName: String = DEFAULT_MODEL,
) {
    private val appContext = context.applicationContext

    /**
     * External files dir needs no permission. The directory is created by the app rather
     * than by `adb shell mkdir`: a shell-created directory inside the app's own sandbox is
     * owned by shell, and the app then gets Permission denied traversing it. In production
     * this is the directory the model is downloaded into, so the app owns it either way.
     */
    val modelDir: File = File(appContext.getExternalFilesDir(null), "models").apply { mkdirs() }

    val modelFile: File = File(modelDir, modelFileName)

    private val _state = MutableStateFlow<EngineState>(EngineState.NotPresent(modelFile.absolutePath))
    val state: StateFlow<EngineState> = _state.asStateFlow()

    @Volatile
    var engine: Engine? = null
        private set

    val modelSizeMb: Long
        get() = if (modelFile.exists()) modelFile.length() / (1024 * 1024) else 0

    val backendLabel: String = "CPU"

    /** Every transition is logged at INFO so the state can be read without the screen. */
    private fun publish(next: EngineState) {
        _state.value = next
        Log.i(TAG, "state -> " + next.label)
    }

    fun initialize() {
        scope.launch {
            Log.i(TAG, "model path: " + modelFile.absolutePath)
            Log.i(TAG, "exists=" + modelFile.exists() + " canRead=" + modelFile.canRead() +
                " bytes=" + (if (modelFile.exists()) modelFile.length() else 0))

            if (!modelFile.exists()) {
                publish(EngineState.NotPresent(modelFile.absolutePath))
                return@launch
            }

            publish(EngineState.Initializing)
            publish(withContext(Dispatchers.IO) {
                try {
                    engine?.runCatching { close() }
                    val started = System.nanoTime()
                    val created = Engine(
                        EngineConfig(
                            modelPath = modelFile.absolutePath,
                            // GPU for Gemma 3 270M on Android is still work in progress
                            // upstream; CPU is the dependable choice for a demo.
                            backend = Backend.CPU(),
                            cacheDir = appContext.cacheDir.absolutePath,
                        ),
                    )
                    created.initialize()
                    engine = created
                    EngineState.Ready((System.nanoTime() - started) / 1_000_000)
                } catch (t: Throwable) {
                    Log.w(TAG, "LiteRT-LM initialise failed", t)
                    EngineState.Failed(t.javaClass.simpleName + ": " + (t.message ?: "no message"))
                }
            })
        }
    }

    fun close() {
        runCatching { engine?.close() }
        engine = null
    }

    companion object {
        const val DEFAULT_MODEL = "gemma3-270m-it-q8.litertlm"
        const val RUNTIME_VERSION = "com.google.ai.edge.litertlm:litertlm-android:0.16.1"
        private const val TAG = "LiteRtEngine"
    }
}
