package eu.feg.ambient.ui.promo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.feg.ambient.data.model.Promo
import eu.feg.ambient.ui.components.PromoCard
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes

/**
 * PRD section 5.9. This screen is the pitch's argument in Phase 2: offers live here, inside
 * the app, which is why the OS surfaces can stay offer-free.
 */
@Composable
fun PromoScreen(
    promos: List<Promo>,
    modifier: Modifier = Modifier,
    onPromoClick: (String) -> Unit = {},
) {
    val psk = LocalPskColors.current
    val hero = promos.firstOrNull { it.endsIn != null } ?: promos.firstOrNull()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title", span = { GridItemSpanFull() }) {
            Text(
                text = "Promo",
                style = MaterialTheme.typography.titleLarge,
                color = psk.textPrimary,
            )
        }

        hero?.let { promo ->
            item(key = "hero", span = { GridItemSpanFull() }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    promo.endsIn?.let {
                        Row(
                            Modifier
                                .clip(PskShapes.chip)
                                .background(psk.negative)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = psk.textPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    PromoCard(
                        title = promo.title,
                        subtitle = promo.subtitle,
                        cta = promo.cta,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onPromoClick(promo.id) },
                    )
                }
            }
        }

        items(promos.filter { it.id != hero?.id }, key = { it.id }) { promo ->
            PromoCard(
                title = promo.title,
                subtitle = promo.subtitle,
                cta = promo.cta,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onPromoClick(promo.id) },
            )
        }
    }
}

private fun GridItemSpanFull() = androidx.compose.foundation.lazy.grid.GridItemSpan(2)
