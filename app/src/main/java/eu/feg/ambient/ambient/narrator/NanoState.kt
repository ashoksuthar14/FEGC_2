package eu.feg.ambient.ambient.narrator

/**
 * What the device says about Gemini Nano.
 *
 * Nano is not shipped with the app — it lives in the OS behind AICore, so this is a probe
 * of the hardware and system image, not of anything we control. On a device without Nano v3
 * there is no remedy, and [Unavailable] carries the raw reason so the diagnostics screen can
 * show it verbatim rather than paraphrasing it away.
 */
sealed interface NanoState {
    data object Checking : NanoState
    data object Available : NanoState
    data object Downloadable : NanoState
    data class Downloading(val percent: Int) : NanoState
    data class Unavailable(val reason: String) : NanoState

    val label: String
        get() = when (this) {
            Checking -> "Checking…"
            Available -> "Available"
            Downloadable -> "Downloadable"
            is Downloading -> "Downloading " + percent + "%"
            is Unavailable -> "Unavailable"
        }
}
