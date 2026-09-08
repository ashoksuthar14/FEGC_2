package eu.feg.ambient

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import eu.feg.ambient.ambient.surfaces.DemoStage
import eu.feg.ambient.ambient.surfaces.live.AlertFeedbackReceiver
import eu.feg.ambient.ambient.surfaces.notifications.NotificationPermission
import eu.feg.ambient.ambient.surfaces.widget.WidgetRefresher
import kotlinx.coroutines.launch
import eu.feg.ambient.ui.nav.AppNavHost
import eu.feg.ambient.ui.theme.PskColors
import eu.feg.ambient.ui.theme.PskTheme

class MainActivity : ComponentActivity() {
    /**
     * Opening the app ends the away-period.
     *
     * onResume, not onCreate: a customer returning to an app that was still in memory has
     * caught up just as surely as one who cold-started it, and only onResume sees both.
     */
    override fun onResume() {
        super.onResume()
        val container = (application as? AmbientApp)?.container
        container?.awayTracker?.markInteraction()

        // WE ARE PAID TO STAY QUIET, and this is where that gets paid. Opening the app of
        // your own accord within the hour, having not been prompted, is evidence that the
        // silences we chose were the right call -- so every unrewarded NOTHING row from the
        // last hour gets its reward and the arms that chose silence go up. Without this the
        // router could only ever learn from the times it spoke, which biases it toward
        // speaking. It is idempotent: rows carry their reward once.
        container?.let { c ->
            lifecycleScope.launch {
                runCatching { c.engine.onAppOpenedUnprompted() }
            }
        }
    }

    /**
     * A tap on an alert is a reward, and the alert brought its own ledger row's id.
     *
     * onCreate rather than onNewIntent because the notification's PendingIntent carries
     * FLAG_ACTIVITY_CLEAR_TOP against a standard launch mode, which recreates the activity --
     * so this is the callback that sees the extra. RewardTable grades it by age: a tap within
     * half an hour is worth more than one on a card found much later.
     */
    private fun recordAlertTap(intent: Intent?) {
        val entryId = intent?.getStringExtra(AlertFeedbackReceiver.EXTRA_ENTRY_ID) ?: return
        val container = (application as? AmbientApp)?.container ?: return
        lifecycleScope.launch {
            runCatching { container.engine.onTapped(entryId) }
        }
    }

    /**
     * Leaving the app starts the away period, in debug builds only.
     *
     * The catch-up card is real in every other respect -- real ledger rows, real ranking,
     * real narration -- but it will not build until the customer has been gone an hour, and
     * a demo cannot wait an hour. Compressing that one gap is the whole of the fiction; see
     * [DemoStage.armAwayPeriod]. The widgets are redrawn straight after, because the launcher
     * would otherwise show yesterday's card until something else happened to poke it.
     */
    override fun onStop() {
        super.onStop()
        if (!BuildConfig.DEBUG) return
        val container = (application as? AmbientApp)?.container ?: return
        DemoStage.armAwayPeriod(container)
        lifecycleScope.launch { WidgetRefresher.refreshAll(applicationContext) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val psk = PskColors()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(psk.brandBlue.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(psk.background.toArgb()),
        )
        super.onCreate(savedInstanceState)

        val container = (application as AmbientApp).container
        val startRoute = intent?.getStringExtra("route")
        recordAlertTap(intent)

        // Asked when a live slip is placed, never at launch: a denied notification
        // permission is close to permanent, and the ask lands best right after someone has
        // shown they want exactly this (step 14, decision 2).
        val requestNotifications = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted -> container.surfaceCoordinator.onNotificationPermissionResult(granted) }

        lifecycleScope.launch {
            container.surfaceCoordinator.permissionWanted.collect { slipId ->
                if (slipId == null) return@collect
                if (NotificationPermission.isGranted(this@MainActivity)) {
                    container.surfaceCoordinator.onNotificationPermissionResult(true)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        setContent {
            PskTheme {
                AppNavHost(container, startRoute = startRoute)
            }
        }
    }

}
