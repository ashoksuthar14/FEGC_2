package eu.feg.ambient.ui.mybets

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.core.formatMoney
import eu.feg.ambient.core.formatOdds
import eu.feg.ambient.data.model.LegStatus
import eu.feg.ambient.ui.components.EmptyState
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

/** PRD section 5.5 — Open / Settled, with a live progress line per slip. */
@Composable
fun MyBetsScreen(
    viewModel: MyBetsViewModel,
    modifier: Modifier = Modifier,
    onScanTicket: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "My bets",
                    style = MaterialTheme.typography.titleLarge,
                    color = psk.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                PskChip("Scan a branch ticket", onClick = onScanTicket)
            }
        }

        item(key = "tabs") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PskChip(
                    "Open",
                    selected = !state.showSettled,
                    count = state.openBets.size,
                    onClick = { viewModel.showSettled(false) },
                )
                PskChip(
                    "Settled",
                    selected = state.showSettled,
                    count = state.settledBets.size,
                    onClick = { viewModel.showSettled(true) },
                )
            }
        }

        val bets = if (state.showSettled) state.settledBets else state.openBets
        if (bets.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    headline = if (state.showSettled) "Nothing settled yet." else "No open bets.",
                    body = "Place a bet from the offer and it will appear here.",
                )
            }
        } else {
            items(bets, key = { it.bet.id }) { row ->
                BetCard(row, liveOnLockScreen = row.bet.id == state.liveOnLockScreenSlipId)
            }
        }
    }
}

@Composable
private fun BetCard(row: BetRow, liveOnLockScreen: Boolean = false) {
    val psk = LocalPskColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surfaceRaised)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (liveOnLockScreen) {
            // The app says what is actually on the phone, rather than leaving the user to guess.
            Text(
                text = "Live on lock screen",
                style = MaterialTheme.typography.labelSmall,
                color = psk.positive,
                fontWeight = FontWeight.SemiBold,
            )
        }

        row.bet.legs.forEach { leg ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LegStatusIcon(leg.status)
                Text(
                    text = leg.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatOdds(leg.odds),
                    style = eu.feg.ambient.ui.theme.PskTextStyles.oddsValue,
                    color = psk.textPrimary,
                )
            }
        }

        // "2/3 · 61'" — the live progress line. Phase 2 turns this into lock-screen segments.
        row.liveProgress?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = psk.positive,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            SummaryCell("Stake", formatMoney(row.bet.stake), Modifier.weight(1f))
            SummaryCell("Total odds", formatOdds(row.bet.totalOdds), Modifier.weight(1f))
            SummaryCell(
                "Possible payout",
                formatMoney(row.bet.possiblePayout),
                Modifier.weight(1f),
                highlight = true,
            )
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
    val psk = LocalPskColors.current
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) psk.positive else psk.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LegStatusIcon(status: LegStatus) {
    val psk = LocalPskColors.current
    when (status) {
        LegStatus.PENDING -> Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(psk.textSecondary),
        )
        LegStatus.WON -> Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Won",
            tint = psk.positive,
            modifier = Modifier.size(16.dp),
        )
        LegStatus.LOST -> Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Lost",
            tint = psk.negative,
            modifier = Modifier.size(16.dp),
        )
        LegStatus.VOID -> Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(psk.surfaceVariant),
        )
    }
}

/** Phase 1 stub — ML Kit barcode scanning arrives in Phase 2. */
@Composable
fun ScanTicketScreen(modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Ticket scanning — Phase 2",
            style = MaterialTheme.typography.titleMedium,
            color = psk.textSecondary,
        )
    }
}
