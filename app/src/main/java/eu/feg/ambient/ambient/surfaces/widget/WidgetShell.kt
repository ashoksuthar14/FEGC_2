package eu.feg.ambient.ambient.surfaces.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
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

/**
 * The line every widget in the family opens with.
 *
 * Five widgets that each invented their own header would read as five apps. One header, one
 * crest size, one type scale — so the set looks like a family on a home screen, which is the
 * entire point of shipping five rather than one.
 *
 * [GlassCard] in GlassShell.kt is the other half of the shell. Its signature carries the
 * card's accessibility description and its tap target rather than a ClubTheme, because the
 * club is already in scope through LocalClubTheme and a card without a spoken description is
 * a card a screen-reader user cannot use.
 */
@Composable
internal fun WidgetHeader(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    crest: Boolean = true,
    crestSize: Dp = 22.dp,
    titleColor: Color = WHITE,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (crest) {
            WidgetCrest(size = crestSize)
            Spacer(GlanceModifier.width(7.dp))
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title.uppercase(),
                style = TextStyle(ColorProvider(titleColor), 11.sp, FontWeight.Bold),
                maxLines = 1,
            )
            if (subtitle != null) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = TextStyle(ColorProvider(GREY), 12.sp),
                    maxLines = 1,
                )
            }
        }
        if (trailing != null) {
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = trailing,
                style = TextStyle(ColorProvider(GREY), 11.sp, FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

/** The hero figure and its caption, shared by the season, protection and club widgets. */
@Composable
internal fun WidgetFigure(label: String, value: String, unit: String? = null) {
    Text(text = label.uppercase(), style = TextStyle(ColorProvider(GREY), 10.sp, FontWeight.Medium), maxLines = 1)
    Row(verticalAlignment = Alignment.Vertical.Bottom) {
        Text(text = value, style = TextStyle(ColorProvider(WHITE), 30.sp, FontWeight.Bold), maxLines = 1)
        if (unit != null) {
            Spacer(GlanceModifier.width(4.dp))
            Text(text = unit, style = TextStyle(ColorProvider(GREY), 12.sp, FontWeight.Medium), maxLines = 1)
        }
    }
}

internal val WHITE = Color(0xFFFFFFFF)
internal val GREY = Color(0xFF939AA8)
internal val WHITE_10 = Color(0x1AFFFFFF)
internal val LIVE_GREEN = Color(0xFF35D07F)
internal val WARN_AMBER = Color(0xFFF5C518)
