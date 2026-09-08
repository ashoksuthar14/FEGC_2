package eu.feg.ambient.ui.loyalty

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.feg.ambient.ambient.loyalty.LoyaltyRepository.RedeemResult
import eu.feg.ambient.ambient.loyalty.LoyaltyState
import eu.feg.ambient.ambient.loyalty.Perk
import eu.feg.ambient.ambient.loyalty.Redemption
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

/**
 * N7: the perk catalogue.
 *
 * Every card states its price and its outcome, and a locked card states what is missing in
 * badges or in tier -- never in time. The redemption sheet shows the full terms before the
 * button and the code after it, with nothing in between: no spinner, no reveal, no motion.
 * Suspense is what a loot box sells, and a receipt has none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    viewModel: LoyaltyViewModel,
    modifier: Modifier = Modifier,
    onOpenMissions: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val outcome by viewModel.redeemOutcome.collectAsStateWithLifecycle()
    var showWhy by rememberSaveable { mutableStateOf(false) }
    var sheetPerkId by rememberSaveable { mutableStateOf<String?>(null) }

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
                    text = "Rewards",
                    style = MaterialTheme.typography.titleLarge,
                    color = psk.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                PskChip("Missions", onClick = onOpenMissions)
            }
        }

        item(key = "balance") {
            Text(
                text = badgeCount(state.spendableBadges) + " to spend · " + tierLabel(state.tier),
                style = MaterialTheme.typography.bodyMedium,
                color = psk.textSecondary,
            )
        }

        if (state.paused) {
            item(key = "paused") { PausedNotice() }
        }

        item(key = "why") {
            WhyNoFreeBets(expanded = showWhy, onToggle = { showWhy = !showWhy })
        }

        items(viewModel.perks, key = { it.id }) { perk ->
            PerkCard(
                perk = perk,
                state = state,
                redemption = state.redemptions.firstOrNull { it.perkId == perk.id },
                onRedeem = { sheetPerkId = perk.id },
            )
        }
    }

    val sheetPerk = viewModel.perks.firstOrNull { it.id == sheetPerkId }
    if (sheetPerk != null) {
        // A result for another perk is stale; only this perk's own outcome moves the sheet on.
        val result = outcome?.takeIf { it.perkId == sheetPerk.id }?.result
        ModalBottomSheet(
            onDismissRequest = {
                sheetPerkId = null
                viewModel.clearRedeemOutcome()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = psk.surface,
        ) {
            RedeemSheet(
                perk = sheetPerk,
                result = result,
                onConfirm = { viewModel.redeem(sheetPerk.id) },
                onClose = {
                    sheetPerkId = null
                    viewModel.clearRedeemOutcome()
                },
            )
        }
    }
}

/**
 * The honest answer, in one sentence, behind a plain text link. It expands in place rather
 * than opening a dialog because a question this ordinary should not interrupt anything.
 */
@Composable
private fun WhyNoFreeBets(expanded: Boolean, onToggle: () -> Unit) {
    val psk = LocalPskColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Why no free bets?",
            style = MaterialTheme.typography.labelMedium,
            color = psk.textSecondary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(role = Role.Button, onClick = onToggle),
        )
        if (expanded) {
            Text(
                text = "Rewards here are never gambling credit, so they are available to every " +
                    "customer, including anyone taking a break.",
                style = MaterialTheme.typography.bodyMedium,
                color = psk.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PskShapes.card)
                    .background(psk.surface)
                    .padding(14.dp),
            )
        }
    }
}

/**
 * What a card can say about itself. The order matches the repository's checks so the card
 * never promises a redemption the repository would then refuse.
 */
private fun perkStatus(perk: Perk, state: LoyaltyState, redemption: Redemption?): String? = when {
    redemption != null -> null
    !perk.inStock -> "None available right now"
    state.tier.ordinal < perk.tierRequired.ordinal -> "Opens at " + tierLabel(perk.tierRequired)
    state.spendableBadges < perk.badgeCost ->
        badgeCount(perk.badgeCost - state.spendableBadges) + " more"
    else -> ""
}

@Composable
private fun PerkCard(
    perk: Perk,
    state: LoyaltyState,
    redemption: Redemption?,
    onRedeem: () -> Unit,
) {
    val psk = LocalPskColors.current
    val status = perkStatus(perk, state, redemption)
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = perk.name,
                style = MaterialTheme.typography.titleMedium,
                color = psk.textPrimary,
                modifier = Modifier.weight(1f),
            )
            CategoryTag(categoryLabel(perk.category))
        }
        Text(
            text = perk.description,
            style = MaterialTheme.typography.bodyMedium,
            color = psk.textSecondary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = badgeCount(perk.badgeCost) + " · " + perk.stock + " available",
                style = MaterialTheme.typography.labelMedium,
                color = psk.textPrimary,
                modifier = Modifier.weight(1f),
            )
            when {
                redemption != null -> Text(
                    text = "Redeemed · " + redemption.code,
                    style = MaterialTheme.typography.labelMedium,
                    color = psk.positive,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics {
                        contentDescription = "Redeemed, code " + spokenCode(redemption.code)
                    },
                )
                status.isNullOrEmpty() -> PskChip("Redeem", onClick = onRedeem)
                else -> Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = psk.textSecondary,
                )
            }
        }
    }
}

/** A static label, not a chip: PskChip announces itself as a button, and this does nothing. */
@Composable
private fun CategoryTag(label: String) {
    val psk = LocalPskColors.current
    Box(
        Modifier
            .clip(PskShapes.chip)
            .background(psk.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
        )
    }
}
