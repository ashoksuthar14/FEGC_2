package eu.feg.ambient.ui.casino

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.feg.ambient.data.model.CasinoGame
import eu.feg.ambient.data.model.Promo
import eu.feg.ambient.data.model.RecentWin
import eu.feg.ambient.ui.components.GameTile
import eu.feg.ambient.ui.components.PromoCard
import eu.feg.ambient.ui.components.PskChip
import eu.feg.ambient.ui.components.SectionHeader
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

private val CATEGORIES = listOf(
    "Lobby", "Providers", "Jackpots", "Topics", "PSK Favorites",
    "New Games", "Popularly", "Game Show", "Spin Gifts",
)

/** PRD section 5.7 — hero, category chips, per-section carousels, recent wins. */
@Composable
fun CasinoScreen(
    games: List<CasinoGame>,
    wins: List<RecentWin>,
    promos: List<Promo>,
    modifier: Modifier = Modifier,
    onGameClick: (String) -> Unit = {},
) {
    val psk = LocalPskColors.current
    var category by remember { mutableStateOf(CATEGORIES.first()) }
    val sections = games.groupBy { it.section }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero") {
            promos.firstOrNull { it.category == "CASINO" }?.let {
                PromoCard(it.title, it.subtitle, it.cta, Modifier.fillMaxWidth())
            }
        }
        item(key = "categories") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CATEGORIES, key = { it }) { name ->
                    PskChip(name, selected = name == category, onClick = { category = name })
                }
            }
        }

        sections.forEach { (section, sectionGames) ->
            item(key = "header-" + section) {
                SectionHeader(section, seeAllCount = sectionGames.size, onSeeAll = {})
            }
            item(key = "row-" + section) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sectionGames, key = { it.id }) { game ->
                        GameTile(
                            name = game.name,
                            badge = game.badge,
                            jackpot = game.jackpot,
                            onClick = { onGameClick(game.id) },
                        )
                    }
                }
            }
        }

        item(key = "wins-header") { SectionHeader("Recent wins") }
        items(wins, key = { it.id }) { win ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(PskShapes.card)
                    .background(psk.surfaceRaised)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = win.player,
                        style = MaterialTheme.typography.bodyMedium,
                        color = psk.textPrimary,
                    )
                    Text(
                        text = win.game,
                        style = MaterialTheme.typography.labelSmall,
                        color = psk.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = win.amount,
                    style = MaterialTheme.typography.labelMedium,
                    color = psk.jackpotYellow,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Tapping a tile opens this, never a game (PRD section 5.7). */
@Composable
fun GameLoadingScreen(modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Game loading…",
            style = MaterialTheme.typography.titleMedium,
            color = psk.textSecondary,
        )
    }
}
