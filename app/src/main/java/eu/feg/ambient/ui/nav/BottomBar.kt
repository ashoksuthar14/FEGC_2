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
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.ui.unit.sp
import eu.feg.ambient.ui.theme.LocalPskColors

/**
 * The bar, and what this product wants to be judged on.
 *
 * The PRD's five were Sport, Live, Casino, Arena and My bets, which is psk.hr's own set. Two
 * of the things that make THIS app different -- scanning a paper slip, and a loyalty scheme
 * with no gambling in it -- were reachable only from inside another screen or from the More
 * sheet, which is where features go to be missed. They are doors now.
 *
 * Arena is the one that stepped back, because it is the only one of the five that is a
 * content feed rather than a thing the customer does; it keeps its More entry and its screen.
 * Six is the ceiling: a seventh tab is a label nobody can read.
 */
enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    SPORT(Routes.SPORT, "Sport", Icons.Filled.SportsSoccer),
    LIVE(Routes.LIVE, "Live", Icons.Filled.Podcasts),
    CASINO(Routes.CASINO, "Casino", Icons.Filled.Casino),
    SCAN(Routes.SCAN_TICKET, "Scan", Icons.Filled.QrCodeScanner),
    REWARDS(Routes.REWARDS, "Rewards", Icons.Filled.EmojiEvents),
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
            .padding(vertical = 6.dp, horizontal = 2.dp),
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
                    modifier = Modifier.size(21.dp),
                )
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelSmall,
                    // Six labels across a phone: the longest ("My bets") is what decides this,
                    // and a label that ellipsises is worse than one a point smaller.
                    fontSize = 10.sp,
                    color = if (selected) psk.textPrimary else psk.textSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}
