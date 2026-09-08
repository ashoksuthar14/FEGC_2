package eu.feg.ambient.ambient.surfaces.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import android.content.Intent
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.Text
import eu.feg.ambient.MainActivity
import eu.feg.ambient.ambient.surfaces.LegStatus
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.surfaces.SlipSurfaceState
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.surfaces.SpokenSurface
import eu.feg.ambient.ambient.surfaces.WidgetState

/**
 * The home-screen surface.
 *
 * The widget is the fallback path to the customer: it needs no notification permission, it
 * survives a denied POST_NOTIFICATIONS, and it is the only ambient surface that is still
 * there when the phone is unlocked and in use. That is why every one of the six WidgetStates
 * has a rendering rather than only the interesting ones.
 *
 * State comes from [WidgetStateStore] rather than from a live reference, because the
 * launcher calls provideGlance in our process at times of its own choosing — often after a
 * reboot, when nothing else of ours is running.
 */
class AmbientWidget : GlanceAppWidget() {

    // TODO: SizeMode.Responsive with a 4x2 medium breakpoint that adds the narrated detail
    // line. Small first — a 2x2 that is right beats two sizes that are approximately right.
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = WidgetStateStore(context)
        val state = store.load()
        val feedback = store.feedback()
        // The club is read from the widget's own store, saved beside the state, so a redraw
        // after a reboot finds it without waking anything else of ours.
        val club = store.club()
        provideContent {
            CompositionLocalProvider(LocalClubTheme provides club) {
                WidgetBody(state, feedback)
            }
        }
    }
}

/**
 * Protection is checked before the state is dispatched, not inside each branch.
 *
 * AndroidSurfaceController already refuses to start a Live Update for UNVERIFIED or BLOCKED,
 * and this is the same rule applied a second time at the point of drawing. Belt and braces is
 * the right posture here: a stale stored snapshot is exactly how a surface outlives the
 * decision that was supposed to suppress it.
 */
@Composable
internal fun WidgetBody(state: WidgetState, feedback: FeedbackMark? = null) {
    val protection = when (state) {
        is WidgetState.Live -> state.slip.protection
        is WidgetState.Settled -> state.slip.protection
        is WidgetState.Protected -> state.protection
        else -> ProtectionState.NORMAL
    }
    if (protection == ProtectionState.UNVERIFIED || protection == ProtectionState.BLOCKED) {
        // The club falls away here as well as in the container's flow. A stored snapshot can
        // outlive the decision that allowed it, and a protected card wearing a fan's colours
        // would be the operator's own message dressed up as somebody else's.
        CompositionLocalProvider(LocalClubTheme provides ClubThemes.Default) {
            ProtectedCard(protection, lastRegisterCheck = null)
        }
        return
    }
    when (state) {
        is WidgetState.PreMatch -> PreMatchCard(state)
        is WidgetState.Live ->
            if (protection == ProtectionState.CALM) CalmSlipCard(state.slip)
            else LiveCard(state.slip, feedback)
        is WidgetState.Settled ->
            if (protection == ProtectionState.CALM) CalmSlipCard(state.slip)
            else SettledCard(state.slip, feedback)
        is WidgetState.Digest -> DigestCard(state, feedback)
        is WidgetState.Idle -> IdleCard(state)
        is WidgetState.Protected -> ProtectedCard(state.protection, state.lastRegisterCheck)
    }
}

/**
 * Countdown as the number, fixture as the caption.
 *
 * "Kickoff in 40 min" was a sentence where a number would do. The thing a customer wants off
 * this card in half a second is how long they have.
 */
@Composable
private fun PreMatchCard(state: WidgetState.PreMatch) {
    val minutes = state.kickoffIn.inWholeMinutes
    WidgetCard(
        description = state.match + ", " + kickoffLabel(state.kickoffIn) + ". " +
            legsSpoken(state.legs),
        onClick = openRoute(ROUTE_MY_BETS),
    ) {
        CardHeader(state.match)
        Spacer(GlanceModifier.height(6.dp))
        when {
            minutes <= 0L -> WidgetHero("Kickoff", "Now")
            minutes < 60L -> WidgetHero("Kickoff in", minutes.toString(), "min")
            else -> WidgetHero("Kickoff in", (minutes / 60L).toString(), "h " + (minutes % 60L) + "m")
        }
        Spacer(GlanceModifier.height(8.dp))
        // Greyed on purpose: nothing has happened yet, and a pre-match leg that looks live is
        // the widget telling a small lie.
        state.legs.take(MAX_LEG_LINES_COMPACT).forEach { WidgetLegLine(it, dimmed = true) }
        Spacer(GlanceModifier.defaultWeight())
        WidgetActionButton(
            label = "Remind me",
            description = "Remind me when " + state.match + " kicks off",
            action = actionRunCallback<RemindMeAction>(),
        )
    }
}

/**
 * The score, big, and almost nothing else.
 *
 * What used to be here: a 22sp chip reading "2/3 tick 61 minutes", a score line, a segment
 * bar, up to three lines of narration and three word-buttons — five things at five weights,
 * none of them dominant. The score is the fact the card exists for, so it is the only thing
 * set large. The narrator's headline stays, because it is the product's voice, but at one
 * line instead of three; the minute moves up to the caption, where a figure that changes on
 * every tick belongs.
 */
@Composable
private fun LiveCard(slip: SlipSurfaceState, feedback: FeedbackMark? = null) {
    val headline = slip.narrated?.headline
    WidgetCard(
        // N1: the narrator's spoken sentence is the screen-reader description, so the card
        // reads as prose rather than as the chip text spelled out symbol by symbol.
        description = SpokenSurface.forSlip(slip) ?: spokenSlip(slip, headline),
        onClick = openRoute(ROUTE_MY_BETS),
    ) {
        CardHeader(matchLabel(slip), trailing = slip.minute?.let { it.toString() + "'" })
        Spacer(GlanceModifier.height(4.dp))
        WidgetHero(
            label = slip.legsWon.toString() + " of " + slip.legsTotal + " home",
            value = scoreOnly(slip),
        )
        Spacer(GlanceModifier.height(10.dp))
        WidgetLegSegments(slip.legs)
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = headline ?: (slip.minutesRemaining?.let { it.toString() + " min left" } ?: ""),
            style = WidgetText.meta,
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        ActionRow(muteMatch = slip.activeMatch, given = feedback)
    }
}

/** Settled leads with the count that decided it, not with a two-word summary of it. */
@Composable
private fun SettledCard(slip: SlipSurfaceState, feedback: FeedbackMark? = null) {
    WidgetCard(
        description = SpokenSurface.forSlip(slip) ?: spokenSlip(slip, slip.narrated?.headline),
        onClick = openRoute(ROUTE_MY_BETS),
    ) {
        CardHeader(matchLabel(slip), trailing = "FT")
        Spacer(GlanceModifier.height(4.dp))
        WidgetHero(
            label = if (slip.legsLost > 0) slip.legsLost.toString() + " lost" else "Settled",
            value = slip.legsWon.toString() + "/" + slip.legsTotal,
        )
        Spacer(GlanceModifier.height(10.dp))
        WidgetLegSegments(slip.legs)
        Spacer(GlanceModifier.height(8.dp))
        Text(text = scoreLine(slip) ?: "", style = WidgetText.meta, maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        ActionRow(muteMatch = null, given = feedback)
    }
}

/**
 * Calm Mode's version of a slip in flight: the score, and nothing that celebrates it.
 *
 * It keeps the same hierarchy as [LiveCard] rather than inventing a quieter one — the point
 * of Calm Mode is that less is said, not that it is said in a way that looks broken. No
 * legs, no narration, no thumbs: the surface stops being a companion and becomes a fact,
 * plus the way out.
 */
@Composable
private fun CalmSlipCard(slip: SlipSurfaceState) {
    WidgetCard(description = (scoreLine(slip) ?: "Match in progress") + ". Protection active.") {
        CardHeader(matchLabel(slip), trailing = slip.minute?.let { it.toString() + "'" })
        Spacer(GlanceModifier.height(4.dp))
        WidgetHero(label = "Protection active", value = scoreOnly(slip))
        Spacer(GlanceModifier.height(6.dp))
        slip.period?.let { Text(text = it, style = WidgetText.meta, maxLines = 1) }
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetIconButton(
                glyph = "\uD83D\uDD0A",
                description = "Read this moment out loud",
                action = actionRunCallback<SpeakWidgetAction>(),
            )
            Spacer(GlanceModifier.width(6.dp))
            WidgetActionButton(
                label = "Take a break",
                description = "Open protection tools and take a break",
                action = actionRunCallback<PanicAction>(),
                modifier = GlanceModifier.defaultWeight(),
                fill = WidgetTokens.surfaceRaised,
                onFill = WidgetTokens.textPrimary,
            )
        }
    }
}

/**
 * Crest, caption, and one trailing figure. Every card opens with this line, so the six states
 * read as one family rather than six designs.
 */
@Composable
private fun CardHeader(label: String, trailing: String? = null) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = androidx.glance.layout.Alignment.Vertical.CenterVertically,
    ) {
        WidgetCrest(size = 18.dp)
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = label.uppercase(),
            style = WidgetText.label,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (trailing != null) Text(text = trailing, style = WidgetText.label, maxLines = 1)
    }
}

/** "1-0" with the team names taken out, because the header line already named them. */
private fun scoreOnly(slip: SlipSurfaceState): String {
    val home = slip.homeScore ?: return slip.legsWon.toString() + "/" + slip.legsTotal
    val away = slip.awayScore ?: return slip.legsWon.toString() + "/" + slip.legsTotal
    return home.toString() + "\u2013" + away
}

/** "Liverpool - Ipswich", or whatever the slip knows, for the caption line. */
private fun matchLabel(slip: SlipSurfaceState): String {
    val home = slip.homeTeam
    val away = slip.awayTeam
    if (home == null || away == null) return slip.activeMatch ?: "Your slip"
    return home + " \u2013 " + away
}

/**
 * Speak, then the two thumbs, then mute — as glyphs rather than words.
 *
 * Three filled pills with labels on them took a third of a 2x2 and left the numbers fighting
 * the buttons for the same space. The label still exists for the only reader that needed the
 * word: it is the control's contentDescription.
 *
 * The speaker is always present and never replaced. The thumbs give way to an
 * acknowledgement once one is given, because a control that looks unchanged after a tap
 * reads as broken — but losing the way to hear the card as a side effect of rating it would
 * be the wrong trade for the customer who most needs it.
 */
@Composable
internal fun ActionRow(muteMatch: String?, given: FeedbackMark? = null) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = androidx.glance.layout.Alignment.Vertical.CenterVertically,
    ) {
        WidgetIconButton(
            glyph = "\uD83D\uDD0A",
            description = "Read this moment out loud",
            action = actionRunCallback<SpeakWidgetAction>(),
        )
        Spacer(GlanceModifier.width(6.dp))

        if (given != null) {
            Text(text = acknowledgement(given), style = WidgetText.label, maxLines = 1)
        } else {
            WidgetIconButton(
                glyph = "\uD83D\uDC4D",
                description = "This was worth telling me",
                action = actionRunCallback<ThumbsUpAction>(),
            )
            Spacer(GlanceModifier.width(6.dp))
            WidgetIconButton(
                glyph = "\uD83D\uDC4E",
                description = "This was not worth telling me",
                action = actionRunCallback<ThumbsDownAction>(),
            )
            if (muteMatch != null) {
                Spacer(GlanceModifier.width(6.dp))
                WidgetIconButton(
                    glyph = "\uD83D\uDD15",
                    description = "Mute updates for " + muteMatch,
                    action = actionRunCallback<MuteMatchAction>(),
                )
            }
        }
    }
}

private fun acknowledgement(mark: FeedbackMark): String = when (mark) {
    FeedbackMark.UP -> "More like this"
    FeedbackMark.DOWN -> "Fewer like this"
    FeedbackMark.MUTED -> "Muted"
}

/** One sentence for TalkBack, built from the narration when there is one. */
private fun spokenSlip(slip: SlipSurfaceState, headline: String?): String {
    val progress = slip.legsWon.toString() + " of " + slip.legsTotal + " legs won"
    val minute = slip.minute?.let { ", minute " + it } ?: ""
    val score = scoreLine(slip)?.let { ", " + it } ?: ""
    val pending = slip.legs.count { it.status == LegStatus.PENDING }
    val tail = if (headline != null) ". " + headline else ". " + pending + " still running"
    return progress + minute + score + tail
}

/** Two lines, now that a hero number takes the top third of the card. */
private const val MAX_LEG_LINES_COMPACT = 2

/**
 * Where a tap on the widget should land. MainActivity reads the "route" extra, so a bare
 * actionStartActivity<MainActivity>() drops the user on the start destination — which is what
 * made every widget tap feel broken.
 */
@Composable
internal fun openRoute(route: String) = actionStartActivity(
    Intent(LocalContext.current, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra("route", route),
)

internal const val ROUTE_MY_BETS = "mybets"
internal const val ROUTE_LIVE = "live"
