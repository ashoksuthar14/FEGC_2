package eu.feg.ambient.ambient.surfaces

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import eu.feg.ambient.AmbientApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Drives the surfaces from adb, without bringing an Activity to the front.
 *
 * The Surface Lab is the screen a human uses, but every one of its buttons needs the app in
 * the foreground — which is useless when the thing being tested is what happens on a locked
 * phone, and awkward when someone else is using the device. This receiver exposes the same
 * SurfaceController calls to:
 *
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action start
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action goal
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action minute
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action calm
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action settle
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action end
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action widget --es state Live
 *
 * Debug builds only — it is registered behind a manifest flag and does nothing in release.
 */
class DemoSurfaceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? AmbientApp ?: return
        val controller = app.container.surfaceController
        val action = intent.getStringExtra("action") ?: "start"

        Log.i(TAG, "demo action: " + action)

        scope.launch {
            when (action) {
                "start" -> {
                    slip = DemoSurfaceData.threeLegLive()
                    controller.startLiveUpdate(slip)
                    controller.refreshWidget(WidgetState.Live(slip))
                }

                "minute" -> {
                    slip = slip.copy(
                        minute = (slip.minute ?: 61) + 1,
                        minutesRemaining = ((slip.minutesRemaining ?: 29) - 1).coerceAtLeast(0),
                    )
                    controller.updateLiveUpdate(slip)
                    controller.refreshWidget(WidgetState.Live(slip))
                }

                "goal" -> {
                    slip = slip.copy(homeScore = (slip.homeScore ?: 0) + 1)
                    controller.updateLiveUpdate(slip)
                    controller.refreshWidget(WidgetState.Live(slip))
                }

                "winleg" -> {
                    val legs = slip.legs.toMutableList()
                    val index = legs.indexOfFirst { it.status == LegStatus.PENDING }
                    if (index >= 0) legs[index] = legs[index].copy(status = LegStatus.WON)
                    slip = slip.copy(legs = legs)
                    controller.updateLiveUpdate(slip)
                    controller.refreshWidget(WidgetState.Live(slip))
                }

                "calm" -> {
                    slip = slip.copy(protection = ProtectionState.CALM)
                    controller.updateLiveUpdate(slip)
                    controller.refreshWidget(WidgetState.Live(slip))
                }

                "normal" -> {
                    slip = slip.copy(protection = ProtectionState.NORMAL)
                    controller.updateLiveUpdate(slip)
                    controller.refreshWidget(WidgetState.Live(slip))
                }

                "blocked" -> {
                    // Must remove the surface entirely, not merely restyle it.
                    slip = slip.copy(protection = ProtectionState.BLOCKED)
                    controller.updateLiveUpdate(slip)
                    controller.refreshWidget(
                        WidgetState.Protected(ProtectionState.BLOCKED, lastRegisterCheck = null),
                    )
                }

                "settle" -> {
                    slip = slip.copy(settled = true)
                    controller.endLiveUpdate(slip.slipId, settled = true)
                    controller.refreshWidget(WidgetState.Settled(slip))
                }

                "end" -> controller.endLiveUpdate(slip.slipId, settled = false)

                "alert" -> {
                    val sent = controller.postAlert(
                        "Your slip is settled",
                        "Two of three came in. Nothing left running.",
                        "mybets",
                    )
                    Log.i(TAG, "alert sent=" + sent)
                }

                "widget" -> {
                    val state = when (intent.getStringExtra("state")) {
                        "PreMatch" -> DemoSurfaceData.preMatchWidget
                        "Settled" -> WidgetState.Settled(DemoSurfaceData.twoLegSettled())
                        "Idle" -> WidgetState.Idle("Varaždin – Istria 1961", null)
                        "Protected" -> WidgetState.Protected(ProtectionState.CALM, null)
                        "Digest" -> WidgetState.Digest(
                            "While you were away",
                            "2 legs won, Betis drew, Sparta kick off in 40 min.",
                            app.container.clock.now(),
                        )
                        else -> WidgetState.Live(slip)
                    }
                    controller.refreshWidget(state)
                }

                else -> Log.w(TAG, "unknown demo action: " + action)
            }
            Log.i(TAG, "after " + action + ": chip=" + slip.chipText + " protection=" + slip.protection)
        }
    }

    private companion object {
        const val TAG = "DemoSurface"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Mutable across broadcasts so a sequence of actions builds on the last one. */
        @Volatile
        var slip: SlipSurfaceState = DemoSurfaceData.threeLegLive()
    }
}
