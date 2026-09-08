package eu.feg.ambient.ui.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.feg.ambient.ambient.ticket.ScannedTicket
import eu.feg.ambient.ambient.ticket.TicketLookupResult
import eu.feg.ambient.core.AppContainer
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

sealed interface ScanState {
    /** The camera is up, or would be if we were allowed to use it. */
    data class Scanning(
        val torchOn: Boolean = false,
        val manualEntry: Boolean = false,
        val permissionDenied: Boolean = false,
        /** The last code the camera reported, for the small "read: …" line under the frame. */
        val lastCode: String? = null,
    ) : ScanState

    data class LookingUp(val code: String) : ScanState

    data class Result(val result: TicketLookupResult, val code: String) : ScanState

    /** The slip is on the lock screen. From here the only place to go is My bets. */
    data class Tracked(val betId: String) : ScanState
}

/**
 * Drives the scanner screen. The camera reports every frame it decodes, many times a second,
 * so the ViewModel is the gate: one lookup per code, and nothing at all while a result is on
 * screen. Keyed on the code's *value* because ML Kit hands us a fresh Barcode object per frame
 * and an identity-keyed cache would let every frame through.
 */
class ScanTicketViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow<ScanState>(ScanState.Scanning())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /** Camera-side settings survive a reset; a user who turned the torch on wants it to stay on. */
    private var scanning = ScanState.Scanning()

    /** Codes that produced a definitive answer this session. Re-reading the same paper is noise. */
    private val handled = mutableSetOf<String>()

    /** Last time each code was sent for lookup, for the 3-second window on retryable codes. */
    private val lastAttempt = mutableMapOf<String, Instant>()

    /** onCode arrives on the camera analyser thread; every decision below is under this lock. */
    private val gate = Any()

    /** From the camera. [format] is the ML Kit name: CODE_128, QR_CODE, OTHER. */
    fun onCode(code: String, format: String) = submit(code.trim(), format, deliberate = false)

    /** From the keyboard. Typed codes skip the de-duplication: the user asked, so we look. */
    fun onManualCode(text: String) {
        val code = text.trim()
        if (code.isEmpty()) return
        submit(code, format = "MANUAL", deliberate = true)
    }

    private fun submit(code: String, format: String, deliberate: Boolean) {
        if (code.isEmpty()) return
        val now = container.clock.now()
        synchronized(gate) {
            if (_state.value !is ScanState.Scanning) return
            if (!deliberate) {
                if (code in handled) return
                val last = lastAttempt[code]
                if (last != null && now - last < DEBOUNCE) return
            }
            lastAttempt[code] = now
            scanning = scanning.copy(lastCode = code, manualEntry = false)
            _state.value = ScanState.LookingUp(code)
        }
        viewModelScope.launch {
            val result = container.ticketLookup.lookup(ScannedTicket(code, format, now))
            synchronized(gate) {
                // NotFound and Invalid stay retryable: a half-read barcode should get a
                // second chance once the hand steadies. The rest are answers.
                if (result !is TicketLookupResult.NotFound && result !is TicketLookupResult.Invalid) {
                    handled += code
                }
                _state.value = ScanState.Result(result, code)
            }
        }
    }

    /** The one action on a Found slip. Goes through the tracker, never straight to a surface. */
    fun trackOnLockScreen() {
        val current = _state.value as? ScanState.Result ?: return
        val found = current.result as? TicketLookupResult.Found ?: return
        viewModelScope.launch {
            val tracked = container.ticketTracker.track(found.bet)
            _state.value = if (tracked) {
                ScanState.Tracked(found.bet.id)
            } else {
                current.copy(result = TicketLookupResult.AlreadyTracked(found.bet.id))
            }
        }
    }

    fun toggleTorch() {
        scanning = scanning.copy(torchOn = !scanning.torchOn)
        if (_state.value is ScanState.Scanning) _state.value = scanning
    }

    /** Also the way out of NotFound: from any state, straight to the keyboard. */
    fun showManualEntry() {
        scanning = scanning.copy(manualEntry = true)
        _state.value = scanning
    }

    fun onPermissionDenied() {
        // No camera means the keyboard is the only way in, so open it rather than explain.
        scanning = scanning.copy(permissionDenied = true, manualEntry = true)
        if (_state.value is ScanState.Scanning) _state.value = scanning
    }

    /** Back to the camera. Torch and permission state carry over; the typed field does not. */
    fun reset() {
        scanning = scanning.copy(manualEntry = scanning.permissionDenied)
        _state.value = scanning
    }

    private companion object {
        val DEBOUNCE = 3.seconds
    }
}
