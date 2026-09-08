package eu.feg.ambient.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ui.theme.LocalPskColors

/**
 * A 2 dp outline while the element holds keyboard or switch-access focus.
 *
 * The custom clickables (odds cells, chips, match rows) draw their own backgrounds, so the
 * default Compose focus indication — nothing, on a dark surface — leaves a keyboard user
 * with no idea where they are. The ring defaults to [eu.feg.ambient.ui.theme.PskColors.textPrimary]
 * because the two fills a focused element is most likely to have, brandBlue and oddsCell,
 * would both swallow a blue ring.
 *
 * Apply it *after* [androidx.compose.foundation.clickable] with the same [interactionSource],
 * and before any clip that would cut the border off.
 */
@Composable
fun Modifier.focusRing(
    interactionSource: InteractionSource,
    shape: Shape,
    color: Color = LocalPskColors.current.textPrimary,
): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    return if (focused) border(FOCUS_RING_WIDTH, color, shape) else this
}

private val FOCUS_RING_WIDTH = 2.dp
