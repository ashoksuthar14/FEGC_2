package eu.feg.ambient.ambient.surfaces

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import eu.feg.ambient.ambient.surfaces.widget.WidgetRefresher
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

    var shortcutRenderer: ShortcutRendering? = null

    /**
     * The last state the widget was actually drawn from.
     *
     * Needed because a repaint — a club change, say — must redraw what is on the home screen
     * rather than decide afresh what ought to be there. Deciding afresh is how a live card
     * got replaced by an idle one the moment somebody picked a club. Null after a process
     * restart, where the caller falls back to computing a state.
     */
    @Volatile
    private var lastWidgetState: WidgetState? = null

    override fun currentWidgetState(): WidgetState? = lastWidgetState

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

    override suspend fun refreshWidgets() {
        // No stored state is touched: each widget re-reads what it draws from. WidgetRenderer
        // is not involved, because there is nothing new for it to write down.
        WidgetRefresher.refreshAll(context)
    }

    override suspend fun refreshWidget(state: WidgetState) {
        Log.i(TAG, "refreshWidget " + state.javaClass.simpleName)
        lastWidgetState = state
        widgetRenderer?.render(state)
        _diagnostics.value = _diagnostics.value.copy(lastWidgetUpdate = Clock.System.now())
    }

    override suspend fun postAlert(
        headline: String,
        detail: String,
        deepLink: String,
        entryId: String?,
        essential: Boolean,
    ): Boolean {
        // An essential message still SPENDS from the budget where it can, so a reality check
        // followed by a goal alert does not add up to two interruptions; it simply is not
        // blocked when the budget has already gone.
        if (essential) alertBudget.tryConsume()
        if (!essential && !alertBudget.tryConsume()) {
            Log.i(TAG, "postAlert refused: budget spent")
            refreshBudgetDiagnostics()
            return false
        }
        Log.i(TAG, "postAlert " + headline)
        liveUpdateRenderer?.alert(headline, detail, deepLink, entryId)
        refreshBudgetDiagnostics()
        return true
    }

    override suspend fun refreshShortcuts(protection: ProtectionState) {
        Log.i(TAG, "refreshShortcuts for " + protection)
        shortcutRenderer?.refresh(protection)
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
        fun alert(headline: String, detail: String, deepLink: String, entryId: String?)
    }

    interface WidgetRendering {
        suspend fun render(state: WidgetState)
    }

    /** N6: the launcher's long-press menu, led by the customer's club. */
    interface ShortcutRendering {
        fun refresh(protection: ProtectionState)
    }

    private companion object {
        const val TAG = "SurfaceController"

        /** Notification.FLAG_PROMOTED_ONGOING, inlined so this builds on older SDKs. */
        const val FLAG_PROMOTED_ONGOING = 1 shl 21

        /** Long enough for the system to register the post before we read it back. */
        const val READ_BACK_DELAY_MS = 300L
    }
}
