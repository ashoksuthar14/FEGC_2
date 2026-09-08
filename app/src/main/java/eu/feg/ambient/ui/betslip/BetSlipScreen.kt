package eu.feg.ambient.ui.betslip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.core.formatMoney
import eu.feg.ambient.core.formatOdds
import eu.feg.ambient.ui.components.EmptyState
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

private val QUICK_STAKES = listOf(5.0, 10.0, 20.0, 50.0)

/** PRD section 5.4 — the web's right-hand panel, as a sheet and a full screen. */
@Composable
fun BetSlipScreen(
    viewModel: BetSlipViewModel,
    modifier: Modifier = Modifier,
    onPlaced: (String) -> Unit = {},
) {
    val psk = LocalPskColors.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var stakeText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(psk.betslipPanel)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SlipHeader(
            activeTab = state.activeTab,
            onSelectTab = viewModel::selectTab,
            onClear = {
                viewModel.clear()
                stakeText = ""
            },
        )

        if (state.rows.isEmpty()) {
            EmptyState(
                headline = "The ticket is empty.",
                body = "If you want to add a bet to your bet slip, review our odds offer " +
                    "and select the bet of your choice.",
                icon = { CrossedTicketIcon() },
            )
            return@Column
        }

        state.rows.forEach { row ->
            SelectionRow(row = row, onRemove = { viewModel.remove(row.selection) })
        }

        Text(
            text = "Stake",
            style = MaterialTheme.typography.labelMedium,
            color = psk.textSecondary,
        )
        OutlinedTextField(
            value = stakeText,
            onValueChange = {
                stakeText = it.filter { ch -> ch.isDigit() || ch == '.' }
                viewModel.setStake(stakeText.toDoubleOrNull() ?: 0.0)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = psk.textPrimary,
                unfocusedTextColor = psk.textPrimary,
                focusedContainerColor = psk.surfaceRaised,
                unfocusedContainerColor = psk.surfaceRaised,
                focusedBorderColor = psk.brandBlue,
                unfocusedBorderColor = psk.surfaceVariant,
                cursorColor = psk.brandBlue,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QUICK_STAKES.forEach { amount ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(PskShapes.oddsButton)
                        .background(psk.surfaceRaised)
                        .clickable(role = Role.Button) {
                            stakeText = formatOdds(amount)
                            viewModel.setStake(amount)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "€" + amount.toInt(),
                        style = MaterialTheme.typography.labelMedium,
                        color = psk.textPrimary,
                    )
                }
            }
        }

        SummaryLine("Total odds", formatOdds(state.slip.totalOdds))
        SummaryLine("Possible payout", formatMoney(state.slip.possiblePayout), highlight = true)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PskShapes.card)
                .background(if (state.slip.stake > 0) psk.brandBlue else psk.surfaceRaised)
                .clickable(enabled = state.slip.stake > 0, role = Role.Button) { viewModel.place() }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "PLACE BET",
                style = MaterialTheme.typography.titleMedium,
                color = if (state.slip.stake > 0) psk.textPrimary else psk.textSecondary,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    // Placing is a one-shot event, so it is consumed in an effect, never in composition.
    LaunchedEffect(state.placedId) {
        val id = state.placedId ?: return@LaunchedEffect
        stakeText = ""
        onPlaced(id)
        viewModel.dismissSuccess()
    }
}
