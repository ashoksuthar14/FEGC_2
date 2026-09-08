package eu.feg.ambient.ui.betslip

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import eu.feg.ambient.ui.components.IconTouchTarget
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.feg.ambient.core.formatOdds
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

// The slip's header, selection rows and summary lines, split out of BetSlipScreen to
// keep that file under the ~300-line limit (CLAUDE.md rule 6).

@Composable
internal fun SlipHeader(activeTab: Int, onSelectTab: (Int) -> Unit, onClear: () -> Unit) {
    val psk = LocalPskColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (1..4).forEach { tab ->
            Box(
                modifier = Modifier
                    // The 30 dp square from the web stays; the tap area around it is 48 dp.
                    .minimumInteractiveComponentSize()
                    .size(30.dp)
                    .clip(PskShapes.oddsButton)
                    .background(if (tab == activeTab) psk.brandBlue else psk.surfaceRaised)
                    .semantics {
                        contentDescription = "Ticket " + tab
                        selected = tab == activeTab
                    }
                    .clickable(role = Role.Tab) { onSelectTab(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = psk.textPrimary,
                )
            }
        }
        Box(
            Modifier
                .clip(PskShapes.oddsButton)
                .background(psk.surfaceRaised)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text("Plain", style = MaterialTheme.typography.labelMedium, color = psk.textPrimary)
        }
        Box(Modifier.weight(1f))
        IconTouchTarget(contentDescription = "Clear the slip", onClick = onClear, visualSize = 22.dp) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = psk.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
internal fun SelectionRow(row: SlipRow, onRemove: () -> Unit) {
    val psk = LocalPskColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Fixture, market, pick and price as one line; the remove button stays separate.
            .semantics(mergeDescendants = true) { }
            .clip(PskShapes.card)
            .background(psk.surfaceRaised)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.fixture,
                style = MaterialTheme.typography.bodyMedium,
                color = psk.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.market + " · " + row.pick,
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatOdds(row.selection.oddsAtPick),
            style = eu.feg.ambient.ui.theme.PskTextStyles.oddsValue,
            color = psk.textPrimary,
        )
        IconTouchTarget(
            contentDescription = "Remove " + row.pick + " from the slip",
            onClick = onRemove,
            visualSize = 18.dp,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = psk.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun SummaryLine(label: String, value: String, highlight: Boolean = false) {
    val psk = LocalPskColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = psk.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = if (highlight) psk.positive else psk.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The crossed-out ticket from the web's empty slip, drawn rather than shipped as art. */
@Composable
internal fun CrossedTicketIcon() {
    val psk = LocalPskColors.current
    Box(
        Modifier
            .size(64.dp)
            .clip(PskShapes.card)
            .background(psk.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            tint = psk.textSecondary,
            modifier = Modifier.size(30.dp),
        )
    }
}
