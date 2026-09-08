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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.ambient.engine.protection.DefaultProtectionEvaluator
import eu.feg.ambient.ambient.engine.protection.RegisterEntry
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Step 13 Prompt 4. The register, and the protection state that follows from it.
 *
 * This screen replaces the old three-state radio button, and the replacement is the point:
 * nothing in the app may set [ProtectionState] any more. You put a player on the register and
 * the evaluator derives BLOCKED from that, exactly as it would in production. A demo that can
 * dial protection directly proves nothing about a system that cannot.
 */
@Composable
fun RegisterPanelScreen(viewModel: EngineLabViewModel, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    val state by viewModel.register.collectAsStateWithLifecycle()

    // The register file is edited by other paths too — the panic button, the engine's own
    // checks — so re-read whenever this screen comes back into view.
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
            Text(
                text = "Register Panel",
                style = MaterialTheme.typography.titleLarge,
                color = psk.textPrimary,
            )
        }

        item(key = "state") {
            DevCard("Protection state") {
                Text(
                    text = state.protection.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = protectionColor(state.protection),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Derived, never set. The register and the age check decide this.",
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                )
                DevRow("Last check", state.lastCheckedAt?.stamp() ?: "never")
                DevRow("Next due (15 min cache)", state.nextDueAt?.stamp() ?: "now")
                DevAction(
                    label = if (state.checking) "Checking…" else "Check now",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = viewModel::checkNow,
                )
                state.notice?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (it.contains("failed")) psk.negative else psk.positive,
                    )
                }
            }
        }

        item(key = "age") {
            DevCard("Age assurance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PskChip(
                        label = "Verified",
                        selected = state.ageVerified,
                    ) { viewModel.setAgeVerified(true) }
                    PskChip(
                        label = "Not verified",
                        selected = !state.ageVerified,
                    ) { viewModel.setAgeVerified(false) }
                }
                Text(
                    text = "Unverified means UNVERIFIED protection, which means no surfaces at all.",
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                )
            }
        }

        item(key = "actions") {
            DevCard("Synthetic register") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DevAction(
                        label = "Add " + DefaultProtectionEvaluator.DEMO_PLAYER,
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::addDemoPlayer,
                    )
                    DevAction(
                        label = "Remove " + DefaultProtectionEvaluator.DEMO_PLAYER,
                        modifier = Modifier.weight(1f),
                        onClick = viewModel::removeDemoPlayer,
                    )
                }
                DevAction(
                    label = "Reset register to seed",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = viewModel::resetRegister,
                )
                Text(
                    text = if (state.demoPlayerListed) {
                        DefaultProtectionEvaluator.DEMO_PLAYER + " is on the register."
                    } else {
                        DefaultProtectionEvaluator.DEMO_PLAYER + " is not on the register."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.demoPlayerListed) psk.negative else psk.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        item(key = "entries-header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Entries (" + state.entries.size + ")",
                    style = MaterialTheme.typography.titleMedium,
                    color = psk.textPrimary,
                )
                if (state.entries.isEmpty()) {
                    Text(
                        text = "The register is empty. Nobody is excluded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = psk.textSecondary,
                    )
                }
            }
        }

        items(state.entries.size, key = { state.entries[it].playerRef }) { index ->
            RegisterEntryRow(state.entries[index])
        }
    }
}

@Composable
private fun RegisterEntryRow(entry: RegisterEntry) {
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
                text = entry.playerRef,
                style = MaterialTheme.typography.titleMedium,
                color = psk.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (entry.excluded) "EXCLUDED" else "clear",
                style = MaterialTheme.typography.labelLarge,
                color = if (entry.excluded) psk.negative else psk.positive,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "valid until " + entry.validUntil.stamp(),
            style = MaterialTheme.typography.labelMedium,
            color = psk.textSecondary,
            fontFamily = FontFamily.Monospace,
        )
        Text(entry.reason, style = MaterialTheme.typography.bodySmall, color = psk.textSecondary)
    }
}

// --- shared pieces --------------------------------------------------------------------------
// Internal rather than private because "Why this?" and the Bandit Debug screen are the same
// three cards in a different order. Surface Lab's equivalents are file-private and so cannot
// be reused, and deliberately named apart from them to avoid a same-package clash.

@Composable
internal fun DevCard(title: String, content: @Composable () -> Unit) {
    val psk = LocalPskColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = psk.textPrimary)
        content()
    }
}

@Composable
internal fun DevRow(label: String, value: String) {
    val psk = LocalPskColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = psk.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = psk.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
internal fun DevAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val psk = LocalPskColors.current
    Box(
        modifier
            .clip(PskShapes.oddsButton)
            .background(psk.brandBlue)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = psk.textPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
internal fun protectionColor(state: ProtectionState) = with(LocalPskColors.current) {
    when (state) {
        ProtectionState.NORMAL -> positive
        ProtectionState.CALM, ProtectionState.UNVERIFIED -> jackpotYellow
        ProtectionState.BLOCKED -> negative
    }
}

/** "08.09. 21:04" — date included, because a register entry can be valid for days. */
internal fun Instant.stamp(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    return pad2(local.dayOfMonth) + "." + pad2(local.monthNumber) + ". " +
        pad2(local.hour) + ":" + pad2(local.minute)
}

/** "21:04:33" — for ledger rows, where the second is what tells two decisions apart. */
internal fun Instant.hms(): String {
    val time = toLocalDateTime(TimeZone.currentSystemDefault()).time
    return pad2(time.hour) + ":" + pad2(time.minute) + ":" + pad2(time.second)
}

internal fun pad2(value: Int): String = if (value < 10) "0" + value else value.toString()
