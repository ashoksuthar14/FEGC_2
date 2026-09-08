package eu.feg.ambient.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ui.theme.LocalPskColors

/** The five tabs from PRD section 4. Everything else lives behind the More sheet. */
enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    SPORT(Routes.SPORT, "Sport", Icons.Filled.SportsSoccer),
    LIVE(Routes.LIVE, "Live", Icons.Filled.Podcasts),
    CASINO(Routes.CASINO, "Casino", Icons.Filled.Casino),
    ARENA(Routes.ARENA, "Arena", Icons.Filled.EmojiEvents),
    MY_BETS(Routes.MY_BETS, "My bets", Icons.Filled.ReceiptLong),
}

@Composable
fun BottomBar(
    currentRoute: String?,
    modifier: Modifier = Modifier,
    onSelect: (BottomTab) -> Unit,
) {
    val psk = LocalPskColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(psk.surface)
            .navigationBarsPadding()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics { this.selected = selected }
                    .clickable(role = Role.Tab) { onSelect(tab) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = tab.icon,
                    // The label beneath is read; a description here would say it twice.
                    contentDescription = null,
                    tint = if (selected) psk.brandBlue else psk.textSecondary,
                    modifier = Modifier.size(23.dp),
                )
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) psk.textPrimary else psk.textSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}
