package eu.feg.ambient.ambient.surfaces.live

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eu.feg.ambient.MainActivity
import eu.feg.ambient.R
import eu.feg.ambient.ambient.surfaces.AndroidSurfaceController
import eu.feg.ambient.ambient.surfaces.LegStatus
import eu.feg.ambient.ambient.surfaces.LiveSlipService
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.surfaces.SlipSurfaceState
import eu.feg.ambient.ambient.surfaces.SpokenSurface
import eu.feg.ambient.ambient.surfaces.notifications.Channels
import eu.feg.ambient.ambient.surfaces.notifications.NotificationPermission
import eu.feg.ambient.ui.theme.PskColors
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * The lock-screen card, the always-on line and the status-bar chip — all three are one
 * promoted ongoing notification, so all three live here.
 *
 * Promotion is all-or-nothing and it fails silently: violate one of the eight requirements
 * and the system renders an ordinary notification without complaint. Every choice below that
 * looks arbitrary — ProgressStyle even in CALM, no colorized background, no custom view — is
 * there to keep the notification promotable. PromotionSpike proved the recipe on the device;
 * this class is that recipe applied to real state.
 *
 * COMPLIANCE — nothing here can read money, because [SlipSurfaceState] carries none. The one
 * rule this class adds is that UNVERIFIED and BLOCKED post nothing at all, not a redacted
 * card. See [isRenderable].
 */
class LiveUpdateRenderer(
    private val context: Context,
) : AndroidSurfaceController.LiveUpdateRendering {

    private val colors = PskColors()

    /**
     * The last state posted per slip, so [end] can draw a final card from a slipId alone.
     * Step 13 will read this back from the ledger instead.
     */
    private val lastState = ConcurrentHashMap<String, SlipSurfaceState>()

    override fun post(state: SlipSurfaceState) {
        if (!isRenderable(state.protection)) return
        // Google's Live Update guidance is explicit: a dismissed Live Update stays dismissed.
        // Reposting one is the behaviour that gets an app's promotion privilege withdrawn.
        if (isDismissed(state.slipId)) return
        if (!NotificationPermission.isGranted(context)) return

        lastState[state.slipId] = state
        val builder = when (state.protection) {
            ProtectionState.CALM -> buildCalm(state)
            else -> buildNormal(state)
        }
        notify(LiveSlipService.NOTIFICATION_ID, builder)
    }

    override fun end(slipId: String, settled: Boolean) {
        if (!settled) {
            // Voided, protection changed, or the user walked away: leave nothing behind.
            NotificationManagerCompat.from(context).cancel(LiveSlipService.NOTIFICATION_ID)
            lastState.remove(slipId)
            return
        }
        val state = lastState[slipId] ?: return
        if (!isRenderable(state.protection)) return
        if (!NotificationPermission.isGranted(context)) return

        val builder = buildNormal(state.copy(settled = true))
            .setOngoing(false)
            .setRequestPromotedOngoing(false)
            .setAutoCancel(true)
        // TODO(step 14E): auto-dismiss the settled card after 30s instead of leaving it until
        //  the user swipes. Needs a scheduled cancel that survives process death.
        notify(LiveSlipService.NOTIFICATION_ID, builder)
        lastState.remove(slipId)
    }

    override fun alert(headline: String, detail: String, deepLink: String) {
        if (!NotificationPermission.isGranted(context)) return
        val builder = NotificationCompat.Builder(context, Channels.SETTLEMENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(headline)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(deepLink.hashCode(), deepLink))
        notify(ALERT_NOTIFICATION_ID, builder)
    }

    // ---- NORMAL --------------------------------------------------------------------------

    private fun buildNormal(state: SlipSurfaceState): NotificationCompat.Builder {
        val segments = state.legs.map { leg ->
            NotificationCompat.ProgressStyle.Segment(segmentLength(leg.status))
                .setColor(segmentColor(leg.status))
        }
        val total = state.legs.sumOf { segmentLength(it.status) }.coerceAtLeast(1)
        val progress = state.legs
            .filter { it.status != LegStatus.PENDING }
            .sumOf { segmentLength(it.status) }

        val style = NotificationCompat.ProgressStyle()
            .setProgressSegments(segments)
            .setProgressPoints(goalPoints(state, total))
            .setProgress(progress)

        val title = state.narrated?.headline ?: fallbackHeadline(state)
        val detail = state.narrated?.detail ?: fallbackDetail(state)

        return base(Channels.LIVE_SLIP)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(style)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            // The chip is glanceable shorthand for the status bar; it is not what a screen
            // reader announces. TalkBack reads contentTitle then contentText, so the detail
            // line is always a full sentence and carries the meaning on its own.
            .setShortCriticalText(state.chipText)
            .setContentIntent(openSlipIntent(state.slipId))
            .setDeleteIntent(dismissIntent(state.slipId))
            .addAction(listenAction(state))
    }

    /**
     * Won and lost must not be told apart by hue alone — roughly one man in twelve cannot
     * separate this green from this red. So a lost leg is drawn at half length: the track
     * shows a visibly clipped stub where a won leg is a full-width block, and the difference
     * survives greyscale. Goal points ([goalPoints]) sit on the same track for the same
     * reason — shape carrying meaning that colour alone would not.
     */
    private fun segmentLength(status: LegStatus): Int = when (status) {
        LegStatus.LOST -> LOST_SEGMENT
        LegStatus.VOID -> LOST_SEGMENT
        else -> FULL_SEGMENT
    }

    private fun segmentColor(status: LegStatus): Int = when (status) {
        LegStatus.WON -> colors.positive.toArgb()
        LegStatus.LOST -> colors.negative.toArgb()
        LegStatus.VOID -> colors.textSecondary.toArgb()
        LegStatus.PENDING -> colors.surfaceRaised.toArgb()
    }

    /**
     * One point per goal in the live match, spread evenly along the track. Goals are match
     * facts, not bet facts, which is why they are allowed on a surface that carries no money.
     */
    private fun goalPoints(
        state: SlipSurfaceState,
        total: Int,
    ): List<NotificationCompat.ProgressStyle.Point> {
        val goals = ((state.homeScore ?: 0) + (state.awayScore ?: 0)).coerceAtMost(MAX_POINTS)
        if (goals <= 0) return emptyList()
        return (1..goals).map { index ->
            val position = (total.toLong() * index / (goals + 1)).toInt().coerceAtLeast(1)
            NotificationCompat.ProgressStyle.Point(position).setColor(colors.brandBlue.toArgb())
        }
    }

    // ---- CALM ----------------------------------------------------------------------------

    /**
     * CALM is a protection state, not a styling variant, so the card drops back to the match
     * itself: no legs, no narrated line about the bet, no progress towards a win — nothing
     * that rewards checking the phone again. ProgressStyle stays only because promotion
     * requires it, and it carries match minutes rather than slip progress.
     */
    private fun buildCalm(state: SlipSurfaceState): NotificationCompat.Builder {
        val minute = (state.minute ?: 0).coerceIn(0, MATCH_MINUTES)
        val style = NotificationCompat.ProgressStyle()
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(MATCH_MINUTES)
                        .setColor(colors.surfaceRaised.toArgb()),
                ),
            )
            .setProgress(minute)

        val title = scoreLine(state) ?: "Utakmica u tijeku"
        val detail = state.period?.let { it + " · " + minute + "'" } ?: (minute.toString() + "'")

        return base(Channels.LIVE_SLIP)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(style)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(minute.toString() + "'")
            // No sound and no urgency: CALM must never be the thing that pulls someone back.
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openSlipIntent(state.slipId))
            .setDeleteIntent(dismissIntent(state.slipId))
            // Listen stays in Calm Mode, but it reads the calm sentence: the score and the
            // minute. Removing the button would take the surface away from the customer who
            // most depends on it in order to make the surface quieter for everyone else.
            .addAction(listenAction(state))
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_launcher_foreground,
                    "Take a break",
                    // TODO(step 13A): route this to the panic flow, not the app's home screen.
                    openAppIntent(("break:" + state.slipId).hashCode(), null),
                ).build(),
            )
    }

    // ---- shared --------------------------------------------------------------------------

    /**
     * Everything common to a promotable notification. Deliberately absent, and never to be
     * added: custom RemoteViews, setCustomContentView, setGroupSummary(true) and
     * setColorized(true) — each one silently disqualifies the notification from promotion.
     */
    private fun base(channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)

    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        // The permission can be revoked between the check and the post; a SecurityException
        // here must not take the match tick down with it.
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    private fun scoreLine(state: SlipSurfaceState): String? {
        val home = state.homeTeam ?: return state.activeMatch
        val away = state.awayTeam ?: return state.activeMatch
        return home + " " + (state.homeScore ?: 0) + "–" + (state.awayScore ?: 0) + " " + away
    }

    private fun fallbackHeadline(state: SlipSurfaceState): String {
        val score = scoreLine(state)
        val minute = state.minute
        return when {
            score != null && minute != null && !state.settled -> score + " · " + minute + "'"
            score != null -> score
            state.settled -> "Your slip is settled"
            else -> "Your slip is running"
        }
    }

    private fun fallbackDetail(state: SlipSurfaceState): String {
        val progress = state.legsWon.toString() + " of " + state.legsTotal + " picks are home"
        val lost = if (state.legsLost > 0) ", " + state.legsLost + " gone" else ""
        val left = state.minutesRemaining?.let { ". " + it + " minutes left." } ?: "."
        return progress + lost + left
    }

    // ---- intents -------------------------------------------------------------------------

    /**
     * Tapping the card opens My Bets, not the app's start destination. MainActivity reads the
     * "route" extra; the older EXTRA_DEEP_LINK was written but never read, which is why every
     * tap landed on the home screen.
     */
    private fun openSlipIntent(slipId: String): PendingIntent =
        openAppIntent(slipId.hashCode(), ROUTE_MY_BETS)

    private fun openAppIntent(requestCode: Int, deepLink: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (deepLink != null) {
            intent.putExtra(EXTRA_DEEP_LINK, deepLink)
            // The extra MainActivity actually navigates on.
            intent.putExtra("route", deepLink)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PENDING_FLAGS)
    }

    /**
     * Speaks the card without opening anything. Absent for UNVERIFIED and BLOCKED because no
     * card is posted for them at all, so there is never a button to press.
     */
    private fun listenAction(state: SlipSurfaceState): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            "Listen",
            speakIntent(state),
        ).build()

    private fun speakIntent(state: SlipSurfaceState): PendingIntent {
        val intent = Intent(context, SpeakReceiver::class.java)
            .setAction(SpeakReceiver.ACTION_SPEAK)
            // The sentence rides along rather than being looked up later, so what is read
            // out is exactly what this post put on the screen.
            .putExtra(SpeakReceiver.EXTRA_TEXT, SpokenSurface.forSlip(state))
            .putExtra(SpeakReceiver.EXTRA_SURFACE, Surface.LIVE_UPDATE.name)
        return PendingIntent.getBroadcast(
            context,
            ("speak:" + state.slipId).hashCode(),
            intent,
            PENDING_FLAGS,
        )
    }

    private fun dismissIntent(slipId: String): PendingIntent {
        val intent = Intent(context, DismissReceiver::class.java)
            .setAction(DismissReceiver.ACTION_DISMISSED)
            .putExtra(DismissReceiver.EXTRA_SLIP_ID, slipId)
        return PendingIntent.getBroadcast(context, slipId.hashCode(), intent, PENDING_FLAGS)
    }

    companion object {
        /** MainActivity navigates on the "route" extra; this is where a tap belongs. */
        const val ROUTE_MY_BETS = "mybets"

        const val ALERT_NOTIFICATION_ID = 4302
        const val EXTRA_DEEP_LINK = "ambient.deepLink"

        private const val PENDING_FLAGS =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        private const val FULL_SEGMENT = 10
        private const val LOST_SEGMENT = 5
        private const val MAX_POINTS = 8
        private const val MATCH_MINUTES = 90

        /**
         * The compliance gate, kept as a pure function so a JVM unit test can assert it
         * without a Context: UNVERIFIED and BLOCKED get no surface at all — not a redacted
         * one, not a neutral one. Nothing is posted.
         */
        fun isRenderable(protection: ProtectionState): Boolean =
            protection == ProtectionState.NORMAL || protection == ProtectionState.CALM

        /**
         * Slips the user has swiped away. Step 13 moves this into the ledger so it survives
         * process death; an in-memory set is enough while the demo runs in one process.
         */
        private val dismissedSlips: MutableSet<String> =
            Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

        /** Called by [DismissReceiver] the moment the user swipes the card away. */
        fun markDismissed(slipId: String) {
            dismissedSlips.add(slipId)
        }

        fun isDismissed(slipId: String): Boolean = dismissedSlips.contains(slipId)

        /** The Surface Lab needs to re-arm a slip it has just dismissed on stage. */
        fun clearDismissals() {
            dismissedSlips.clear()
        }
    }
}
