package eu.feg.ambient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import eu.feg.ambient.ui.theme.LocalPskColors
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
        setContent {
            PskTheme { PskShellPlaceholder() }
        }
    }
}

/** Step 1 acceptance: blue bar, PSK wordmark, #0E0E10 body. The nav host replaces this at step 10. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PskShellPlaceholder() {
    val psk = LocalPskColors.current
    Scaffold(
        containerColor = psk.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PSK",
                        color = psk.textPrimary,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = psk.brandBlue,
                    titleContentColor = psk.textPrimary,
                ),
            )
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .background(psk.background),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun PskShellPlaceholderPreview() {
    PskTheme { PskShellPlaceholder() }
}
