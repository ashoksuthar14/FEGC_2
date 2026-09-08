package eu.feg.ambient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import eu.feg.ambient.ui.theme.PskTheme

/** 16:9 promo hero with title, subtitle and a CTA strip (PRD section 3.4). */
@Composable
fun PromoCard(
    title: String,
    subtitle: String,
    cta: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    val end = accent ?: psk.brandBlue
    Column(
        modifier = modifier
            .clip(PskShapes.card)
            .background(Brush.horizontalGradient(listOf(psk.brandBlueDeep, end)))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .padding(14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = psk.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = psk.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .background(psk.betslipPanel)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = cta,
                style = MaterialTheme.typography.labelMedium,
                color = psk.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Casino tile: gradient placeholder, name, badge and the yellow jackpot bar. */
@Composable
fun GameTile(
    name: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    jackpot: String? = null,
    onClick: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    val hue = name.hashCode().and(Int.MAX_VALUE) % 5
    val top = listOf(psk.brandBlue, psk.brandBlueDeep, psk.negative, psk.betslipPanel, psk.brandBlueDark)[hue]

    Column(
        modifier = modifier
            .width(132.dp)
            .clip(PskShapes.card)
            .background(psk.surface)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Brush.verticalGradient(listOf(top, psk.background))),
        ) {
            badge?.let {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(PskShapes.oddsButton)
                        .background(psk.jackpotYellow)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = psk.background,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        jackpot?.let {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(psk.jackpotYellow, psk.jackpotYellowDim)))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.background,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = psk.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** Icon + title + "SEE ALL n" row above every carousel. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    seeAllCount: Int? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    val psk = LocalPskColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = psk.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAll != null) {
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Button, onClick = onSeeAll),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (seeAllCount != null) "SEE ALL " + seeAllCount else "SEE ALL",
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = psk.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Icon, headline, body. The bet slip's empty state is the main user (PRD section 5.4). */
@Composable
fun EmptyState(
    headline: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
) {
    val psk = LocalPskColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.invoke()
        Text(
            text = headline,
            style = MaterialTheme.typography.titleMedium,
            color = psk.textPrimary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = psk.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10, heightDp = 620)
@Composable
private fun CardsPreview() {
    PskTheme {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PromoCard("LOTTO FREE BET", "1 EUR every day", "Play!")
            SectionHeader("PSK Favorites", seeAllCount = 42, onSeeAll = {})
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameTile("Book of Ra", badge = "NEW")
                GameTile("Mega Joker", badge = "JACKPOT", jackpot = "€142,388.21")
            }
            EmptyState(
                headline = "The ticket is empty.",
                body = "If you want to add a bet to your bet slip, review our odds offer " +
                    "and select the bet of your choice.",
            )
        }
    }
}
