package eu.feg.ambient.ui.loyalty

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.R
import eu.feg.ambient.ambient.loyalty.Badge
import eu.feg.ambient.ambient.loyalty.Mission
import eu.feg.ambient.ambient.loyalty.MissionType
import eu.feg.ambient.ui.components.EmptyState
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

/**
 * N7: missions and the tier they add up to.
 *
 * Two visual registers on one screen, on purpose. Ordinary missions sit on surfaceRaised
 * with the brand-blue bar, the same weight as a bet card. The deposit-limit mission sits on
 * the flat surface with a thin green outline and a shield, which is exactly how the
 * Responsible Gaming screen and the More sheet already mark care. A customer who has seen
 * either will read this card as the same kind of thing before reading a word of it.
 */
@Composable
fun MissionsScreen(
    viewModel: LoyaltyViewModel,
    modifier: Modifier = Modifier,
    onSetLimit: () -> Unit = {},
    onOpenRewards: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDone by rememberSaveable { mutableStateOf(false) }

    // The care card is drawn on its own, ahead of the list, so it is never "mission 4 of 7".
    val careMission = state.active.firstOrNull { it.type == MissionType.SET_A_LIMIT }
    val ordinary = state.active.filterNot { it.type == MissionType.SET_A_LIMIT }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Missions",
                    style = MaterialTheme.typography.titleLarge,
                    color = psk.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                PskChip("Rewards", onClick = onOpenRewards)
            }
        }

        item(key = "tier") { TierHeader(state) }

        if (state.paused) {
            item(key = "paused") { PausedNotice() }
        }

        careMission?.let { mission ->
            item(key = mission.id) { CareCard(mission, onSetLimit) }
        }

        if (ordinary.isEmpty() && careMission == null) {
            item(key = "empty") {
                EmptyState(
                    headline = "Nothing left to do.",
                    body = "Every mission is done. New ones appear here when there are any.",
                )
            }
        } else {
            items(ordinary, key = { it.id }) { mission ->
                MissionCard(mission, viewModel.badgeFor(mission))
            }
        }

        if (state.done.isNotEmpty()) {
            item(key = "done-header") {
                DoneHeader(
                    count = state.done.size,
                    expanded = showDone,
                    onToggle = { showDone = !showDone },
                )
            }
            if (showDone) {
                items(state.done, key = { "done-" + it.id }) { mission ->
                    DoneRow(mission, state.badges.firstOrNull { it.id == mission.badgeId })
                }
            }
        }
    }
}

@Composable
private fun MissionCard(mission: Mission, badge: Badge?) {
    val psk = LocalPskColors.current
    val progressLine = mission.progress.toString() + " of " + mission.target
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surfaceRaised)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            badge?.let { BadgeIcon(it, psk.textPrimary) }
            Column(Modifier.weight(1f)) {
                Text(
                    text = mission.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = psk.textPrimary,
                )
                badge?.let {
                    Text(
                        text = "Badge: " + it.name +
                            (if (it.weight > 1) " (counts " + it.weight + ")" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = psk.textSecondary,
                    )
                }
            }
            // The bar below announces the same value; reading it twice is noise.
            Text(
                text = progressLine,
                style = MaterialTheme.typography.labelMedium,
                color = psk.textSecondary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        Text(
            text = mission.description,
            style = MaterialTheme.typography.bodyMedium,
            color = psk.textSecondary,
        )
        LoyaltyProgressBar(
            progress = mission.progress,
            target = mission.target,
            fill = psk.brandBlue,
            description = mission.title + ", " + progressLine + " complete",
        )
    }
}

/**
 * The deposit-limit mission.
 *
 * No progress bar and no accent fill: there is nothing to grind toward, only a decision to
 * make once. The chip is the ordinary unselected chip rather than a primary button, because
 * the card is an offer of help and a big blue button would make it a demand. The weight is
 * stated as a fact in the last line and nowhere else.
 */
@Composable
private fun CareCard(mission: Mission, onSetLimit: () -> Unit) {
    val psk = LocalPskColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .border(1.dp, psk.positive, PskShapes.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_badge_shield),
                contentDescription = null,
                tint = psk.positive,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = mission.title,
                style = MaterialTheme.typography.titleMedium,
                color = psk.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = mission.description,
            style = MaterialTheme.typography.bodyMedium,
            color = psk.textPrimary,
        )
        Text(
            text = "It counts for three badges, more than any other mission here. " +
                "It also costs nothing and takes about a minute.",
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Not set yet",
                style = MaterialTheme.typography.labelMedium,
                color = psk.textSecondary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { stateDescription = "Deposit limit not set yet" },
            )
            PskChip("Choose a limit", onClick = onSetLimit)
        }
    }
}

@Composable
private fun DoneHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val psk = LocalPskColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .semantics { stateDescription = if (expanded) "expanded" else "collapsed" }
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Done",
            style = MaterialTheme.typography.titleMedium,
            color = psk.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = psk.textSecondary,
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = psk.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A finished mission: the badge it gave, and when. The shield keeps its green when it is done. */
@Composable
private fun DoneRow(mission: Mission, badge: Badge?) {
    val psk = LocalPskColors.current
    val care = mission.type == MissionType.SET_A_LIMIT
    Row(
        Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        badge?.let { BadgeIcon(it, if (care) psk.positive else psk.textPrimary) }
        Column(Modifier.weight(1f)) {
            Text(
                text = mission.title,
                style = MaterialTheme.typography.bodyMedium,
                color = psk.textPrimary,
            )
            badge?.let {
                Text(
                    text = it.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                )
            }
        }
        badge?.earnedAt?.let {
            Text(
                text = "Earned " + formatEarnedDate(it),
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
            )
        }
    }
}
