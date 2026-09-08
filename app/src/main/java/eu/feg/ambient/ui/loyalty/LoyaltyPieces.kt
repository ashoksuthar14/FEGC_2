package eu.feg.ambient.ui.loyalty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.R
import eu.feg.ambient.ambient.loyalty.Badge
import eu.feg.ambient.ambient.loyalty.LoyaltyState
import eu.feg.ambient.ambient.loyalty.LoyaltyTier
import eu.feg.ambient.ambient.loyalty.PerkCategory
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import eu.feg.ambient.ui.theme.PskTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * A bar that says its value.
 *
 * The visual is 6 dp tall like the limit bars on the Responsible Gaming screen, and the whole
 * thing is one semantics node: TalkBack reads "2 of 3 complete" and reports it as a progress
 * bar, rather than reading a coloured box as nothing at all. The fill colour is a parameter
 * because the care card and the ordinary missions must not share an accent.
 */
@Composable
fun LoyaltyProgressBar(
    progress: Int,
    target: Int,
    fill: Color,
    modifier: Modifier = Modifier,
    description: String = progress.toString() + " of " + target + " complete",
) {
    val psk = LocalPskColors.current
    val safeTarget = target.coerceAtLeast(1)
    val fraction = (progress.toFloat() / safeTarget).coerceIn(0f, 1f)
    Box(
        modifier
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = progress.coerceIn(0, safeTarget).toFloat(),
                    range = 0f..safeTarget.toFloat(),
                )
            }
            .fillMaxWidth()
            .height(6.dp)
            .clip(PskShapes.chip)
            .background(psk.surfaceVariant),
    ) {
        // A zero-width child still lays out; fillMaxWidth(0f) is the honest empty bar.
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(PskShapes.chip)
                .background(fill),
        )
    }
}

/**
 * Current tier and the distance to the next one.
 *
 * "5 of 8 badges for Gold" counts weight, not rows, because that is what the tier is made of.
 * At Platinum the bar is full and the line says so plainly; there is no "coming soon" above
 * the top, because a ceiling that keeps moving is the mechanic this design is not.
 */
@Composable
fun TierHeader(state: LoyaltyState, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    val next = state.nextTier
    val floor = state.tier.badgesRequired
    Column(
        modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tierLabel(state.tier),
                style = MaterialTheme.typography.titleMedium,
                color = psk.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = badgeCount(state.badgeWeight),
                style = MaterialTheme.typography.labelMedium,
                color = psk.textSecondary,
            )
        }
        if (next != null) {
            val line = state.badgeWeight.toString() + " of " + next.badgesRequired +
                " badges for " + tierLabel(next)
            LoyaltyProgressBar(
                progress = state.badgeWeight - floor,
                target = next.badgesRequired - floor,
                fill = psk.brandBlue,
                description = line,
            )
            // The bar already says this; a sighted reader wants it written, a listener does not want it twice.
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        } else {
            LoyaltyProgressBar(
                progress = 1,
                target = 1,
                fill = psk.brandBlue,
                description = "Platinum, the top tier",
            )
            Text(
                text = "The top tier. Every perk is open to you.",
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
            )
        }
    }
}

/** A badge's icon, dimmed until it is earned. The name is read; the drawable is decoration. */
@Composable
fun BadgeIcon(badge: Badge, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(badge.iconRes),
        contentDescription = null,
        tint = if (badge.earnedAt != null) tint else tint.copy(alpha = 0.45f),
        modifier = modifier.size(28.dp),
    )
}

/**
 * The one line both screens show while the mechanic is paused.
 *
 * It says what stays, before what stops: badges are kept and perks stay redeemable in CALM,
 * and the customer should not have to read to the end of a sentence to learn that.
 */
@Composable
fun PausedNotice(modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    Text(
        text = "Your badges are yours. Missions are resting while your account is in a quieter " +
            "mode, and they carry on where they left off.",
        style = MaterialTheme.typography.bodyMedium,
        color = psk.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(14.dp),
    )
}

fun tierLabel(tier: LoyaltyTier): String = when (tier) {
    LoyaltyTier.BRONZE -> "Bronze"
    LoyaltyTier.SILVER -> "Silver"
    LoyaltyTier.GOLD -> "Gold"
    LoyaltyTier.PLATINUM -> "Platinum"
}

fun categoryLabel(category: PerkCategory): String = when (category) {
    PerkCategory.MATCH_TICKET -> "Tickets"
    PerkCategory.MERCHANDISE -> "Merchandise"
    PerkCategory.EXPERIENCE -> "Experience"
    PerkCategory.FEATURE_ACCESS -> "Features"
    PerkCategory.PARTNER_VOUCHER -> "Voucher"
    PerkCategory.CHARITY_DONATION -> "Donation"
}

fun badgeCount(n: Int): String = if (n == 1) "1 badge" else n.toString() + " badges"

/** "12.3.2026", the day-first form the rest of the product uses for dates. */
fun formatEarnedDate(at: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val d = at.toLocalDateTime(zone).date
    return d.dayOfMonth.toString() + "." + d.monthNumber + "." + d.year
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun LoyaltyPiecesPreview() {
    PskTheme {
        val psk = LocalPskColors.current
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TierHeader(
                LoyaltyState(
                    badges = listOf(
                        Badge("b1", "Colours on", R.drawable.ic_badge_crest, Instant.fromEpochSeconds(0)),
                        Badge("b2", "Limit set", R.drawable.ic_badge_shield, Instant.fromEpochSeconds(0), weight = 3),
                    ),
                ),
            )
            LoyaltyProgressBar(progress = 2, target = 3, fill = psk.brandBlue)
            LoyaltyProgressBar(progress = 0, target = 1, fill = psk.positive)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeIcon(Badge("b3", "First whistle", R.drawable.ic_ball, Instant.fromEpochSeconds(0)), psk.jackpotYellow)
                BadgeIcon(Badge("b4", "Three days", R.drawable.ic_badge_star), psk.jackpotYellow)
            }
            PausedNotice()
            Text(formatEarnedDate(Instant.fromEpochSeconds(1_772_000_000)), color = psk.textSecondary, fontWeight = FontWeight.Normal)
        }
    }
}
