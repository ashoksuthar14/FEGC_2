package eu.feg.ambient.ambient.surfaces

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import eu.feg.ambient.ambient.surfaces.notifications.Channels
import eu.feg.ambient.ambient.surfaces.notifications.NotificationPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * The one implementation of [SurfaceController]. The Surface Lab drives it today and the
 * Moment Engine will drive it in step 13B; neither knows anything about a renderer.
 *
 * Renderer wiring is filled in by the three parallel workstreams — until then each method
 * logs its arguments so the Lab can be built and driven against a controller that answers.
 */
class AndroidSurfaceController(
    private val context: Context,
    private val alertBudget: AlertBudget = AlertBudget(context),
) : SurfaceController {

    private val _diagnostics = MutableStateFlow(SurfaceDiagnostics())
    override val diagnostics: StateFlow<SurfaceDiagnostics> = _diagnostics.asStateFlow()

    /** Set by the Live Update renderer once wired; used by the widget for its Live state. */
    var liveUpdateRenderer: LiveUpdateRendering? = null

    var widgetRenderer: WidgetRendering? = null

    init {
        Channels.ensure(context)
        refreshDiagnostics()
    }

    override suspend fun startLiveUpdate(state: SlipSurfaceState) {
        if (!state.protection.allowsLiveUpdate()) {
            // Enforced here as well as in the renderer: this is the door everything goes
            // through, and the rule has one definition so the two cannot drift apart.
            Log.i(TAG, "startLiveUpdate refused for protection " + state.protection)
            _diagnostics.value = _diagnostics.value.copy(activeLiveUpdateSlipId = null)
            return
        }
        Log.i(TAG, "startLiveUpdate " + state.slipId + " " + state.chipText)
        LiveSlipService.start(context, state.slipId)
        liveUpdateRenderer?.post(state)
        _diagnostics.value = _diagnostics.value.copy(activeLiveUpdateSlipId = state.slipId)
        readBackPromotion()
    }

    override suspend fun updateLiveUpdate(state: SlipSurfaceState) {
        if (!state.protection.allowsLiveUpdate()) {
            endLiveUpdate(state.slipId, settled = false)
            return
        }
        Log.i(TAG, "updateLiveUpdate " + state.slipId + " " + state.chipText)
        liveUpdateRenderer?.post(state)
        readBackPromotion()
    }

    override suspend fun endLiveUpdate(slipId: String, settled: Boolean) {
        Log.i(TAG, "endLiveUpdate " + slipId + " settled=" + settled)
        liveUpdateRenderer?.end(slipId, settled)
        if (!settled) LiveSlipService.stop(context)
        _diagnostics.value = _diagnostics.value.copy(activeLiveUpdateSlipId = null)
    }

    override suspend fun refreshWidget(state: WidgetState) {
        Log.i(TAG, "refreshWidget " + state.javaClass.simpleName)
        widgetRenderer?.render(state)
        _diagnostics.value = _diagnostics.value.copy(lastWidgetUpdate = Clock.System.now())
    }

    override suspend fun postAlert(headline: String, detail: String, deepLink: String): Boolean {
        if (!alertBudget.tryConsume()) {
            Log.i(TAG, "postAlert refused: budget spent")
            refreshBudgetDiagnostics()
            return false
        }
        Log.i(TAG, "postAlert " + headline)
        liveUpdateRenderer?.alert(headline, detail, deepLink)
        refreshBudgetDiagnostics()
        return true
    }

    override suspend fun refreshShortcuts(protection: ProtectionState) {
        Log.i(TAG, "refreshShortcuts for " + protection)
    }

    override fun resetAlertBudget() {
        alertBudget.reset()
        refreshBudgetDiagnostics()
    }

    override fun refreshDiagnostics() {
        _diagnostics.value = _diagnostics.value.copy(
            notificationPermissionGranted = NotificationPermission.isGranted(context),
            canPostPromoted = NotificationPermission.canPostPromoted(context),
            alertsSentToday = alertBudget.sentInLastDay(),
            alertBudget = alertBudget.budget,
        )
    }

    private fun refreshBudgetDiagnostics() {
        _diagnostics.value = _diagnostics.value.copy(
            alertsSentToday = alertBudget.sentInLastDay(),
            alertBudget = alertBudget.budget,
        )
    }

    /**
     * Reads the posted notification back from the system rather than assuming promotion was
     * granted. Deliberately not called synchronously with notify(): the system needs a beat
     * to register it, and reading too early reports a promoted notification as unpromoted.
     */
    private suspend fun readBackPromotion() {
        kotlinx.coroutines.delay(READ_BACK_DELAY_MS)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val posted = runCatching {
            manager.activeNotifications.firstOrNull { it.id == LiveSlipService.NOTIFICATION_ID }
        }.getOrNull()
        val promoted = posted?.notification?.let { (it.flags and FLAG_PROMOTED_ONGOING) != 0 }
        _diagnostics.value = _diagnostics.value.copy(lastPostWasPromoted = promoted)
    }

    /** Interface the Live Update renderer implements, so this class does not depend on it. */
    interface LiveUpdateRendering {
        fun post(state: SlipSurfaceState)
        fun end(slipId: String, settled: Boolean)
        fun alert(headline: String, detail: String, deepLink: String)
    }

    interface WidgetRendering {
        suspend fun render(state: WidgetState)
    }

    private companion object {
        const val TAG = "SurfaceController"

        /** Notification.FLAG_PROMOTED_ONGOING, inlined so this builds on older SDKs. */
        const val FLAG_PROMOTED_ONGOING = 1 shl 21

        /** Long enough for the system to register the post before we read it back. */
        const val READ_BACK_DELAY_MS = 300L
    }
}
