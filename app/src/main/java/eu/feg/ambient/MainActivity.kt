package eu.feg.ambient

import android.Manifest
import android.content.pm.PackageManager
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
import eu.feg.ambient.ambient.surfaces.notifications.NotificationPermission
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
        (application as? AmbientApp)?.container?.awayTracker?.markInteraction()
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
