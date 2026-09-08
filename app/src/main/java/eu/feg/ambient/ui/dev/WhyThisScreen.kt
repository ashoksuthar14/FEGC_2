package eu.feg.ambient.ui.dev

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.ambient.engine.Explain
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.engine.ledger.LedgerEntry
import eu.feg.ambient.ambient.engine.ledger.RegisterCheck
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import kotlinx.datetime.Instant

/**
 * Step 13 Prompt 4. Every decision the engine made, in words, including the decisions to say
 * nothing.
 *
 * The sentences come from [Explain] and nowhere else. A second explainer written for this
 * screen would eventually disagree with the recorded one, and a transparency view that
 * disagrees with the record is worse than no transparency view.
 */
@Composable
fun WhyThisScreen(viewModel: EngineLabViewModel, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    val state by viewModel.whyThis.collectAsStateWithLifecycle()

    // Register checks are appended without a flow, so pull them again on resume.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Why this?",
                    style = MaterialTheme.typography.titleLarge,
                    color = psk.textPrimary,
                )
                Text(
                    text = "Compliance only is the DSA Article 27 transparency view: every " +
                        "register check, every render we blocked, every offer withheld.",
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                )
            }
        }

        item(key = "filter") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PskChip(
                    label = "All",
                    selected = !state.complianceOnly,
                ) { viewModel.setComplianceOnly(false) }
                PskChip(
                    label = "Compliance only (DSA)",
                    selected = state.complianceOnly,
                ) { viewModel.setComplianceOnly(true) }
            }
        }

        item(key = "decisions-header") {
            Text(
                text = "Decisions (" + state.rows.size + ")",
                style = MaterialTheme.typography.titleMedium,
                color = psk.textPrimary,
            )
        }

        if (state.rows.isEmpty()) {
            item(key = "decisions-empty") {
                Text(
                    text = if (state.complianceOnly) {
                        "No compliance-relevant decisions yet."
                    } else {
                        "Nothing decided yet — run the simulator or the Surface Lab first."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textSecondary,
                )
            }
        }

        items(state.rows.size, key = { state.rows[it].id }) { index ->
            val entry = state.rows[index]
            val sentence = Explain.why(entry, state.history)
            DecisionRow(entry = entry, sentence = sentence, onSpeak = { viewModel.speak(sentence) })
        }

        item(key = "checks-header") {
            Text(
                text = "Register checks (" + state.checks.size + ")",
                style = MaterialTheme.typography.titleMedium,
                color = psk.textPrimary,
            )
        }

        if (state.checks.isEmpty()) {
            item(key = "checks-empty") {
                Text(
                    text = "No checks recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textSecondary,
                )
            }
        }

        items(state.checks.size, key = { state.checks[it].id }) { index ->
            RegisterCheckRow(state.checks[index])
        }
    }
}

/** Moment, surface, then the sentence. The badges are the machine record; the sentence is why. */
@Composable
private fun DecisionRow(entry: LedgerEntry, sentence: String, onSpeak: () -> Unit) {
    val psk = LocalPskColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Badge(entry.momentType, psk.brandBlue)
            // Silence is the decision most worth seeing, so it is coloured rather than muted.
            Badge(
                text = entry.surface,
                fill = if (entry.surface == Surface.NOTHING.name) {
                    psk.surfaceRaised
                } else {
                    psk.brandBlueDark
                },
            )
            Text(
                text = Instant.fromEpochMilliseconds(entry.createdAt).hms(),
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            // 48 dp, because a control a screen-reader user reaches for must be reachable by
            // someone whose aim is not perfect either.
            Text(
                text = "🔊",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .size(48.dp)
                    .clip(PskShapes.chip)
                    .clickable(onClickLabel = "Read this decision out loud", onClick = onSpeak)
                    .wrapContentSize(),
            )
        }
        Text(
            text = sentence,
            style = MaterialTheme.typography.bodyMedium,
            color = psk.textPrimary,
        )
        Text(
            text = "arm " + entry.armId + " · score " + format(entry.score) +
                " · " + entry.protection,
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun RegisterCheckRow(check: RegisterCheck) {
    val psk = LocalPskColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = check.registerRef,
                style = MaterialTheme.typography.labelLarge,
                color = psk.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (check.excluded) "EXCLUDED" else "clear",
                style = MaterialTheme.typography.labelLarge,
                color = if (check.excluded) psk.negative else psk.positive,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "checked " + Instant.fromEpochMilliseconds(check.checkedAt).hms() +
                " · valid until " + Instant.fromEpochMilliseconds(check.validUntil).hms(),
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
            fontFamily = FontFamily.Monospace,
        )
        Text(check.reason, style = MaterialTheme.typography.bodySmall, color = psk.textSecondary)
    }
}

@Composable
private fun Badge(text: String, fill: androidx.compose.ui.graphics.Color) {
    val psk = LocalPskColors.current
    Box(
        Modifier
            .clip(PskShapes.chip)
            .background(fill)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = psk.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun format(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)
