package eu.feg.ambient.ambient.surfaces.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import eu.feg.ambient.R
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.identity.CrestBitmap
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.surfaces.SlipSurfaceState
import eu.feg.ambient.ambient.surfaces.SpokenSurface

/**
 * The Live state as a match card: competition, score, timeline, actions.
 *
 * COMPLIANCE -- nothing here can name money because [SlipSurfaceState] has no field for it,
 * and the one figure about the slip is legs won. In CALM the card is the same card with the
 * slip removed from it: no legs caption, and the actions become the way to keep following the
 * match and the way out. UNVERIFIED and BLOCKED never reach here; WidgetBody routes those to
 * the Protected card before dispatching.
 *
 * The header names the competition, not the club. The club is present as the accent and the
 * crest -- a header reading HAJDUK SPLIT over a Liverpool match would be a card contradicting
 * itself.
 */
@Composable
internal fun LiveMatchCard(slip: SlipSurfaceState, feedback: FeedbackMark? = null) {
    val calm = slip.protection == ProtectionState.CALM
    val fontScale = LocalContext.current.resources.configuration.fontScale
    // At large font sizes the names and the score give way before anything clips.
    val teamSp = if (fontScale >= 1.5f) 16.sp else 19.sp
    val scoreSp = if (fontScale >= 1.5f) 40.sp else 50.sp

    GlassCard(
        description = SpokenSurface.forSlip(slip) ?: (slip.activeMatch ?: "Match in progress"),
        onClick = openRoute(ROUTE_MY_BETS),
    ) {
        // Heights are tighter than the mockup's: its zones sum past what a 4x3 widget is
        // given, and a card whose actions sit below the fold has no actions.
        Header(slip)
        Spacer(GlanceModifier.height(6.dp))
        ScoreRow(slip, teamSp, scoreSp)
        Spacer(GlanceModifier.height(4.dp))
        Timeline(slip, showLegs = !calm)
        Divider()
        if (calm) CalmActions(slip) else Actions(slip, feedback)
    }
}

// ---- zone 1 · header --------------------------------------------------------------------

@Composable
private fun Header(slip: SlipSurfaceState) {
    val theme = LocalClubTheme.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Crest(theme = theme, size = 44.dp, radius = 12.dp, bordered = true)
        Spacer(GlanceModifier.width(14.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = (slip.competition ?: slip.activeMatch ?: "Live match").uppercase(),
                style = TextStyle(ColorProvider(WHITE), 19.sp, FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = slip.region ?: (slip.period ?: ""),
                style = TextStyle(ColorProvider(GREY), 14.sp),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(14.dp))
        LivePill()
        Spacer(GlanceModifier.width(14.dp))
        Text(
            text = (slip.minute ?: 0).toString() + "'",
            style = TextStyle(ColorProvider(WHITE), 23.sp, FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/** A green dot and the word, in a translucent pill. Green means "on", not the club. */
@Composable
private fun LivePill() {
    Row(
        modifier = GlanceModifier
            .height(34.dp)
            .background(ColorProvider(WHITE_10))
            .cornerRadius(17.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.size(9.dp).background(ColorProvider(LIVE_GREEN)).cornerRadius(5.dp),
            contentAlignment = Alignment.Center,
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Text(text = "LIVE", style = TextStyle(ColorProvider(LIVE_GREEN), 14.sp, FontWeight.Bold), maxLines = 1)
    }
}

// ---- zone 2 · score ---------------------------------------------------------------------

/**
 * Five children, the names weighted equally on either side, so the score is centred on the
 * card rather than on whatever is left after a long name. Neither side is coloured for
 * leading or losing: the number carries that.
 */
@Composable
private fun ScoreRow(slip: SlipSurfaceState, teamSp: androidx.compose.ui.unit.TextUnit, scoreSp: androidx.compose.ui.unit.TextUnit) {
    val home = slip.homeTeam ?: "Home"
    val away = slip.awayTeam ?: "Away"
    // Two crests, a 50sp score and its padding leave a 4-cell card roughly 60dp per name.
    // Below a comfortable width the names give way to their codes rather than to "L...".
    val contentWidthDp = LocalSize.current.width.value - 2 * PAD_H.value
    val compact = contentWidthDp < 400f
    val homeLabel = if (compact) ClubThemes.forTeam(home).short else home
    val awayLabel = if (compact) ClubThemes.forTeam(away).short else away
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(80.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = homeLabel,
            style = TextStyle(ColorProvider(WHITE), teamSp, FontWeight.Medium, textAlign = TextAlign.End),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(8.dp))
        Crest(theme = ClubThemes.forTeam(home), size = 46.dp, radius = 12.dp, bordered = false)
        Text(
            text = (slip.homeScore ?: 0).toString() + " - " + (slip.awayScore ?: 0),
            style = TextStyle(ColorProvider(WHITE), scoreSp, FontWeight.Bold, textAlign = TextAlign.Center),
            maxLines = 1,
            modifier = GlanceModifier.padding(horizontal = 12.dp),
        )
        Crest(theme = ClubThemes.forTeam(away), size = 46.dp, radius = 12.dp, bordered = false)
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = awayLabel,
            style = TextStyle(ColorProvider(WHITE), teamSp, FontWeight.Medium, textAlign = TextAlign.Start),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

@Composable
private fun Crest(theme: eu.feg.ambient.ambient.identity.ClubTheme, size: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp, bordered: Boolean) {
    val density = LocalContext.current.resources.displayMetrics.density
    Image(
        provider = ImageProvider(
            CrestBitmap.square(theme, (size.value * density).toInt(), (radius.value * density), bordered),
        ),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
    )
}

// ---- zone 3 · timeline ------------------------------------------------------------------

@Composable
private fun Timeline(slip: SlipSurfaceState, showLegs: Boolean) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val contentWidthDp = LocalSize.current.width.value - 2 * PAD_H.value - 2 * BORDER_WIDTH.value
    val minute = slip.minute ?: 0
    val goals = slip.goalMinutes.ifEmpty { spreadGoals((slip.homeScore ?: 0) + (slip.awayScore ?: 0), minute) }
    Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
        Image(
            provider = ImageProvider(TimelineBitmap.draw((contentWidthDp * density).toInt(), density, minute, goals)),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxWidth().height(TimelineBitmap.HEIGHT_DP.dp),
        )
        if (showLegs) {
            Spacer(GlanceModifier.height(4.dp))
            // Legs won on the slip. Never a stake, a return, or any money figure.
            Text(
                text = slip.legsWon.toString() + " of " + slip.legsTotal + " legs",
                style = TextStyle(ColorProvider(GREY), 14.sp),
                maxLines = 1,
            )
        }
    }
}

/** When goal minutes are not known, space the goals evenly through the match so far. */
private fun spreadGoals(count: Int, minute: Int): List<Int> =
    if (count <= 0 || minute <= 0) emptyList() else (1..count).map { minute * it / (count + 1) }

// ---- zones 4 and 5 · divider and actions ------------------------------------------------

@Composable
private fun Divider() {
    Spacer(GlanceModifier.height(10.dp))
    Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(WHITE_10)), contentAlignment = Alignment.Center) {}
    Spacer(GlanceModifier.height(10.dp))
}

@Composable
private fun Actions(slip: SlipSurfaceState, feedback: FeedbackMark?) {
    val match = (slip.homeTeam ?: "the home side") + " versus " + (slip.awayTeam ?: "the away side")
    Row(modifier = GlanceModifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
        ActionItem(R.drawable.ic_ball, "Live", "Live updates for " + match, actionRunCallback<ToggleLiveUpdateAction>())
        VDivider()
        if (feedback == null) {
            ActionItem(R.drawable.ic_thumb_up, "Like", "Like this update", actionRunCallback<ThumbsUpAction>())
            VDivider()
            ActionItem(R.drawable.ic_thumb_down, "Dislike", "Dislike this update", actionRunCallback<ThumbsDownAction>())
        } else {
            // Once rated, the two thumbs give way to the acknowledgement rather than sitting
            // there looking untouched.
            Text(
                text = if (feedback == FeedbackMark.UP) "More like this" else if (feedback == FeedbackMark.DOWN) "Fewer like this" else "Muted",
                style = TextStyle(ColorProvider(GREY), 14.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().padding(horizontal = 8.dp),
            )
        }
        VDivider()
        ActionItem(R.drawable.ic_bell_off, "Mute", "Mute this match", actionRunCallback<MuteMatchAction>())
    }
}

/** Calm Mode: keep following the match, or step away. Nothing that rates the moment. */
@Composable
private fun CalmActions(slip: SlipSurfaceState) {
    val match = (slip.homeTeam ?: "the home side") + " versus " + (slip.awayTeam ?: "the away side")
    Row(modifier = GlanceModifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.Vertical.CenterVertically) {
        ActionItem(R.drawable.ic_ball, "Live Updates", "Live updates for " + match, actionRunCallback<ToggleLiveUpdateAction>())
        VDivider()
        ActionItem(R.drawable.ic_bell_off, "Take a break", "Open protection tools and take a break", actionRunCallback<PanicAction>())
    }
}

@Composable
private fun RowScope.ActionItem(icon: Int, label: String, description: String, action: Action) {
    // Four labelled actions do not fit a 4-cell card at large font sizes; the labels give
    // way and the icons stay. The description still says what each one does.
    val showLabel = LocalContext.current.resources.configuration.fontScale < 1.5f
    Row(
        modifier = GlanceModifier
            .defaultWeight()
            .height(44.dp)
            .clickable(action)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier.size(34.dp).background(ColorProvider(WHITE_10)).cornerRadius(17.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(ColorProvider(WHITE)),
            )
        }
        if (showLabel) {
            Spacer(GlanceModifier.width(6.dp))
            Text(text = label, style = TextStyle(ColorProvider(WHITE), 12.sp), maxLines = 1)
        }
    }
}

@Composable
private fun VDivider() {
    Box(modifier = GlanceModifier.width(1.dp).height(26.dp).background(ColorProvider(WHITE_10)), contentAlignment = Alignment.Center) {}
}

// Colours live in WidgetShell.kt so the five widgets cannot drift apart.
