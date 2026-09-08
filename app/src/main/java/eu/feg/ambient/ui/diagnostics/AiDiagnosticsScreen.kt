package eu.feg.ambient.ui.diagnostics

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.ambient.narrator.NanoState
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

/**
 * Step 12B's probe, on screen. This is the thing that tells us which narrator path the
 * device is on, so it shows the raw failure reason rather than a friendly summary.
 */
@Composable
fun AiDiagnosticsScreen(
    viewModel: AiDiagnosticsViewModel,
    modifier: Modifier = Modifier,
) {
    val psk = LocalPskColors.current
    val state by viewModel.nanoState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text(
                text = "AI diagnostics",
                style = MaterialTheme.typography.titleLarge,
                color = psk.textPrimary,
            )
        }

        item(key = "device") {
            Card("Device") {
                KeyValue("Model", Build.MODEL)
                KeyValue("Manufacturer", Build.MANUFACTURER)
                KeyValue("Device", Build.DEVICE)
                KeyValue("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
            }
        }

        item(key = "status") {
            Card("Gemini Nano") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = when (state) {
                            is NanoState.Available -> psk.positive
                            is NanoState.Unavailable -> psk.negative
                            else -> psk.jackpotYellow
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }

                (state as? NanoState.Unavailable)?.let { unavailable ->
                    Text(
                        text = "Reason reported by AICore:",
                        style = MaterialTheme.typography.labelSmall,
                        color = psk.textSecondary,
                    )
                    Text(
                        text = unavailable.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = psk.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PskShapes.oddsButton)
                            .background(psk.surfaceRaised)
                            .padding(8.dp),
                    )
                }

                (state as? NanoState.Downloading)?.let { downloading ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(PskShapes.chip)
                            .background(psk.surfaceVariant),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(downloading.percent / 100f)
                                .height(6.dp)
                                .clip(PskShapes.chip)
                                .background(psk.brandBlue),
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action("Check again", enabled = true, onClick = viewModel::check)
                    Action(
                        label = "Download model",
                        enabled = state is NanoState.Downloadable,
                        onClick = viewModel::download,
                    )
                }
            }
        }

        item(key = "narrator") {
            Card("Narrator") {
                KeyValue("Active engine", viewModel.activeEngineLabel)
                KeyValue("ML Kit dependency", viewModel.mlKitVersion)
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    val psk = LocalPskColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = psk.textPrimary,
        )
        content()
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    val psk = LocalPskColors.current
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodyMedium,
            color = psk.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = psk.textPrimary,
        )
    }
}

@Composable
private fun Action(label: String, enabled: Boolean, onClick: () -> Unit) {
    val psk = LocalPskColors.current
    Box(
        Modifier
            .clip(PskShapes.card)
            .background(if (enabled) psk.brandBlue else psk.surfaceRaised)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) psk.textPrimary else psk.textSecondary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
