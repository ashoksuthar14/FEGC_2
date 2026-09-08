package eu.feg.ambient.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskTheme

/** The accessible minimum for anything tappable (Compliance_Conformance §3 Gap C). */
val MinTouchTarget: Dp = 48.dp

/**
 * A 48 dp touch and focus target around an icon that is drawn smaller.
 *
 * The favourite star sits on a 20 dp chip band; growing the layout to 48 dp would push every
 * match row taller than the screenshots. So the outer box keeps the icon's own footprint and
 * the inner, clickable box is *required* to be 48 dp — Compose centres an over-sized child on
 * its parent, which puts 12 dp of tappable, focusable area on each side without moving a
 * single neighbour. The ripple and focus ring draw on the 48 dp circle, as IconButton's do.
 */
@Composable
fun IconTouchTarget(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualSize: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.size(visualSize), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .requiredSize(MinTouchTarget)
                .clip(CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                )
                .focusRing(interaction, CircleShape)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun IconTouchTargetPreview() {
    PskTheme {
        IconTouchTarget(contentDescription = "Remove from favourites", onClick = {}) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = LocalPskColors.current.jackpotYellow,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
