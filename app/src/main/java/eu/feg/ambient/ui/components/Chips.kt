package eu.feg.ambient.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
    /** N6: the club accent, when a caller has one. Defaults to the operator's brand blue. */
    selectedFill: androidx.compose.ui.graphics.Color? = null,
    /** Text on [selectedFill]. Computed by the caller from the club, never assumed white. */
    selectedContent: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (selected) selectedFill ?: psk.brandBlue else psk.surfaceVariant,
        animationSpec = if (reducedMotion()) snap<Color>() else spring<Color>(),
        label = "chipFill",
    )
    val onFill = if (selected) selectedContent ?: psk.textPrimary else psk.textSecondary
    Row(
        modifier = modifier
            // The pill stays 34 dp tall as in the screenshots; the extra height is empty,
            // tappable padding around it.
            .minimumInteractiveComponentSize()
            .semantics { this.selected = selected }
            .clip(PskShapes.chip)
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .focusRing(interaction, PskShapes.chip)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leadingDotColor?.let {
            // A country marker the label already names; nothing to read.
            androidx.compose.foundation.layout.Box(
                Modifier
                    .clearAndSetSemantics { }
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(it),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = onFill,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
        count?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = onFill,
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
