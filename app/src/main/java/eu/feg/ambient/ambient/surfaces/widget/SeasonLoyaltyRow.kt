package eu.feg.ambient.ambient.surfaces.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import eu.feg.ambient.AmbientApp
import eu.feg.ambient.ambient.loyalty.LoyaltyState
import eu.feg.ambient.ambient.recap.Recap
import eu.feg.ambient.ambient.recap.RecapPeriod
import eu.feg.ambient.ambient.recap.windowDays
import eu.feg.ambient.ui.nav.Routes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

/**
 * The season card's loyalty line, split out for length.
 *
 * A real seam rather than an arbitrary cut: everything here draws [LoyaltyLine] and nothing
 * else, and LoyaltyLine is the compliance boundary -- a tier, a badge count and one mission's
 * progress, with no field that could hold a perk's cost or a redemption code. The season card
 * is counts-only, and this row is the easiest place to break that.
 */

/**
 * `Silver · 5 badges   ·   Follow three teams  2/3`, on one line.
 *
 * The tier and count are fixed-width and come first; the mission title takes the weight
 * and is the part that truncates, because "Silver · 5 badges" is the fact and the mission
 * is the footnote. No mission (everything done, or the mechanic paused with nothing active)
 * leaves the tier and count on their own rather than an empty separator.
 */
@Composable
internal fun LoyaltyRow(loyalty: LoyaltyLine) {
    val badges = loyalty.badgeCount.toString() + (if (loyalty.badgeCount == 1) " badge" else " badges")
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(
            text = loyalty.tier + " · " + badges,
            style = TextStyle(ColorProvider(WHITE), 11.sp, FontWeight.Medium),
            maxLines = 1,
        )
        if (loyalty.missionTitle != null) {
            Text(
                text = "   ·   " + loyalty.missionTitle + "  " +
                    loyalty.missionProgress + "/" + loyalty.missionTarget,
                style = TextStyle(ColorProvider(GREY), 11.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

/** The same line for the card's description, as a sentence rather than separators. */
internal fun loyaltySpoken(loyalty: LoyaltyLine): String {
    val badges = loyalty.badgeCount.toString() + (if (loyalty.badgeCount == 1) " badge" else " badges")
    val mission = loyalty.missionTitle?.let {
        " Nearest mission: " + it + ", " + loyalty.missionProgress + " of " + loyalty.missionTarget + "."
    } ?: ""
    return loyalty.tier + " tier, " + badges + "." + mission
}
