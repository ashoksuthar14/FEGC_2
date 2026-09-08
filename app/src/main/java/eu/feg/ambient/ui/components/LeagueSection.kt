package eu.feg.ambient.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import eu.feg.ambient.ui.theme.PskTheme

/**
 * Collapsible league grouping: flag, name, match count, chevron (PRD section 3.4).
 * Expanded by default on Home; the Live screen collapses the tail of the list.
 */
@Composable
fun LeagueSection(
    name: String,
    count: Int,
    modifier: Modifier = Modifier,
    flag: String? = null,
    expanded: Boolean = true,
    onToggle: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val psk = LocalPskColors.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        label = "leagueChevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(psk.surfaceVariant)
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            flag?.let { FlagBadge(it) }
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = psk.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
            )
            Box(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = psk.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    }
}

/** Country flags stand in as a two-letter tile — no image assets in Phase 1. */
@Composable
fun FlagBadge(code: String, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    Box(
        modifier = modifier
            .clip(PskShapes.oddsButton)
            .background(psk.surfaceRaised)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = code.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun LeagueSectionPreview() {
    PskTheme {
        Column(Modifier.padding(12.dp)) {
            LeagueSection(name = "England 1", count = 8, flag = "gb") {
                MatchRow(samplePrematchRow)
                MatchRow(sampleTopBadgeRow)
            }
        }
    }
}

@Preview(name = "Collapsed", showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun LeagueSectionCollapsedPreview() {
    PskTheme {
        Column(Modifier.padding(12.dp)) {
            LeagueSection(name = "3.Croatia", count = 2, flag = "hr", expanded = false) {
                MatchRow(sampleLiveRow)
            }
        }
    }
}
