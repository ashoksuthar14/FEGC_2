package eu.feg.ambient.ui.loyalty

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
 * The tier hero and the plates the loyalty cards are built from.
 *
 * Split from LoyaltyPieces for length, on a real seam: everything here is about how the
 * feature LOOKS -- a metal, a plate, a hero band -- while LoyaltyPieces holds the pieces that
 * carry meaning to a screen reader. Keeping the accessible primitives away from the
 * decoration is what stops the decoration quietly acquiring the semantics.
 */

/**
 * The four tier metals.
 *
 * ON THE PALETTE RULE: Color.kt says nothing outside it may declare a Color, and the one
 * standing exception is ClubTheme.kt, because a feature whose subject IS a colour cannot take
 * its colours from a fixed palette. Tiers are the same shape of problem -- bronze, silver,
 * gold and platinum are the content, not decoration -- so the exception is here, in one named
 * place, rather than spread across the cards that use it. Gold reuses the palette's own
 * jackpot yellow rather than inventing a second one.
 *
 * Each is legible as a fill behind white text and as a tint on the dark surface; they are
 * deliberately close in weight, because a tier ladder that gets visibly louder as it climbs is
 * a status mechanic, and this one is a shelf for badges.
 */
@Immutable
object LoyaltyMetals {
    val bronze = Color(0xFFB0764A)
    val silver = Color(0xFFB9C0CC)
    val gold = Color(0xFFF8C102)
    val platinum = Color(0xFFCBD5E1)

    fun of(tier: LoyaltyTier): Color = when (tier) {
        LoyaltyTier.BRONZE -> bronze
        LoyaltyTier.SILVER -> silver
        LoyaltyTier.GOLD -> gold
        LoyaltyTier.PLATINUM -> platinum
    }
}

/**
 * A round plate with a glyph on it.
 *
 * The badges were bare 28dp icons floating at the left of a card, which read as clip art. A
 * plate gives them an edge and a weight, and the tint carries meaning the icon cannot: the
 * care mission is green here and nowhere else on the screen.
 */
@Composable
fun Medallion(
    iconRes: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    dimmed: Boolean = false,
) {
    // 0.55, not 0.35. Dimmed has to read as "not yet", and at a third of full strength the
    // glyph stopped being legible at all -- the plate looked empty rather than unearned.
    val alpha = if (dimmed) 0.55f else 1f
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f * alpha))
            .border(1.dp, tint.copy(alpha = 0.45f * alpha), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint.copy(alpha = alpha),
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** "2 of 3" as a plate rather than grey text at the end of a row. */
@Composable
fun CountPill(text: String, tint: Color, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    Box(
        modifier
            .clip(PskShapes.chip)
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (tint == psk.textPrimary) psk.textPrimary else tint,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * The tier, as the thing you see first.
 *
 * It replaces a flat card whose title was the same size as every other title on the screen,
 * so the one fact the page is organised around looked like a row of a list. The metal band is
 * a wash rather than a fill -- at full strength it reads as a promotional banner, which is the
 * register this feature spends the rest of its time avoiding.
 */
@Composable
fun TierHero(state: LoyaltyState, modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    val metal = LoyaltyMetals.of(state.tier)
    val next = state.nextTier
    val floor = state.tier.badgesRequired
    Column(
        modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(
                Brush.horizontalGradient(
                    listOf(metal.copy(alpha = 0.20f), psk.surface),
                ),
            )
            .border(1.dp, metal.copy(alpha = 0.35f), PskShapes.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Medallion(R.drawable.ic_badge_star, metal, size = 52.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = tierLabel(state.tier),
                    style = MaterialTheme.typography.headlineSmall,
                    color = psk.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = badgeCount(state.badgeWeight) +
                        (if (state.spendableBadges != state.badgeWeight) {
                            " · " + state.spendableBadges + " to spend"
                        } else ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = psk.textSecondary,
                )
            }
        }
        if (next != null) {
            val line = state.badgeWeight.toString() + " of " + next.badgesRequired +
                " badges for " + tierLabel(next)
            LoyaltyProgressBar(
                progress = state.badgeWeight - floor,
                target = next.badgesRequired - floor,
                fill = metal,
                description = line,
            )
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        } else {
            LoyaltyProgressBar(1, 1, metal, description = "Platinum, the top tier")
            Text(
                text = "The top tier. Every perk is open to you.",
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
            )
        }
    }
}
