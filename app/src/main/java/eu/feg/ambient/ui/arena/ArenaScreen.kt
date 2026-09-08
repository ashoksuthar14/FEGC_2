package eu.feg.ambient.ui.arena

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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.feg.ambient.core.formatMoney
import eu.feg.ambient.core.formatOdds
import eu.feg.ambient.data.model.ArenaTip
import eu.feg.ambient.ui.components.BadgeChip
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

/** PRD section 5.8 — the tipster feed. Copy-slip is a second path into the Phase 2 trigger. */
@Composable
fun ArenaScreen(
    tips: List<ArenaTip>,
    modifier: Modifier = Modifier,
    onCopySlip: (ArenaTip) -> Unit = {},
) {
    val psk = LocalPskColors.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") {
            Text(
                text = "PSK Arena",
                style = MaterialTheme.typography.titleLarge,
                color = psk.textPrimary,
            )
        }
        item(key = "controls") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PskChip("According to attractiveness", selected = true)
                PskChip("Filters")
            }
        }
        items(tips, key = { it.id }) { tip ->
            ArenaTipRow(tip, onCopy = { onCopySlip(tip) })
        }
    }
}

@Composable
private fun ArenaTipRow(tip: ArenaTip, onCopy: () -> Unit) {
    val psk = LocalPskColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surfaceRaised)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(psk.brandBlue),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tip.username.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
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
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "Tip details",
                tint = psk.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy this slip",
                tint = psk.brandBlue,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onCopy),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BadgeChip("inspiration " + tip.inspiration)
            tip.badges.forEach { BadgeChip(it) }
            BadgeChip(tip.eventCount.toString() + " events")
            BadgeChip(tip.type)
            BadgeChip(tip.day + " " + tip.time)
        }

        Row {
            Cell("Stake", formatMoney(tip.stake), Modifier.weight(1f))
            Cell("Course", formatOdds(tip.course), Modifier.weight(1f))
            Cell("Possible payment", formatMoney(tip.possiblePayment), Modifier.weight(1f), true)
        }
    }
}

@Composable
private fun Cell(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
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
