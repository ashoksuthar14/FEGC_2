package eu.feg.ambient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import eu.feg.ambient.ui.theme.PskTheme

/** One tappable price inside a row's odds group. */
data class RowOdd(
    val label: String,
    val value: String,
    val state: OddsState = OddsState.DEFAULT,
    val isTop: Boolean = false,
    val flash: OddsFlash = OddsFlash.NONE,
)

/**
 * Presentation model for [MatchRow]. Screens map domain objects into this, which keeps
 * `ui/components` independent of `data/` (CLAUDE.md rule 7).
 */
data class MatchRowUi(
    val id: String,
    val homeName: String,
    val awayName: String,
    /** Prematch time ("tomorrow 00:30") or live period ("1. poluvrijeme - 44m"). */
    val timeText: String,
    val isLive: Boolean = false,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val badges: List<String> = emptyList(),
    /** "Basic offer", "Match", "Match - double chance"… shown above the odds group. */
    val marketLabel: String? = null,
    val odds: List<RowOdd> = emptyList(),
    val isFavourite: Boolean = false,
)

/**
 * Layout follows the screenshots: the time chip and badges sit on their own band above the
 * teams, with score and odds to the right of the team names. The PRD's desktop rows put the
 * chip in a left-hand column, which cannot fit beside teams, score and three odds cells on a
 * 360dp phone — the banded form is both what psk.hr renders and what fits.
 */
@Composable
fun MatchRow(
    match: MatchRowUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onToggleFavourite: () -> Unit = {},
    onOddClick: (Int) -> Unit = {},
) {
    val psk = LocalPskColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (match.isLive) {
                LiveMinuteChip(match.timeText)
            } else {
                KickoffChip(match.timeText)
            }
            match.badges.forEach { BadgeChip(it) }
            Box(Modifier.weight(1f))
            Icon(
                imageVector = if (match.isFavourite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (match.isFavourite) "Remove from favourites" else "Add to favourites",
                tint = if (match.isFavourite) psk.jackpotYellow else psk.textSecondary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onToggleFavourite),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TeamLine(match.homeName)
                TeamLine(match.awayName)
            }

            if (match.isLive && match.homeScore != null && match.awayScore != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ScoreLine(match.homeScore)
                    ScoreLine(match.awayScore)
                }
            }

            if (match.odds.isNotEmpty()) {
                Column(modifier = Modifier.width(168.dp)) {
                    match.marketLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = psk.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        match.odds.forEachIndexed { index, odd ->
                            OddsButton(
                                label = odd.label,
                                value = odd.value,
                                modifier = Modifier.weight(1f),
                                state = odd.state,
                                isTop = odd.isTop,
                                flash = odd.flash,
                                onClick = { onOddClick(index) },
                            )
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = psk.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 1dp separator between stacked rows, PRD section 3.3. */
@Composable
fun MatchRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = LocalPskColors.current.surfaceVariant)
}

@Composable
private fun TeamLine(name: String) {
    val psk = LocalPskColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TeamCrest(name)
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = psk.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ScoreLine(score: Int) {
    Text(
        text = score.toString(),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalPskColors.current.textPrimary,
        textAlign = TextAlign.End,
        modifier = Modifier.width(16.dp),
    )
}
