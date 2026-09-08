package eu.feg.ambient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
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
        setContent {
            PskTheme {
                AppNavHost(container)
            }
        }
    }
}
