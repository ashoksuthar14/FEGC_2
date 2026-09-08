package eu.feg.ambient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskTheme

@Preview(name = "Match rows", showBackground = true, backgroundColor = 0xFF0E0E10, heightDp = 460)
@Composable
private fun MatchRowPreview() {
    PskTheme {
        Column(
            modifier = Modifier
                .width(360.dp)
                .background(LocalPskColors.current.background)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MatchRow(samplePrematchRow)
            MatchRowDivider()
            MatchRow(sampleLiveRow)
            MatchRowDivider()
            MatchRow(sampleTopBadgeRow)
            MatchRowDivider()
            MatchRow(sampleLockedRow)
        }
    }
}

@Preview(name = "Prematch row", showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun PrematchRowPreview() {
    PskTheme { Column(Modifier.width(360.dp).padding(12.dp)) { MatchRow(samplePrematchRow) } }
}

@Preview(name = "Live row with score", showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun LiveRowPreview() {
    PskTheme { Column(Modifier.width(360.dp).padding(12.dp)) { MatchRow(sampleLiveRow) } }
}

@Preview(name = "TOP badge row", showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun TopBadgeRowPreview() {
    PskTheme { Column(Modifier.width(360.dp).padding(12.dp)) { MatchRow(sampleTopBadgeRow) } }
}

@Preview(name = "Locked odds row", showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun LockedRowPreview() {
    PskTheme { Column(Modifier.width(360.dp).padding(12.dp)) { MatchRow(sampleLockedRow) } }
}
