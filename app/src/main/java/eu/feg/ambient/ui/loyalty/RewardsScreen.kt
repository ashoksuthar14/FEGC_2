package eu.feg.ambient.ui.loyalty

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import eu.feg.ambient.R
import eu.feg.ambient.ambient.loyalty.Perk
import eu.feg.ambient.ambient.loyalty.PerkCategory
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

        // The same hero the missions screen opens on. It already says the tier and what is
        // left to spend, which is exactly the two facts a catalogue is read against, and two
        // screens of one feature should not introduce themselves differently.
        item(key = "tier") { TierHero(state) }

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
    // Locked perks step back rather than disappear: seeing what is out of reach is the point
    // of a catalogue, and greying the whole card would make it look broken instead of distant.
    val reachable = redemption != null || status.isNullOrEmpty()
    val accent = if (redemption != null) psk.positive else psk.brandBlue
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .border(
                1.dp,
                if (reachable) accent.copy(alpha = 0.45f) else psk.surfaceRaised,
                PskShapes.card,
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Medallion(
                iconRes = perkIcon(perk.category),
                tint = accent,
                size = 44.dp,
                dimmed = !reachable,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = perk.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = psk.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = categoryLabel(perk.category),
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                )
            }
            CountPill(badgeCount(perk.badgeCost), accent)
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
                text = perk.stock.toString() + " available",
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
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

/**
 * A glyph per category, from the badge set rather than a new icon pack.
 *
 * THE SHIELD IS NOT IN HERE. It is the deposit-limit mission's mark, and it was briefly a
 * charity donation too -- which put the protection glyph on a reward and made the one icon
 * that means "we are looking after you" mean "you bought something". A donation to grassroots
 * football gets the ball instead, which is also simply better. Voucher shares the ticket:
 * five euros of coffee is a ticket for a coffee, and one honest reuse beats a sixth drawable.
 */
private fun perkIcon(category: PerkCategory): Int = when (category) {
    PerkCategory.MATCH_TICKET, PerkCategory.PARTNER_VOUCHER -> R.drawable.ic_badge_ticket
    PerkCategory.EXPERIENCE -> R.drawable.ic_badge_star
    PerkCategory.FEATURE_ACCESS -> R.drawable.ic_badge_widget
    PerkCategory.CHARITY_DONATION -> R.drawable.ic_ball
    PerkCategory.MERCHANDISE -> R.drawable.ic_badge_crest
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
