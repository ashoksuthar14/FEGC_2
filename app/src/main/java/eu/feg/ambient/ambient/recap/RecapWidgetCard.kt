package eu.feg.ambient.ambient.recap

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import eu.feg.ambient.ambient.surfaces.widget.LocalClubTheme
import eu.feg.ambient.ambient.surfaces.widget.SpeakWidgetAction
import eu.feg.ambient.ambient.surfaces.widget.WidgetCard
import eu.feg.ambient.ambient.surfaces.widget.WidgetCrest
import eu.feg.ambient.ambient.surfaces.widget.WidgetHero
import eu.feg.ambient.ambient.surfaces.widget.WidgetIconButton
import eu.feg.ambient.ambient.surfaces.widget.WidgetText
import eu.feg.ambient.ambient.surfaces.widget.openRoute
import eu.feg.ambient.ui.nav.Routes

/**
 * N5 on the home screen: the recap as a 2x2 card, for [WidgetState.Recap].
 *
 * Same hierarchy as every other card — one number set large, a caption above it, two quiet
 * lines under it — because the widget is read at arm's length in half a second and the
 * number is the whole message. The number is matches followed: it is the count that is true
 * of every customer with a recap, where a prediction count is only true of those with a slip
 * that settled.
 *
 * NO MONEY, structurally. [Recap] has no field for a stake, a return or a balance, so this
 * card cannot show one however it is laid out. That is what lets it render unchanged in Calm
 * Mode, and it is why the card is drawn in the club's colours: nothing on it needs the
 * operator's frame.
 *
 * Club theming is automatic through [LocalClubTheme], which AmbientWidget provides around
 * every state. This composable is not yet dispatched from WidgetBody — the
 * `is WidgetState.Recap -> RecapCard(state.recap)` branch is added at assembly.
 */
@Composable
internal fun RecapCard(recap: Recap) {
    val theme = LocalClubTheme.current
    WidgetCard(
        // N1: the whole card reads as the narrator's one spoken sentence, not as a caption,
        // a number and two lines announced separately.
        description = recap.spokenText,
        onClick = openRoute(Routes.WHY_THIS),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            WidgetCrest(size = 22.dp)
            Spacer(GlanceModifier.width(7.dp))
            Text(
                text = recap.headline.uppercase(),
                style = TextStyle(ColorProvider(theme.accentOnDark), 11.sp, FontWeight.Bold),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            // The streak takes the trailing slot the minute takes on a live card: the one
            // small figure that is allowed next to the club's name.
            if (recap.longestStreak >= 2) {
                Text(
                    text = recap.longestStreak.toString() + "-DAY STREAK",
                    style = WidgetText.label,
                    maxLines = 1,
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        WidgetHero(label = "Matches followed", value = recap.matchesFollowed.toString())
        Spacer(GlanceModifier.height(6.dp))
        // A prediction is a leg outcome, never money. Omitted rather than shown as "0 of 0"
        // when nothing settled in the period, for the same reason the builder omits it.
        if (recap.predictionsTotal > 0) {
            Text(
                text = recap.predictionsRight.toString() + " of " + recap.predictionsTotal +
                    " predictions right",
                style = WidgetText.meta,
                maxLines = 1,
            )
        }
        recap.topTeam?.let { team ->
            Text(text = topTeamLine(team, recap.topTeamCount), style = WidgetText.meta, maxLines = 1)
        }
        Spacer(GlanceModifier.defaultWeight())
        // The speaker is present so the card has the same control every other card has and
        // a TalkBack user finds it in the same place. SpeakWidgetAction reads the stored
        // slip, which is not the recap; a recap-aware speak action is wired at assembly,
        // alongside the WidgetBody branch, so the button and the card ship together.
        WidgetIconButton(
            glyph = "🔊",
            description = "Read this recap out loud",
            action = actionRunCallback<SpeakWidgetAction>(),
        )
    }
}

/** "Sparta · 6 matches", or just the name when it was followed once. */
private fun topTeamLine(team: String, count: Int): String =
    if (count >= 2) team + " · " + count + " matches" else team
