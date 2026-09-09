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
import eu.feg.ambient.ambient.identity.ClubTheme
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.identity.CrestBitmap
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
    /**
     * Read on every post rather than injected once: the club can change while a card is on
     * the lock screen, and the next post has to be in the new colours without this object
     * being rebuilt.
     */
    private val clubTheme: () -> ClubTheme = { ClubThemes.Default },
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
        // Attached on every post, not once: the score changes, and a public version frozen at
        // kick-off would show 0-0 on the lock screen for ninety minutes.
        builder.setPublicVersion(publicVersion(Channels.LIVE_SLIP, state))
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
            .setPublicVersion(publicVersion(Channels.LIVE_SLIP, state))
        // TODO(step 14E): auto-dismiss the settled card after 30s instead of leaving it until
        //  the user swipes. Needs a scheduled cancel that survives process death.
        notify(LiveSlipService.NOTIFICATION_ID, builder)
        lastState.remove(slipId)
    }

    override fun alert(headline: String, detail: String, deepLink: String, entryId: String?) {
        if (!NotificationPermission.isGranted(context)) return
        val builder = NotificationCompat.Builder(context, Channels.SETTLEMENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(headline)
            .setContentText(detail)
            .setTicker(headline + " " + detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(deepLink.hashCode(), deepLink, entryId))
        // A SWIPE IS AN ANSWER. Without a deleteIntent the only feedback the router ever
        // heard was a thumb on the widget, so an alert nobody wanted looked exactly like an
        // alert nobody had seen -- and the arm that sent it kept its score. This is the
        // negative half of the loop, and it costs the customer nothing to give.
        if (entryId != null) {
            builder.setDeleteIntent(AlertFeedbackReceiver.dismissIntent(context, entryId))
        }
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

        // HIERARCHY: the score is the title, not the narration.
        //
        // It used to be the other way round -- title "Two down, Liverpool to go.", body a
        // full sentence -- which put the system's largest, boldest line at the service of the
        // least glanceable content. Someone looking at a locked phone wants the number. So
        // the score takes the title, the narrator's headline becomes the body (already
        // written short, at most 60 characters), and the long detail sentence leaves this
        // surface: it survives as the spoken variant behind Listen and as the screen-reader
        // description, where a whole sentence is the right unit.
        val title = fallbackHeadline(state)
        val detail = state.narrated?.headline ?: fallbackDetail(state)

        return base(Channels.LIVE_SLIP)
            .setContentTitle(title)
            .setContentText(detail)
            // N1: the ticker is the one field Android hands to accessibility services, so
            // the spoken variant goes here — "Two of your three legs have won…" — and
            // TalkBack reads that instead of "2/3 · 61'". setOnlyAlertOnce keeps it to the
            // first post, so a running card does not talk on every tick.
            .setTicker(SpokenSurface.forSlip(state))
            // The small line above the title: progress and minute, where a figure that
            // changes on every tick can update without moving the title.
            .setSubText(subText(state))
            .setStyle(style)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            // The chip is glanceable shorthand for the status bar; it is not what a screen
            // reader announces. TalkBack reads contentTitle then contentText, which now read
            // as a pair -- "Liverpool 1-0 Ipswich", "Two down, Liverpool to go." The full
            // sentence still exists behind Listen and as the card's spoken variant.
            .setShortCriticalText(chipText(state))
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
            // The goal markers take the club's accent too, so the theme reaches the one
            // part of this card a customer actually watches change.
            NotificationCompat.ProgressStyle.Point(position)
                .setColor(clubTheme().primary.toArgb())
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
        // A sentence, not "1. poluvrijeme · 61'": a screen reader gets the body as-is, and
        // "sixty-one apostrophe" is the soup N1 exists to replace. The chip and the
        // sub-text keep the short form, where the eye wants it.
        val detail = (state.period?.let { it + ", " } ?: "") + minute + " minutes played."

        return base(Channels.LIVE_SLIP)
            .setContentTitle(title)
            .setContentText(detail)
            .setTicker(SpokenSurface.forSlip(state))
            // No progress and no leg count in Calm Mode, so the sub-text carries the minute
            // and nothing that counts towards a result.
            .setSubText(minute.toString() + "'")
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
    private fun base(channelId: String): NotificationCompat.Builder {
        val theme = clubTheme()
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            // PRIVATE, WITH A PUBLIC VERSION SUPPLIED. The two halves have to go together.
            //
            // VISIBILITY_PRIVATE means "show this on every lock screen, but conceal the
            // sensitive part on a secure one". Without a public version the system has
            // nothing to substitute and falls back to its own redacted placeholder -- the app
            // name and no card -- which is why the Live Update was not appearing on the lock
            // screen. It looked correct in the shade and vanished exactly where the product
            // most needs it.
            //
            // The fix is not to make it PUBLIC. A score on a locked phone is fine; the slip
            // is not, and "1/3 legs home" tells anyone who picks the phone up that its owner
            // has money on the game. So the public version keeps the football and drops the
            // bet -- see [publicVersion].
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            // N6: the accent, and the crest. setColor tints the small icon and the accent
            // furniture, which is the whole of the club's presence on this surface.
            //
            // setColorized(true) is NOT called and must not be. It floods the notification
            // background with the colour, and a colorized notification is disqualified from
            // being promoted to the lock screen — the club would cost us the surface it was
            // meant to decorate.
            .setColor(theme.primary.toArgb())
            .setLargeIcon(CrestBitmap.of(theme))
    }

    /**
     * What a secure lock screen shows instead: the match, and nothing about the slip.
     *
     * Deliberately plain. No ProgressStyle, no actions, no narration -- the segments encode
     * how many legs are home, the Listen button reads a sentence about the customer's picks,
     * and the narrator's line is written to somebody who has a bet on. All three are the
     * private half. What survives is the fixture and the score, which is a fact about a
     * football match and belongs to nobody.
     *
     * The same channel and the same small icon, so the two cards read as one thing seen at
     * two levels of trust rather than as two different notifications.
     */
    private fun publicVersion(channelId: String, state: SlipSurfaceState): android.app.Notification {
        val theme = clubTheme()
        val score = scoreLine(state)
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setColor(theme.primary.toArgb())
            .setContentTitle(score ?: (state.activeMatch ?: "Match in progress"))
            .setContentText(state.minute?.let { it.toString() + "'" } ?: "")
            .build()
    }

    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        // The permission can be revoked between the check and the post; a SecurityException
        // here must not take the match tick down with it.
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    /**
     * The status-bar chip. The club code goes in front only when the match on the card is
     * actually that club's — a Dinamo fan watching a Liverpool slip should not see "DIN"
     * next to someone else's score, which would be identity applied as decoration rather
     * than as meaning. The chip is short-critical text, so it is kept to a few characters.
     */
    private fun chipText(state: SlipSurfaceState): String {
        val theme = clubTheme()
        val isMyClub = theme.clubId.isNotEmpty() &&
            (theme.name.equals(state.homeTeam, true) || theme.name.equals(state.awayTeam, true))
        // The status-bar chip is the smallest surface we own -- a few characters beside the
        // clock. "2/3 tick 61 apostrophe" was three facts fighting for that space and
        // arriving as none of them. The score alone is what a glance is for; the legs and the
        // minute are one pull-down away in the sub-text.
        val core = scoreOnly(state) ?: (state.legsWon.toString() + "/" + state.legsTotal)
        return if (isMyClub) theme.short + " " + core else core
    }

    /** "2/3 · 61'", the line above the title. */
    private fun subText(state: SlipSurfaceState): String {
        val progress = state.legsWon.toString() + "/" + state.legsTotal
        val minute = state.minute
        return when {
            state.settled -> progress + " · Full time"
            minute != null -> progress + " · " + minute + "'"
            else -> progress
        }
    }

    /** "1–0" with no team names; the title already carries those. */
    private fun scoreOnly(state: SlipSurfaceState): String? {
        val home = state.homeScore ?: return null
        val away = state.awayScore ?: return null
        return home.toString() + "–" + away
    }

    private fun scoreLine(state: SlipSurfaceState): String? {
        val home = state.homeTeam ?: return state.activeMatch
        val away = state.awayTeam ?: return state.activeMatch
        return home + " " + (state.homeScore ?: 0) + "–" + (state.awayScore ?: 0) + " " + away
    }

    /**
     * The title: teams and score, and nothing else.
     *
     * The minute used to be appended here, which made the title change on every tick and
     * pushed the team names into an ellipsis on a narrow lock screen. It lives in the
     * sub-text now, so the title holds still and the score sits where the eye lands.
     */
    private fun fallbackHeadline(state: SlipSurfaceState): String {
        val score = scoreLine(state)
        return when {
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

    private fun openAppIntent(
        requestCode: Int,
        deepLink: String?,
        entryId: String? = null,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (deepLink != null) {
            intent.putExtra(EXTRA_DEEP_LINK, deepLink)
            // The extra MainActivity actually navigates on.
            intent.putExtra("route", deepLink)
        }
        // The positive half of the loop: MainActivity turns this into engine.onTapped.
        if (entryId != null) intent.putExtra(AlertFeedbackReceiver.EXTRA_ENTRY_ID, entryId)
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
