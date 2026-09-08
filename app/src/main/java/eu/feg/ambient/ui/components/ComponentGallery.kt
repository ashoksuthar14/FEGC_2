package eu.feg.ambient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskTheme

/**
 * Step 2 scaffolding: the Compose previews made visible on a real device, since previews
 * alone need Android Studio. Not a PRD screen — the nav host replaces this at step 10.
 */
@Composable
fun ComponentGallery(modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    var selected by remember { mutableIntStateOf(-1) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(psk.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "odds-header") { GalleryHeader("OddsButton states") }
        item(key = "odds-row") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OddsButton("1", "6.50", Modifier.weight(1f), onClick = { selected = 0 })
                OddsButton(
                    "X", "5.50", Modifier.weight(1f),
                    state = if (selected == 1) OddsState.SELECTED else OddsState.DEFAULT,
                    onClick = { selected = 1 },
                )
                OddsButton("2", "1.50", Modifier.weight(1f), isTop = true, onClick = { selected = 2 })
                OddsButton("2", "1.45", Modifier.weight(1f), state = OddsState.LOCKED)
            }
        }
        item(key = "flash-header") { GalleryHeader("Odds flash — up / down") }
        item(key = "flash-row") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OddsButton("NK Lucko", "6.00", Modifier.weight(1f), flash = OddsFlash.UP)
                OddsButton("Draw", "3.40", Modifier.weight(1f))
                OddsButton("Solin", "1.50", Modifier.weight(1f), flash = OddsFlash.DOWN)
            }
        }
        item(key = "rows-header") { GalleryHeader("MatchRow") }
        items(
            listOf(
                samplePrematchRow,
                sampleLiveRow,
                sampleTopBadgeRow,
                sampleDoubleChanceRow,
                sampleLockedRow,
            ),
            key = { it.id },
        ) { row ->
            MatchRow(row, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GalleryHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = LocalPskColors.current.textSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10, heightDp = 700)
@Composable
private fun ComponentGalleryPreview() {
    PskTheme { ComponentGallery() }
}
