package eu.feg.ambient.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import eu.feg.ambient.ui.theme.PskTheme

/**
 * The horizontal scrollers: sports (Football, Tennis…), countries (Croatia 1, England 1…)
 * and casino categories all use this one chip (PRD section 3.4).
 */
@Composable
fun PskChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    count: Int? = null,
    leadingDotColor: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    val fill by animateColorAsState(
        targetValue = if (selected) psk.brandBlue else psk.surfaceVariant,
        label = "chipFill",
    )
    Row(
        modifier = modifier
            .clip(PskShapes.chip)
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leadingDotColor?.let {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(it),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) psk.textPrimary else psk.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
        count?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) psk.textPrimary else psk.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun PskChipPreview() {
    PskTheme {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PskChip("Football", selected = true, count = 314)
            PskChip("Tennis", count = 88)
            PskChip("Croatia 1", leadingDotColor = LocalPskColors.current.negative)
        }
    }
}
