package eu.feg.ambient.ambient.surfaces.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
        provideContent { WidgetBody(state, feedback) }
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
        ProtectedCard(protection, lastRegisterCheck = null)
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

@Composable
private fun PreMatchCard(state: WidgetState.PreMatch) {
    val countdown = kickoffLabel(state.kickoffIn)
    WidgetCard(
        description = state.match + ", " + countdown + ". " + legsSpoken(state.legs),
        onClick = openRoute(ROUTE_MY_BETS),
    ) {
        Text(text = state.match, style = WidgetText.title, maxLines = 2)
        Text(text = countdown, style = WidgetText.meta, maxLines = 1)
        Spacer(GlanceModifier.height(8.dp))
        // Greyed on purpose: nothing has happened yet, and a pre-match leg that looks live is
        // the widget telling a small lie.
        state.legs.take(MAX_LEG_LINES).forEach { WidgetLegLine(it, dimmed = true) }
        Spacer(GlanceModifier.defaultWeight())
        WidgetActionButton(
            label = "Remind me",
            description = "Remind me when " + state.match + " kicks off",
            action = actionRunCallback<RemindMeAction>(),
        )
    }
}

@Composable
private fun LiveCard(slip: SlipSurfaceState, feedback: FeedbackMark? = null) {
    val score = scoreLine(slip)
    val headline = slip.narrated?.headline
    WidgetCard(
        description = spokenSlip(slip, headline),
        onClick = openRoute(ROUTE_MY_BETS),
    ) {
        Text(text = slip.chipText, style = WidgetText.chip, maxLines = 1)
        if (score != null) Text(text = score, style = WidgetText.meta, maxLines = 1)
        Spacer(GlanceModifier.height(8.dp))
        WidgetLegSegments(slip.legs)
        Spacer(GlanceModifier.height(8.dp))
        if (headline != null) Text(text = headline, style = WidgetText.body, maxLines = 3)
        Spacer(GlanceModifier.defaultWeight())
        FeedbackRow(muteMatch = slip.activeMatch, given = feedback)
    }
}

@Composable
private fun SettledCard(slip: SlipSurfaceState, feedback: FeedbackMark? = null) {
    val result = slip.legsWon.toString() + " won · " + slip.legsLost + " lost"
    WidgetCard(
        description = spokenSlip(slip, slip.narrated?.headline),
        onClick = openRoute(ROUTE_MY_BETS),
    ) {
        Text(text = result, style = WidgetText.title, maxLines = 1)
        scoreLine(slip)?.let { Text(text = it, style = WidgetText.meta, maxLines = 1) }
        Spacer(GlanceModifier.height(8.dp))
        slip.legs.take(MAX_LEG_LINES).forEach { WidgetLegLine(it) }
        Spacer(GlanceModifier.defaultWeight())
        FeedbackRow(muteMatch = null, given = feedback)
    }
}

/**
 * Calm Mode's version of a slip in flight: the score, and nothing that celebrates it.
 *
 * No narration, no leg colours, no thumbs — the surface stops being a companion and becomes
 * a fact, plus the way out. This is the demo moment, so it has to be visibly different at a
 * glance and not merely quieter.
 */
@Composable
private fun CalmSlipCard(slip: SlipSurfaceState) {
    val score = scoreLine(slip) ?: slip.activeMatch ?: "Match in progress"
    WidgetCard(description = score + ". Protection active.") {
        Text(text = score, style = WidgetText.title, maxLines = 2)
        slip.period?.let { Text(text = it, style = WidgetText.meta, maxLines = 1) }
        Spacer(GlanceModifier.height(8.dp))
        Text(text = "Protection active", style = WidgetText.meta, maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        WidgetActionButton(
            label = "Take a break",
            description = "Open protection tools and take a break",
            action = actionRunCallback<PanicAction>(),
            fill = WidgetTokens.surfaceRaised,
        )
    }
}

/**
 * Thumbs, and mute when there is something to mute.
 *
 * The thumbs are the whole learning signal, so they live on the surface that prompted the
 * feeling rather than behind a tap into the app.
 */
@Composable
internal fun FeedbackRow(muteMatch: String?, given: FeedbackMark? = null) {
    // Once answered, the buttons are replaced rather than merely disabled. A control that is
    // still there after it has been used invites a second tap that would teach the router
    // nothing, and leaving it looking live is how the widget reads as broken.
    if (given != null) {
        Text(text = acknowledgement(given), style = WidgetText.meta, maxLines = 1)
        return
    }
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        WidgetActionButton(
            label = "👍",
            description = "This was worth telling me",
            action = actionRunCallback<ThumbsUpAction>(),
            modifier = GlanceModifier.defaultWeight(),
            fill = WidgetTokens.surfaceRaised,
        )
        Spacer(GlanceModifier.width(6.dp))
        WidgetActionButton(
            label = "👎",
            description = "This was not worth telling me",
            action = actionRunCallback<ThumbsDownAction>(),
            modifier = GlanceModifier.defaultWeight(),
            fill = WidgetTokens.surfaceRaised,
        )
        if (muteMatch != null) {
            Spacer(GlanceModifier.width(6.dp))
            WidgetActionButton(
                label = "Mute",
                description = "Mute updates for " + muteMatch,
                action = actionRunCallback<MuteMatchAction>(),
                modifier = GlanceModifier.defaultWeight(),
                fill = WidgetTokens.surfaceRaised,
            )
        }
    }
}

private fun acknowledgement(mark: FeedbackMark): String = when (mark) {
    FeedbackMark.UP -> "Thanks — more like this"
    FeedbackMark.DOWN -> "Thanks — fewer like this"
    FeedbackMark.MUTED -> "Muted for this match"
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

/** Three lines is what a 2x2 holds before the actions are pushed off the card. */
private const val MAX_LEG_LINES = 3

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
