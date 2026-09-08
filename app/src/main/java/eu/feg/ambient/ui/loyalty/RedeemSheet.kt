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
 * The redemption sheet and the words it uses when it has to say no.
 *
 * Split out of RewardsScreen for length, and the seam is a real one rather than an arbitrary
 * cut: everything here runs only after a customer has tapped Redeem, and none of it is part
 * of drawing the catalogue.
 */

/**
 * Confirm, then the receipt. The same sheet, two states, and the change between them is a
 * recomposition with no transition: the code is simply there.
 */
@Composable
internal fun RedeemSheet(
    perk: Perk,
    result: RedeemResult?,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val psk = LocalPskColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = perk.name,
            style = MaterialTheme.typography.titleLarge,
            color = psk.textPrimary,
        )
        when (result) {
            null -> {
                Text(
                    text = "Costs " + badgeCount(perk.badgeCost) + ". Here is exactly what you get:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textSecondary,
                )
                // The terms, whole. Summarising them is how "subject to the club releasing
                // them" turns into a complaint.
                Text(
                    text = perk.terms,
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PskChip("Redeem for " + badgeCount(perk.badgeCost), onClick = onConfirm)
                    PskChip("Not now", onClick = onClose)
                }
            }
            is RedeemResult.Done -> {
                Text(
                    text = "Redeemed",
                    style = MaterialTheme.typography.titleMedium,
                    color = psk.positive,
                )
                Text(
                    text = result.redemption.code,
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    color = psk.textPrimary,
                    modifier = Modifier.semantics {
                        contentDescription = "Your code is " + spokenCode(result.redemption.code)
                    },
                )
                Text(
                    text = "That is your reference if you need to ask us about it. " +
                        badgeCount(result.redemption.badgesSpent) + " spent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textSecondary,
                )
                PskChip("Done", onClick = onClose)
            }
            else -> {
                Text(
                    text = refusalText(result),
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textPrimary,
                )
                PskChip("Close", onClick = onClose)
            }
        }
    }
}

private fun refusalText(result: RedeemResult): String = when (result) {
    is RedeemResult.NotEnoughBadges -> "You need " + badgeCount(result.short) + " more for this one."
    RedeemResult.TierTooLow -> "This one opens at a higher tier."
    RedeemResult.OutOfStock -> "None available right now."
    RedeemResult.AlreadyRedeemed -> "You already have this one."
    RedeemResult.Unavailable -> "Rewards are not available on this account right now."
    is RedeemResult.Done -> ""
}

/** "PSK-SCAR-1000" read as "P S K, S C A R, 1000" rather than as one made-up word. */
internal fun spokenCode(code: String): String =
    code.split("-").joinToString(", ") { part ->
        if (part.all { it.isDigit() }) part else part.toCharArray().joinToString(" ")
    }
