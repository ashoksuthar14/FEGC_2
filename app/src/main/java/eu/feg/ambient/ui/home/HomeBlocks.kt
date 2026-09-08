package eu.feg.ambient.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.core.formatMoney
import eu.feg.ambient.core.formatOdds
import eu.feg.ambient.data.model.ArenaTip
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.Promo
import eu.feg.ambient.ui.components.OddsButton
import eu.feg.ambient.ui.components.PromoCard
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import eu.feg.ambient.ui.theme.PskTheme
import kotlinx.coroutines.delay

/** Block 1 — four heroes, auto-advancing every 5s, with page dots. */
@Composable
fun PromoCarousel(promos: List<Promo>, modifier: Modifier = Modifier) {
    if (promos.isEmpty()) return
    val psk = LocalPskColors.current
    val pages = promos.take(4)
    val state = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(pages.size) {
        while (true) {
            delay(5_000)
            state.animateScrollToPage((state.currentPage + 1) % pages.size)
        }
    }

    Column(modifier) {
        HorizontalPager(
            state = state,
            pageSpacing = 8.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 32.dp),
        ) { page ->
            val promo = pages[page]
            PromoCard(
                title = promo.title,
                subtitle = promo.subtitle,
                cta = promo.cta,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            pages.indices.forEach { index ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (index == state.currentPage) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(if (index == state.currentPage) psk.textPrimary else psk.surfaceRaised),
                )
            }
        }
    }
}

/** Block 2 — the quick-link icon rail. */
@Composable
fun QuickLinkRow(modifier: Modifier = Modifier, onClick: (String) -> Unit = {}) {
    val psk = LocalPskColors.current
    val links = listOf(
        "Promo", "Aviator", "MM", "Missions", "Casino", "eFootball",
        "eBasketball", "Lotto", "PSK Champions", "Live Casino", "PSK Arena", "Forum",
    )
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(links, key = { it }) { label ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(62.dp)
                    .clickable { onClick(label) },
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(psk.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label.take(2).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = psk.textPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = psk.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

/** Block 4 — "STARTS IN 3 HOURS" highlight cards: one named outcome, one big price. */
@Composable
fun StartingSoonCard(
    match: Match,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val psk = LocalPskColors.current
    val outcome = match.markets.firstOrNull()?.outcomes?.minByOrNull { it.odds }

    Column(
        modifier = modifier
            .width(230.dp)
            .clip(PskShapes.card)
            .background(psk.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "STARTS IN 3 HOURS",
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
        )
        Text(
            text = (match.home.name + " - " + match.away.name).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = psk.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = match.home.name + " win",
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        outcome?.let {
            OddsButton(label = "", value = formatOdds(it.odds), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Block 6 — a player-props card: player name, then prop rows with a price each. */
@Composable
fun PlayerPropsCard(
    player: String,
    fixture: String,
    kickoff: String,
    props: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
) {
    val psk = LocalPskColors.current
    Column(
        modifier = modifier
            .width(250.dp)
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = player.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = psk.textPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = fixture.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = kickoff.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
            )
        }
        props.forEach { (label, odds) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = psk.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                OddsButton(
                    label = "",
                    value = formatOdds(odds),
                    modifier = Modifier.width(74.dp),
                )
            }
        }
    }
}

/** Block 10 — a novelty market with two outcomes and the share-of-stakes split. */
@Composable
fun SpecialBetsCard(modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "SPECIAL",
                style = MaterialTheme.typography.labelSmall,
                color = psk.jackpotYellow,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "ENDS IN 26 DAYS",
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
            )
        }
        Text(
            text = "Will the release of GTA VI be delayed from November 19, 2026?",
            style = MaterialTheme.typography.titleMedium,
            color = psk.textPrimary,
        )
        Row(Modifier.fillMaxWidth()) {
            ShareOfStakes("69.3% SHARE OF STAKES", psk.positive, 0.693f)
            ShareOfStakes("30.7% SHARE OF STAKES", psk.negative, 0.307f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OddsButton("That", "5.20", Modifier.weight(1f))
            OddsButton("Not", "1.12", Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowScope.ShareOfStakes(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    weight: Float,
) {
    val psk = LocalPskColors.current
    Column(Modifier.weight(weight)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = psk.textPrimary,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = 0.35f))
                .padding(6.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(color),
        )
    }
}

/** Block 11 — the first few Arena rows, as a taster for the full screen. */
@Composable
fun ArenaPreviewRow(tip: ArenaTip, modifier: Modifier = Modifier, onCopy: () -> Unit = {}) {
    val psk = LocalPskColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surfaceRaised)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(psk.brandBlue),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = tip.username.take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = psk.textPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = tip.username,
                style = MaterialTheme.typography.bodyMedium,
                color = psk.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tip.inspirationLabel,
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = tip.eventCount.toString() + " events",
                style = MaterialTheme.typography.labelSmall,
                color = psk.textSecondary,
            )
            Text(
                text = formatMoney(tip.possiblePayment),
                style = MaterialTheme.typography.labelMedium,
                color = psk.positive,
                fontWeight = FontWeight.SemiBold,
            )
        }
        PskChip("Copy", onClick = onCopy)
    }
}

/** Block 12 — the compliance footer. Responsible gaming and self-exclusion must be here. */
@Composable
fun HomeFooter(modifier: Modifier = Modifier, onLink: (String) -> Unit = {}) {
    val psk = LocalPskColors.current
    val links = listOf(
        "About us", "Game rules", "Responsible gaming", "Self-exclusion form",
        "User Safety Guide", "Personal data protection", "Branches", "Help",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        links.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { link ->
                    Text(
                        text = link,
                        style = MaterialTheme.typography.labelMedium,
                        color = psk.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onLink(link) },
                    )
                }
            }
        }
        Text(
            text = "Payment methods: PSK Terminal · SEPA · Revolut · aircash · Visa · Mastercard · " +
                "Diners · Apple Pay · Skrill · paysafecard",
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
        )
        Text(
            text = "© 2026 Hattrick-PSK d.o.o. All rights reserved. Authorised organiser of games of " +
                "chance under approval CLASS: UP/I-461-04/25-02/493, REGISTRATION NUMBER: " +
                "513-07-21-01-12-2, Zagreb. Participation is permitted only for persons over the " +
                "age of 18. Games of chance can be addictive. Play responsibly!",
            style = MaterialTheme.typography.labelSmall,
            color = psk.textSecondary,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10, heightDp = 700)
@Composable
private fun HomeBlocksPreview() {
    PskTheme {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            QuickLinkRow()
            SpecialBetsCard()
            HomeFooter()
        }
    }
}
