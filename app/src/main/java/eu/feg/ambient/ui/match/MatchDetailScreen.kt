package eu.feg.ambient.ui.match

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import eu.feg.ambient.ui.components.SpokenRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.core.formatKickoff
import eu.feg.ambient.core.formatLiveMinute
import eu.feg.ambient.core.formatOdds
import eu.feg.ambient.data.model.MatchState
import eu.feg.ambient.ui.components.OddsButton
import eu.feg.ambient.ui.components.OddsState
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.components.SetAsMyClubChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

private val MARKET_GROUPS = listOf("Match", "Goals", "Handicaps", "Player props", "Statistics")

/** PRD section 5.3. Reachable from any MatchRow via route "match/{matchId}". */
@Composable
fun MatchDetailScreen(
    viewModel: MatchDetailViewModel,
    modifier: Modifier = Modifier,
    onOpenSlip: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val club by viewModel.myClubTheme.collectAsStateWithLifecycle()
    var group by remember { mutableStateOf(MARKET_GROUPS.first()) }
    val match = state.match

    if (match == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(psk.background),
            contentAlignment = Alignment.Center,
        ) {
            Text("Match not found", color = psk.textSecondary)
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(psk.background),
    ) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(PskShapes.card)
                        .background(psk.surface)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = state.leagueName.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = psk.textSecondary,
                    )
                    TeamScoreLine(match.home.name, match.homeScore)
                    TeamScoreLine(match.away.name, match.awayScore)
                    // Offered per team, and only for the eight themed clubs — a control that
                    // appeared on every fixture and did nothing on most would be worse than
                    // no control.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SetAsMyClubChip(match.home.name, club, viewModel::setMyClub)
                        SetAsMyClubChip(match.away.name, club, viewModel::setMyClub)
                    }
                    Text(
                        text = if (match.state == MatchState.LIVE) {
                            formatLiveMinute(match.period, match.minute)
                        } else {
                            formatKickoff(match.kickoff, state.now)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (match.state == MatchState.LIVE) psk.positive else psk.textSecondary,
                    )
                }
            }

            item(key = "groups") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MARKET_GROUPS, key = { it }) { name ->
                        PskChip(name, selected = name == group, onClick = { group = name })
                    }
                }
            }

            if (group == "Statistics") {
                item(key = "stats") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(PskShapes.card)
                            .background(psk.surface)
                            .padding(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Statistics — Phase 2",
                            style = MaterialTheme.typography.bodyMedium,
                            color = psk.textSecondary,
                        )
                    }
                }
            } else {
                items(match.markets, key = { it.id }) { market ->
                    MarketAccordion(
                        name = market.name,
                        outcomeCount = market.outcomes.size,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            market.outcomes.forEach { outcome ->
                                OddsButton(
                                    label = outcome.label,
                                    value = formatOdds(outcome.odds),
                                    modifier = Modifier.weight(1f),
                                    state = when {
                                        outcome.locked -> OddsState.LOCKED
                                        (match.id + "/" + outcome.id) in state.selectedOutcomeIds ->
                                            OddsState.SELECTED
                                        else -> OddsState.DEFAULT
                                    },
                                    isTop = outcome.isTop,
                                    selectionLabel = SpokenRow.selectionLabel(
                                        outcome.label, match.home.name, match.away.name,
                                    ),
                                    onClick = { viewModel.toggleSelection(market.id, outcome.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sticky slip bar, only once there is something in the slip.
        AnimatedVisibility(
            visible = state.slipCount > 0,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .clip(PskShapes.card)
                    .background(psk.brandBlue)
                    .clickable(role = Role.Button, onClick = onOpenSlip)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Bet slip",
                    style = MaterialTheme.typography.titleMedium,
                    color = psk.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.slipCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = psk.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
