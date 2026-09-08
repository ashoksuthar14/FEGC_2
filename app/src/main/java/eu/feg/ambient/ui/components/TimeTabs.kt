package eu.feg.ambient.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import eu.feg.ambient.ui.theme.PskTheme

/** The time filter above every offer list (PRD sections 5.1 and 5.2). */
enum class TimeTab(val label: String) {
    LIVE("LIVE"),
    TODAY("TODAY"),
    ONE_HOUR("1H"),
    THREE_HOURS("3H"),
    TOMORROW("TOMORROW"),
    ALL("ALL"),
}

@Composable
fun TimeTabs(
    selected: TimeTab,
    modifier: Modifier = Modifier,
    onSelect: (TimeTab) -> Unit = {},
) {
    val psk = LocalPskColors.current
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
        items(TimeTab.entries.toList(), key = { it.name }) { tab ->
            TimeTabItem(tab = tab, selected = tab == selected, onClick = { onSelect(tab) })
        }
    }
}

@Composable
private fun TimeTabItem(tab: TimeTab, selected: Boolean, onClick: () -> Unit) {
    val psk = LocalPskColors.current
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (tab == TimeTab.LIVE) LivePulseDot()
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) psk.textPrimary else psk.textSecondary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .padding(top = 8.dp)
                .height(2.dp)
                .width(if (selected) 28.dp else 0.dp)
                .background(psk.brandBlue),
        )
    }
}

@Composable
private fun LivePulseDot() {
    val psk = LocalPskColors.current
    val transition = rememberInfiniteTransition(label = "tabPulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "tabDot",
    )
    Box(
        Modifier
            .size(7.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(psk.positive),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun TimeTabsPreview() {
    PskTheme {
        Box(Modifier.padding(12.dp)) {
            TimeTabs(selected = TimeTab.TODAY)
        }
    }
}
