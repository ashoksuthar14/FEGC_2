package eu.feg.ambient.ambient.narrator

import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks AICore whether Gemini Nano exists on this device.
 *
 * Nano ships in the system image, not in the APK, so there is nothing to bundle and nothing
 * to side-load. On hardware without Nano v3 this reports Unavailable with the raw AICore
 * message (commonly a PREPARATION_ERROR / FEATURE_NOT_FOUND string) and there is no remedy —
 * which is exactly why the template narrator exists and is the default.
 *
 * This class never throws. Every failure becomes [NanoState.Unavailable].
 */
class NanoAvailabilityChecker(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<NanoState>(NanoState.Checking)
    val state: StateFlow<NanoState> = _state.asStateFlow()

    fun check() {
        scope.launch {
            _state.value = NanoState.Checking
            _state.value = withContext(Dispatchers.IO) { readStatus() }
        }
    }

    private suspend fun readStatus(): NanoState = try {
        val model = Generation.getClient()
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> NanoState.Available
            FeatureStatus.DOWNLOADABLE -> NanoState.Downloadable
            FeatureStatus.DOWNLOADING -> NanoState.Downloading(0)
            FeatureStatus.UNAVAILABLE -> NanoState.Unavailable("FeatureStatus.UNAVAILABLE")
            else -> NanoState.Unavailable("unrecognised FeatureStatus")
        }
    } catch (t: Throwable) {
        Log.d(TAG, "Nano status check failed", t)
        NanoState.Unavailable(describe(t))
    }

    fun download() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val model = Generation.getClient()
                    model.download().collect { status ->
                        _state.value = mapDownload(status)
                    }
                }
                // The stream ending without an explicit completion still warrants a re-read.
                _state.value = withContext(Dispatchers.IO) { readStatus() }
            } catch (t: Throwable) {
                Log.d(TAG, "Nano download failed", t)
                _state.value = NanoState.Unavailable(describe(t))
            }
        }
    }

    /**
     * DownloadStatus is a sealed hierarchy whose member names differ across beta releases,
     * so it is matched by simple name. Getting this wrong costs a progress bar, never a crash.
     */
    private fun mapDownload(status: Any): NanoState {
        val name = status.javaClass.simpleName
        return when {
            name.contains("Completed", ignoreCase = true) -> NanoState.Available
            name.contains("Failed", ignoreCase = true) -> NanoState.Unavailable("download failed: " + status)
            name.contains("Progress", ignoreCase = true) -> NanoState.Downloading(percentOf(status))
            name.contains("Started", ignoreCase = true) -> NanoState.Downloading(0)
            else -> NanoState.Downloading(0)
        }
    }

    /** Progress arrives as bytes without a reliable total, so this is best-effort only. */
    private fun percentOf(status: Any): Int = runCatching {
        val downloaded = status.javaClass.methods
            .firstOrNull { it.name.contains("otalBytesDownloaded", ignoreCase = false) && it.parameterCount == 0 }
            ?.invoke(status) as? Long ?: return@runCatching 0
        val total = status.javaClass.methods
            .firstOrNull { it.name.contains("otalBytesToDownload", ignoreCase = false) && it.parameterCount == 0 }
            ?.invoke(status) as? Long
        if (total == null || total <= 0L) 0 else ((downloaded * 100) / total).toInt().coerceIn(0, 100)
    }.getOrDefault(0)

    private fun describe(t: Throwable): String =
        t.javaClass.simpleName + ": " + (t.message ?: "no message")

    private companion object {
        const val TAG = "NanoAvailability"
    }
}
