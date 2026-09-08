package eu.feg.ambient.ui.rg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.core.formatMoney
import eu.feg.ambient.data.model.RiskState
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

/**
 * PRD section 5.6. Small, but it carries the whole compliance story: Phase 2's safety gate
 * reads UserState and nothing else, and the "Simulate state" row drives the Calm Mode demo.
 */
@Composable
fun ResponsibleGamingScreen(
    viewModel: ResponsibleGamingViewModel,
    modifier: Modifier = Modifier,
) {
    val psk = LocalPskColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmPanic by remember { mutableStateOf(false) }
    var confirmExclusion by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text(
                text = "Responsible gaming",
                style = MaterialTheme.typography.titleLarge,
                color = psk.textPrimary,
            )
        }

        item(key = "limits") {
            Card("Limits") {
                LimitBar(
                    "Deposit limit",
                    state.limits.depositUsed,
                    state.limits.depositLimit,
                    money = true,
                )
                LimitBar("Loss limit", state.limits.lossUsed, state.limits.lossLimit, money = true)
                LimitBar(
                    "Time limit",
                    state.limits.timeUsed.toDouble(),
                    state.limits.timeLimit.toDouble(),
                    money = false,
                )
                Spacer(Modifier.height(12.dp))
                DepositLimitPicker(
                    current = state.limits.depositLimit,
                    onPick = { viewModel.setDepositLimit(it) },
                )
            }
        }

        item(key = "panic") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(PskShapes.card)
                    .background(psk.surface)
                    .border(2.dp, psk.negative, PskShapes.card)
                    .clickable(role = Role.Button) { confirmPanic = true }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Pause my account for 48 hours",
                    style = MaterialTheme.typography.titleMedium,
                    color = psk.negative,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (state.panicUntil != null) {
                        "Paused until " + state.panicUntil
                    } else {
                        "Takes effect immediately. No deposits, no bets, no offers."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textSecondary,
                )
            }
        }

        item(key = "exclusion") {
            Card("Self-exclusion") {
                Text(
                    text = "Permanently close this account. This cannot be undone from the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textSecondary,
                )
                PskChip("Start self-exclusion", onClick = { confirmExclusion = true })
            }
        }

        item(key = "reality") {
            Card("Reality check") {
                Text(
                    text = "Remind me how long I have been playing every…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120).forEach { minutes ->
                        PskChip(
                            label = minutes.toString() + " min",
                            selected = state.realityCheckMinutes == minutes,
                            onClick = { viewModel.setRealityCheck(minutes) },
                        )
                    }
                }
            }
        }

        item(key = "quiet") {
            Card("Quiet hours") {
                Text(
                    text = "No notifications during this window.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUIET_RANGES.forEach { (label, range) ->
                        PskChip(
                            label = label,
                            selected = state.quietHours == range,
                            onClick = {
                                viewModel.setQuietHours(if (state.quietHours == range) null else range)
                            },
                        )
                    }
                }
            }
        }

        // Two switches, never one combined — Phase 2 reads them independently.
        item(key = "consents") {
            Card("Notifications") {
                ConsentSwitch(
                    label = "Match updates",
                    description = "Scores and results for bets you have placed.",
                    checked = state.consents.matchUpdates,
                    onChange = viewModel::setMatchUpdates,
                )
                ConsentSwitch(
                    label = "Offers",
                    description = "Promotions, bonuses and prize games.",
                    checked = state.consents.offers,
                    onChange = viewModel::setOffers,
                )
            }
        }

        item(key = "simulate") {
            Card("Simulate state (developer)") {
                RiskState.entries.forEach { risk ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setRiskState(risk) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.riskState == risk,
                            onClick = { viewModel.setRiskState(risk) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = psk.brandBlue,
                                unselectedColor = psk.textSecondary,
                            ),
                        )
                        Text(
                            text = when (risk) {
                                RiskState.NORMAL -> "Normal"
                                RiskState.AT_RISK -> "At-risk"
                                RiskState.SELF_EXCLUDED -> "Self-excluded"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = psk.textPrimary,
                        )
                    }
                }
            }
        }
    }

    if (confirmPanic) {
        ConfirmDialog(
            title = "Pause for 48 hours?",
            body = "You will not be able to deposit or place a bet until the pause ends.",
            confirmLabel = "Pause my account",
            onConfirm = {
                viewModel.panicFor48Hours()
                confirmPanic = false
            },
            onDismiss = { confirmPanic = false },
        )
    }

    if (confirmExclusion) {
        ConfirmDialog(
            title = "Self-exclude?",
            body = "This closes the account permanently. Phase 1 records the state only.",
            confirmLabel = "Self-exclude",
            onConfirm = {
                viewModel.setRiskState(RiskState.SELF_EXCLUDED)
                confirmExclusion = false
            },
            onDismiss = { confirmExclusion = false },
        )
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = psk.textPrimary,
        )
        content()
    }
}

/**
 * The deposit ceiling, and a way to change it.
 *
 * WHY THIS EXISTS AT ALL: this screen drew three limit bars and offered no way to move any of
 * them, so the app's central responsible-gambling control was a picture of a control. Setting
 * a limit is also the one mission in N7 that rewards protecting yourself, and it could not be
 * completed by a customer until something here actually wrote to UserState.
 *
 * Presets rather than a text field, because a number pad is a decision with friction in it and
 * this is the one setting where friction should be on the way UP. Lower amounts come first for
 * the same reason. Nothing is preselected beyond what is already set, and there is no
 * recommendation: an operator nudging a customer toward a higher ceiling is the whole problem.
 */
@Composable
private fun DepositLimitPicker(current: Double, onPick: (Double) -> Unit) {
    val psk = LocalPskColors.current
    Text(
        text = "Your daily deposit limit",
        style = MaterialTheme.typography.labelLarge,
        color = psk.textSecondary,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DEPOSIT_PRESETS.forEach { amount ->
            PskChip(
                label = formatMoney(amount),
                selected = current == amount,
                onClick = { onPick(amount) },
                modifier = Modifier.semantics {
                    contentDescription = "Set the daily deposit limit to " + formatMoney(amount) +
                        if (current == amount) ", currently selected" else ""
                },
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = "You can change this whenever you like. Lowering it takes effect at once.",
        style = MaterialTheme.typography.bodySmall,
        color = psk.textSecondary,
    )
}

/** Deliberately modest and deliberately ascending. See [DepositLimitPicker]. */
private val DEPOSIT_PRESETS = listOf(50.0, 100.0, 200.0, 500.0)

@Composable
private fun LimitBar(label: String, used: Double, limit: Double, money: Boolean) {
    val psk = LocalPskColors.current
    val fraction = if (limit <= 0) 0f else (used / limit).coerceIn(0.0, 1.0).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = psk.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (money) {
                    formatMoney(used) + " / " + formatMoney(limit)
                } else {
                    used.toInt().toString() + " / " + limit.toInt() + " min"
                },
                style = MaterialTheme.typography.labelMedium,
                color = psk.textSecondary,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(PskShapes.chip)
                .background(psk.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(PskShapes.chip)
                    .background(if (fraction > 0.8f) psk.negative else psk.positive),
            )
        }
    }
}

@Composable
private fun ConsentSwitch(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val psk = LocalPskColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = psk.textPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = psk.textPrimary,
                checkedTrackColor = psk.brandBlue,
                uncheckedThumbColor = psk.textSecondary,
                uncheckedTrackColor = psk.surfaceVariant,
                uncheckedBorderColor = psk.surfaceRaised,
            ),
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val psk = LocalPskColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = psk.surface,
        titleContentColor = psk.textPrimary,
        textContentColor = psk.textSecondary,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = psk.negative, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = psk.textSecondary)
            }
        },
    )
}

private val QUIET_RANGES = listOf(
    "23:00 – 08:00" to eu.feg.ambient.data.model.QuietHours(
        kotlinx.datetime.LocalTime(23, 0),
        kotlinx.datetime.LocalTime(8, 0),
    ),
    "00:00 – 07:00" to eu.feg.ambient.data.model.QuietHours(
        kotlinx.datetime.LocalTime(0, 0),
        kotlinx.datetime.LocalTime(7, 0),
    ),
)
