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
 * N5 on the home screen: the season's counts. Counts only -- never money.
 *
 * The shell is not this widget's business: [GlassCard] and [WidgetHeader] draw the card and
 * its opening line, so the five widgets read as one family. A widget that draws its own card
 * is a bug, not a variation.
 */
class SeasonWidget : GlanceAppWidget() {

    // Exact, so the body can decide by width -- the same reason the Live Slip card does.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val club = WidgetStateStore(context).club()
        // Built here, not in the body: the builder suspends, and the recap is the same all
        // day so a value frozen for the composition's life is the right value. The
        // builder is asked directly rather than through recapForWidget(), which marks the
        // recap seen -- that is the one-shot N5 card's business, and this widget is
        // permanent.
        val recap = (context.applicationContext as? AmbientApp)?.container?.let { c ->
            val now = c.clock.now()
            c.recapBuilder.build(RecapPeriod.SEASON, now) ?: c.recapBuilder.build(RecapPeriod.MONTH, now)
        }
        provideContent {
            // Keyed on the store's version, like AmbientWidget: Glance keeps a composition
            // alive between updates, so anything read once before provideContent stays frozen
            // and the widget silently stops changing. This is what makes "one protection
            // change updates every widget" true rather than nearly true.
            val version by WidgetStoreVersion.flow.collectAsState()
            CompositionLocalProvider(
                LocalClubTheme provides remember(version) { WidgetStateStore(context).club() },
            ) {
                SeasonWidgetBody(recap)
            }
        }
    }
}

/**
 * Everything the card is allowed to draw.
 *
 * THIS CLASS IS THE COMPLIANCE RULE, NOT A CONVENIENCE. The composables below take this and
 * never the [Recap], so there is no field a future edit could reach that names a stake, a
 * return or a balance -- the card literally cannot show money however it is laid out.
 * [Recap] has no such field today either; this is what keeps that true at the point of
 * drawing if the model ever grows one. The period and its dates ride along because the
 * header needs them, and a date is not a count of anything.
 */
private data class SeasonCounts(
    val period: RecapPeriod,
    val from: Instant,
    val to: Instant,
    val matchesFollowed: Int,
    val predictionsRight: Int,
    val predictionsTotal: Int,
    val longestStreak: Int,
    val topTeam: String?,
    val topTeamCount: Int,
) {
    companion object {
        fun from(recap: Recap) = SeasonCounts(
            period = recap.period,
            from = recap.from,
            to = recap.to,
            matchesFollowed = recap.matchesFollowed,
            predictionsRight = recap.predictionsRight,
            predictionsTotal = recap.predictionsTotal,
            longestStreak = recap.longestStreak,
            topTeam = recap.topTeam,
            topTeamCount = recap.topTeamCount,
        )
    }
}

@Composable
internal fun SeasonWidgetBody(recap: Recap?) {
    if (recap == null) {
        EmptySeasonCard()
        return
    }
    SeasonCard(SeasonCounts.from(recap))
}

/** The same card on sample counts, for the Surface Lab and a future glance preview. */
@Composable
internal fun SeasonWidgetPreviewBody() {
    val now = Clock.System.now()
    SeasonCard(
        SeasonCounts(
            period = RecapPeriod.SEASON,
            from = now - 300.days,
            to = now,
            matchesFollowed = 23,
            predictionsRight = 14,
            predictionsTotal = 21,
            longestStreak = 5,
            topTeam = "Sparta",
            topTeamCount = 6,
        ),
    )
}

// ---- the card --------------------------------------------------------------------------

/**
 * Header, then the hero count with the two secondary counts beside it, then the top team and
 * the share control on one line. Side by side rather than stacked because a 4x2 is about
 * 110dp tall and a stacked version put the Share button below the fold.
 */
@Composable
private fun SeasonCard(counts: SeasonCounts) {
    val club = LocalClubTheme.current
    val title = when (counts.period) {
        RecapPeriod.SEASON -> "Your season"
        RecapPeriod.MONTH -> "Your month"
    }
    val predictions = if (counts.predictionsTotal > 0) {
        counts.predictionsRight.toString() + " of " + counts.predictionsTotal + " predictions right"
    } else {
        null
    }
    val streak = if (counts.longestStreak >= 2) counts.longestStreak.toString() + "-day streak" else null
    val topTeam = counts.topTeam?.let { topTeamLine(it, counts.topTeamCount, counts.period) }
    val spoken = title + ", " + periodLabel(counts.from, counts.to) + ". " +
        counts.matchesFollowed + " matches followed" +
        (predictions?.let { ", " + it } ?: "") +
        (streak?.let { ", a " + it } ?: "") +
        (topTeam?.let { ". " + it } ?: "") + "."

    GlassCard(description = spoken) {
        WidgetHeader(
            title = title,
            subtitle = periodLabel(counts.from, counts.to),
            titleColor = club.accentOnDark,
        )
        Spacer(GlanceModifier.height(4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column {
                WidgetFigure(label = "matches followed", value = counts.matchesFollowed.toString())
            }
            Spacer(GlanceModifier.width(16.dp))
            // Omitted rather than shown as "0 of 0" when nothing settled, as the recap
            // builder omits it: a prediction is a leg outcome, never money.
            Column(modifier = GlanceModifier.defaultWeight()) {
                if (predictions != null) SecondaryLine(predictions)
                if (streak != null) SecondaryLine(streak)
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = topTeam ?: "",
                style = TextStyle(ColorProvider(club.accentOnDark), 12.sp, FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            // A widget cannot show a chooser, so the tap opens the app; the in-app RecapCard
            // (ui/recap/RecapCard.kt) owns the real share sheet.
            WidgetActionButton(
                label = "Share",
                description = "Share your " + (if (counts.period == RecapPeriod.SEASON) "season" else "month"),
                action = openRoute(ROUTE_MY_BETS),
                modifier = GlanceModifier.width(88.dp),
            )
        }
    }
}

@Composable
private fun SecondaryLine(text: String) {
    Text(text = text, style = TextStyle(ColorProvider(WHITE), 12.sp), maxLines = 1)
}

/** Nothing to count yet: the card says where the counts come from and offers the first step. */
@Composable
private fun EmptySeasonCard() {
    GlassCard(description = "Your season starts here. Follow a club to start counting the matches you follow.") {
        WidgetHeader(title = "Your season")
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = "Your season starts here",
            style = TextStyle(ColorProvider(WHITE), 15.sp, FontWeight.Medium),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = "Follow a club and the matches you watch add up",
            style = TextStyle(ColorProvider(GREY), 12.sp),
            maxLines = 2,
        )
        Spacer(GlanceModifier.defaultWeight())
        WidgetActionButton(
            label = "Follow a club",
            description = "Open the app and choose a club to follow",
            action = openRoute(Routes.SPORT),
        )
    }
}

// ---- words -------------------------------------------------------------------------------

/**
 * "Sparta, every single week" when the count earned it, "Sparta, 3 times" when it did not,
 * just the name when it was once. The same threshold RecapLines uses: a team followed at
 * least once per week of the window.
 */
private fun topTeamLine(team: String, count: Int, period: RecapPeriod): String {
    val weeks = period.windowDays / 7
    return when {
        count >= weeks -> team + ", every single week"
        count >= 2 -> team + ", " + count + " times"
        else -> team
    }
}

/** "12 Aug – 8 Sep", in the customer's zone; the recap's window is days, so the day is enough. */
private fun periodLabel(from: Instant, to: Instant): String =
    dayMonth(from) + " – " + dayMonth(to)

private fun dayMonth(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return date.dayOfMonth.toString() + " " + month
}
