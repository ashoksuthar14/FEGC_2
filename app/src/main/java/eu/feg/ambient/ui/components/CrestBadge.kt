package eu.feg.ambient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.feg.ambient.ambient.identity.ClubTheme
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ui.theme.PskTheme

/**
 * A club's mark: its initials in its colour.
 *
 * Drawn, not downloaded. Club crests are trademarks and shipping one would be the kind of
 * detail that stops a demo becoming a product, so the identity cue is two letters the
 * customer recognises instantly and we are entirely free to draw. It also scales to a 20 dp
 * chip and a 96 dp notification icon without an asset for each.
 *
 * The initials are never white by decree — [ClubTheme.onPrimary] is computed from the club
 * colour, so Hajduk's white circle gets near-black letters and Dinamo's blue gets white.
 */
@Composable
fun CrestBadge(
    theme: ClubTheme,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            // One label for the whole badge: a screen reader should say "Hajduk Split", not
            // spell out two letters that mean nothing read aloud.
            .clearAndSetSemantics { }
            .clip(CircleShape)
            .background(theme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = theme.crestInitials,
            style = TextStyle(
                color = theme.onPrimary,
                // Scales with the badge so the same composable works at 20 dp and at 64 dp.
                fontSize = (size.value * INITIALS_RATIO).sp,
                fontWeight = FontWeight.Black,
            ),
            maxLines = 1,
        )
    }
}

private const val INITIALS_RATIO = 0.38f

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun CrestBadgePreview() {
    PskTheme {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Hajduk first: white on white is the failure this component exists to prevent.
            CrestBadge(ClubThemes.HajdukSplit)
            CrestBadge(ClubThemes.DinamoZagreb)
            CrestBadge(ClubThemes.Varazdin)
            CrestBadge(ClubThemes.Liverpool)
            CrestBadge(ClubThemes.RealMadrid, size = 44.dp)
            CrestBadge(ClubThemes.Default, size = 20.dp)
        }
    }
}
