package eu.feg.ambient.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** PRD section 3.3 — 4dp grid, screen padding 12dp. */
object PskShapes {
    val card = RoundedCornerShape(8.dp)
    val chip = RoundedCornerShape(16.dp)
    val oddsButton = RoundedCornerShape(6.dp)
}

val PskMaterialShapes = Shapes(
    extraSmall = PskShapes.oddsButton,
    small = PskShapes.oddsButton,
    medium = PskShapes.card,
    large = PskShapes.card,
    extraLarge = PskShapes.chip,
)
