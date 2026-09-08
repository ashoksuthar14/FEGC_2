package eu.feg.ambient.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.feg.ambient.ui.theme.LocalPskColors
import eu.feg.ambient.ui.theme.PskShapes
import eu.feg.ambient.ui.theme.PskTheme

/**
 * The 400ms skeleton the PRD asks for on first load (section 5.1), shaped like a MatchRow
 * so the list does not jump when the real rows arrive.
 */
@Composable
fun ShimmerRow(modifier: Modifier = Modifier) {
    val psk = LocalPskColors.current
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PskShapes.card)
            .background(psk.surfaceRaised)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Block(width = 120.dp, height = 14.dp, alpha = alpha)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Block(width = 150.dp, height = 14.dp, alpha = alpha)
                Block(width = 130.dp, height = 14.dp, alpha = alpha)
            }
            repeat(3) {
                Box(
                    Modifier
                        .width(50.dp)
                        .height(44.dp)
                        .clip(PskShapes.oddsButton)
                        .alpha(alpha)
                        .background(psk.oddsCell),
                )
            }
        }
    }
}

@Composable
private fun Block(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, alpha: Float) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(PskShapes.oddsButton)
            .alpha(alpha)
            .background(LocalPskColors.current.oddsCell),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun ShimmerRowPreview() {
    PskTheme {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShimmerRow()
            ShimmerRow()
        }
    }
}
