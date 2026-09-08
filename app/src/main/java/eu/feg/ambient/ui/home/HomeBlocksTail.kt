package eu.feg.ambient.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.core.formatMoney
import eu.feg.ambient.data.model.ArenaTip
import eu.feg.ambient.ui.components.OddsButton
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import eu.feg.ambient.ui.theme.PskTheme

// Blocks 10 to 12 of the Home column. Blocks 1 to 6 stay in HomeBlocks.kt; the file was
// past the ~300-line limit (CLAUDE.md rule 6) and this is the natural seam.

/** Block 10 — a novelty market with two outcomes and the share-of-stakes split. */
@Composable
fun SpecialBetsCard(modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "SPECIAL",
                style = MaterialTheme.typography.labelSmall,
                color = psk.jackpotYellow,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "ENDS IN 26 DAYS",
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
            )
        }
        Text(
            text = "Will the release of GTA VI be delayed from November 19, 2026?",
            style = MaterialTheme.typography.titleMedium,
            color = psk.textPrimary,
        )
        Row(Modifier.fillMaxWidth()) {
            ShareOfStakes("69.3% SHARE OF STAKES", psk.positive, 0.693f)
            ShareOfStakes("30.7% SHARE OF STAKES", psk.negative, 0.307f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OddsButton("That", "5.20", Modifier.weight(1f))
            OddsButton("Not", "1.12", Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowScope.ShareOfStakes(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    weight: Float,
) {
    val psk = LocalPskColors.current
    Column(Modifier.weight(weight)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = psk.textPrimary,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.35f))
                .padding(6.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(color),
        )
    }
}

/** Block 11 — the first few Arena rows, as a taster for the full screen. */
@Composable
fun ArenaPreviewRow(tip: ArenaTip, modifier: Modifier = Modifier, onCopy: () -> Unit = {}) {
    val psk = LocalPskColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surfaceRaised)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .clearAndSetSemantics { }
                .size(28.dp)
                .clip(CircleShape)
                .background(psk.brandBlue),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tip.username.take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = psk.textPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = tip.username,
                style = MaterialTheme.typography.bodyMedium,
                color = psk.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tip.inspirationLabel,
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = tip.eventCount.toString() + " events",
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
            )
            Text(
                text = formatMoney(tip.possiblePayment),
                style = MaterialTheme.typography.labelMedium,
                color = psk.positive,
                fontWeight = FontWeight.SemiBold,
            )
        }
        PskChip("Copy", onClick = onCopy)
    }
}

/** Block 12 — the compliance footer. Responsible gaming and self-exclusion must be here. */
@Composable
fun HomeFooter(modifier: Modifier = Modifier, onLink: (String) -> Unit = {}) {
    val psk = LocalPskColors.current
    val links = listOf(
        "About us", "Game rules", "Responsible gaming", "Self-exclusion form",
        "User Safety Guide", "Personal data protection", "Branches", "Help",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        links.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { link ->
                    Text(
                        text = link,
                        style = MaterialTheme.typography.labelMedium,
                        color = psk.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            // Two rows of links 10 dp apart cannot borrow touch area from
                            // each other, so each takes its 48 dp outright.
                            .minimumInteractiveComponentSize()
                            .clickable(role = Role.Button) { onLink(link) },
                    )
                }
            }
        }
        Text(
            text = "Payment methods: PSK Terminal · SEPA · Revolut · aircash · Visa · Mastercard · " +
                "Diners · Apple Pay · Skrill · paysafecard",
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
        )
        Text(
            text = "© 2026 Hattrick-PSK d.o.o. All rights reserved. Authorised organiser of games of " +
                "chance under approval CLASS: UP/I-461-04/25-02/493, REGISTRATION NUMBER: " +
                "513-07-21-01-12-2, Zagreb. Participation is permitted only for persons over the " +
                "age of 18. Games of chance can be addictive. Play responsibly!",
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10, heightDp = 700)
@Composable
private fun HomeBlocksPreview() {
    PskTheme {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickLinkRow()
            SpecialBetsCard()
            HomeFooter()
        }
    }
}
