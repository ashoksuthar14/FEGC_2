package eu.feg.ambient.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.tooling.preview.Preview
import eu.feg.ambient.ui.theme.PskTheme
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

/**
 * A club crest stands in as a coloured circle with initials — PRD section 7 rules out
 * scraped logos, and Coil has nothing to load in Phase 1.
 */
@Composable
fun TeamCrest(name: String, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    // Deterministic hue per club, so the same team keeps the same crest across screens.
    // surfaceRaised is deliberately absent: it is the row background, and a crest painted
    // in it disappears.
    val palette = listOf(
        psk.brandBlue, psk.brandBlueDark, psk.positive,
        psk.negative, psk.jackpotYellowDim, psk.betslipPanel,
    )
    val fill = palette[(name.hashCode().and(Int.MAX_VALUE)) % palette.size]
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = psk.textPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Prematch time, e.g. "tomorrow 00:30". */
@Composable
fun KickoffChip(text: String, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    Box(
        modifier = modifier
            .clip(PskShapes.oddsButton)
            .background(psk.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * Live period and minute, e.g. "1. poluvrijeme - 44m". Croatian is kept deliberately
 * (CLAUDE.md rule 5). The dot pulses so a glance tells you the row is moving.
 *
 * The dot is green, not the red the PRD asks for: psk.hr renders it green on both the live
 * minute chip and the "Pauza" chip, and matching the real product wins here.
 */
@Composable
fun LiveMinuteChip(text: String, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    val transition = rememberInfiniteTransition(label = "livePulse")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Row(
        modifier = modifier
            .clip(PskShapes.oddsButton)
            .background(psk.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .alpha(dotAlpha)
                .clip(CircleShape)
                .background(psk.positive),
        )
    }
}

/** BB, 90+, stream and stats markers. Colour is per-badge; the rest are plain meta text. */
@Composable
fun BadgeChip(text: String, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    val content: Color = when (text) {
        "BB" -> psk.brandBlue
        "90+" -> psk.negative
        else -> psk.textSecondary
    }
    Box(
        modifier = modifier
            .clip(PskShapes.oddsButton)
            .background(psk.surfaceVariant)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Preview(name = "Row parts", showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun MatchRowPartsPreview() {
    PskTheme {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TeamCrest("Liverpool")
                TeamCrest("Betis")
                TeamCrest("NK Lucko")
                TeamCrest("Imisli FK")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KickoffChip("tomorrow 00:30")
                BadgeChip("BB")
                BadgeChip("90+")
                BadgeChip("STREAM")
            }
            LiveMinuteChip("1. poluvrijeme - 44m")
            LiveMinuteChip("Pauza")
        }
    }
}
