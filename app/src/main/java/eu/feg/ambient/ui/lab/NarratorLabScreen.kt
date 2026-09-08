package eu.feg.ambient.ui.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.NarratedText
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.Tone
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

/**
 * Step 12D. Both a test bench and a pitch slide: it shows the two engines side by side and,
 * at the bottom, exactly what the model was given.
 */
@Composable
fun NarratorLabScreen(viewModel: NarratorLabViewModel, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text("Narrator Lab", style = MaterialTheme.typography.titleLarge, color = psk.textPrimary)
        }

        item(key = "type") {
            Picker("Moment") {
                items(MomentType.entries.toList(), key = { it.name }) { type ->
                    PskChip(
                        label = type.name,
                        selected = state.type == type,
                        onClick = {
                            viewModel.setType(type)
                            viewModel.loadSample()
                        },
                    )
                }
            }
        }

        item(key = "tone") {
            Picker("Tone") {
                items(Tone.entries.toList(), key = { it.name }) { tone ->
                    PskChip(tone.name, selected = state.tone == tone, onClick = { viewModel.setTone(tone) })
                }
            }
        }

        item(key = "language") {
            Picker("Language") {
                items(NarratorLanguage.entries.toList(), key = { it.name }) { language ->
                    PskChip(
                        label = language.name,
                        selected = state.language == language,
                        onClick = { viewModel.setLanguage(language) },
                    )
                }
            }
        }

        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Load sample facts", onClick = viewModel::loadSample)
                Action("Generate", onClick = viewModel::generate)
            }
        }

        item(key = "results") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ResultCard("Template", state.templateResult, null, Modifier.weight(1f))
                ResultCard("Local Gemma", state.localResult, state.localNotice, Modifier.weight(1f))
                ResultCard("Nano", state.nanoResult, state.nanoNotice, Modifier.weight(1f))
            }
        }

        item(key = "sweep-action") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Action("Run all 64", onClick = viewModel::runAll64)
                state.sweepMedianMs?.let {
                    Text(
                        text = "median " + it + " ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = psk.textSecondary,
                    )
                }
            }
        }

        if (state.sweep.isNotEmpty()) {
            val passed = state.sweep.count { it.passed }
            item(key = "sweep-summary") {
                Text(
                    text = passed.toString() + " / " + state.sweep.size + " passed the guard",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (passed == state.sweep.size) psk.positive else psk.negative,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(state.sweep, key = { it.label }) { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(PskShapes.oddsButton)
                        .background(psk.surface)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (row.passed) "✓" else "✗",
                        color = if (row.passed) psk.positive else psk.negative,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = row.label + (row.reason?.let { " — " + it } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = psk.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = row.latencyMs.toString() + " ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = psk.textSecondary,
                    )
                }
            }
        }

        item(key = "facts") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(PskShapes.card)
                    .background(psk.surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "What the model was given",
                    style = MaterialTheme.typography.titleMedium,
                    color = psk.textPrimary,
                )
                Text(
                    text = "No odds, stake, balance or identity — there is no field for them.",
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                )
                Text(
                    text = state.factsJson,
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PskShapes.oddsButton)
                        .background(psk.surfaceRaised)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun Picker(
    label: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val psk = LocalPskColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = psk.textSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun ResultCard(
    title: String,
    result: NarratedText?,
    notice: String?,
    modifier: Modifier = Modifier,
) {
    val psk = LocalPskColors.current
    Column(
        modifier
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = psk.textSecondary)
        when {
            notice != null -> Text(
                text = notice,
                style = MaterialTheme.typography.bodyMedium,
                color = psk.negative,
            )
            result == null -> Text(
                text = "Not generated yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = psk.textSecondary,
            )
            else -> {
                Text(
                    text = result.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = psk.textPrimary,
                )
                Text(
                    text = result.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .clip(PskShapes.chip)
                            .background(psk.brandBlue)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = result.engine.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = psk.textPrimary,
                        )
                    }
                    Text(
                        text = result.latencyMs.toString() + " ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = psk.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    val psk = LocalPskColors.current
    Box(
        Modifier
            .clip(PskShapes.card)
            .background(psk.brandBlue)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = psk.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
