package eu.feg.ambient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ambient.identity.ClubTheme
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import eu.feg.ambient.ui.theme.PskTheme

/**
 * "My club" — the one control that sets the theme, and the only place in the app that shows
 * all eight at once.
 *
 * Deliberately light. N6's whole argument is that this should read as the customer's corner
 * of an operator's app rather than a reskin, so the picker is one strip near the top of Home
 * and the accent it produces appears in a handful of places, not everywhere.
 *
 * Selection is one tap, because the demo turns on it: pick Hajduk, look at the lock screen.
 */
@Composable
fun MyClubRow(
    selected: ClubTheme,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val psk = LocalPskColors.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CrestBadge(selected, size = 24.dp)
            Text(
                text = if (selected.clubId.isEmpty()) "Pick your club" else selected.name,
                style = MaterialTheme.typography.titleSmall,
                color = psk.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (selected.clubId.isNotEmpty()) {
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.labelMedium,
                    color = psk.textSecondary,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(PskShapes.chip)
                        .clickable(onClickLabel = "Stop following a club") { onSelect(null) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ClubThemes.all, key = { it.clubId }) { club ->
                ClubPill(club, isSelected = club.clubId == selected.clubId) {
                    onSelect(club.clubId)
                }
            }
        }
    }
}

/**
 * The pill fills with the club's own colour when chosen, which is the point: the customer
 * sees the exact accent they are about to put on their lock screen before they commit to it.
 * The label uses [ClubTheme.onPrimary] so Hajduk's white pill still has readable text.
 */
@Composable
private fun ClubPill(club: ClubTheme, isSelected: Boolean, onClick: () -> Unit) {
    val psk = LocalPskColors.current
    Row(
        modifier = Modifier
            // The pill keeps its 34 dp look; the tappable area around it grows to 48 dp.
            .minimumInteractiveComponentSize()
            .clip(PskShapes.chip)
            .background(if (isSelected) club.primary else psk.surfaceVariant)
            // A white club on a dark card needs an edge, or the pill has no visible bounds.
            .border(1.dp, club.primary, PskShapes.chip)
            .clickable(onClickLabel = "Set " + club.name + " as my club", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CrestBadge(club, size = 20.dp)
        Text(
            text = club.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) club.onPrimary else psk.textSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * "Set as my club" on a single team — for the Match screen, where the customer is looking at
 * exactly one club and the eight-wide strip would be noise.
 */
@Composable
fun SetAsMyClubChip(
    teamName: String,
    selected: ClubTheme,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only the eight themed clubs can be set; every other fixture shows nothing at all rather
    // than a control that would silently do nothing.
    val club = ClubThemes.byName(teamName) ?: return
    val isMine = club.clubId == selected.clubId
    val psk = LocalPskColors.current
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(PskShapes.chip)
            .background(if (isMine) club.primary else psk.surfaceVariant)
            .clickable(
                enabled = !isMine,
                onClickLabel = "Set " + club.name + " as my club",
            ) { onSelect(club.clubId) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CrestBadge(club, size = 18.dp)
        Text(
            text = if (isMine) "My club" else "Set as my club",
            style = MaterialTheme.typography.labelMedium,
            color = if (isMine) club.onPrimary else psk.textSecondary,
            maxLines = 1,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun MyClubRowPreview() {
    PskTheme {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MyClubRow(selected = ClubThemes.HajdukSplit, onSelect = {})
            MyClubRow(selected = ClubThemes.Default, onSelect = {})
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetAsMyClubChip("Dinamo Zagreb", ClubThemes.HajdukSplit, onSelect = {})
                SetAsMyClubChip("Hajduk Split", ClubThemes.HajdukSplit, onSelect = {})
            }
        }
    }
}
