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
import eu.feg.ambient.ambient.surfaces.PromotionSpike
import eu.feg.ambient.ui.nav.AppNavHost
import eu.feg.ambient.ui.theme.PskColors
import eu.feg.ambient.ui.theme.PskTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val psk = PskColors()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(psk.brandBlue.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(psk.background.toArgb()),
        )
        super.onCreate(savedInstanceState)

        val container = (application as AmbientApp).container
        val startRoute = intent?.getStringExtra("route")

        // Step 14.0c spike, debug only: find out whether this device promotes an ongoing
        // notification before the rest of step 14 is built around the assumption that it does.
        if (BuildConfig.DEBUG) runPromotionSpike()
        setContent {
            PskTheme {
                AppNavHost(container, startRoute = startRoute)
            }
        }
    }

    private fun runPromotionSpike() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            PromotionSpike.run(this)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            PromotionSpike.run(this)
        } else {
            // The spike asks at launch on purpose. Real bet placement asks at the right
            // moment instead — see section 14.0 decision 2.
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                PromotionSpike.run(this)
            }.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
