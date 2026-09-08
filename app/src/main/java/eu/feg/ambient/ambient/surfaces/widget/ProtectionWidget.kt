package eu.feg.ambient.ambient.surfaces.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import eu.feg.ambient.AmbientApp
import eu.feg.ambient.ambient.engine.ledger.RegisterCheck
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.core.formatMoney
import eu.feg.ambient.data.model.Limits
import eu.feg.ambient.ui.nav.Routes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * N2 on the home screen: limits used, when the register was last checked, and the way out.
 *
 * The shell is not this widget's business: [GlassCard] and [WidgetHeader] draw the card and
 * its opening line, so the five widgets read as one family. A widget that draws its own card
 * is a bug, not a variation.
 */
class ProtectionWidget : GlanceAppWidget() {

    // Exact, so the body can decide by width -- the same reason the Live Slip card does.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val club = WidgetStateStore(context).club()
        // Read here, before provideContent, and handed in as parameters, so the body is a
        // pure function of what it is given. With no container to read -- a launcher
        // composing us before the Application exists -- the card must not claim the account
        // is fine, so the fallback is the state that shows nothing about it.
        val container = (context.applicationContext as? AmbientApp)?.container
        val protection = container?.protectionEvaluator?.state?.value ?: ProtectionState.UNVERIFIED
        val limits = container?.userStateRepository?.state?.value?.limits ?: Limits()
        val lastCheck = container?.ledger?.lastCheck()
        val now = container?.clock?.now() ?: Clock.System.now()
        provideContent {
            // Keyed on the store's version, like AmbientWidget: Glance keeps a composition
            // alive between updates, so anything read once before provideContent stays frozen
            // and the widget silently stops changing. This is what makes "one protection
            // change updates every widget" true rather than nearly true.
            val version by WidgetStoreVersion.flow.collectAsState()
            CompositionLocalProvider(
                LocalClubTheme provides remember(version) { WidgetStateStore(context).club() },
            ) {
                ProtectionWidgetBody(protection, limits, lastCheck, now)
            }
        }
    }
}

@Composable
internal fun ProtectionWidgetBody(
    protection: ProtectionState,
    limits: Limits,
    lastCheck: RegisterCheck?,
    now: Instant,
) {
    when (protection) {
        // Status and help, nothing else, and not in the club's colours: a protected surface
        // has to look like the operator speaking, not like the fan's team.
        ProtectionState.BLOCKED, ProtectionState.UNVERIFIED ->
            CompositionLocalProvider(LocalClubTheme provides ClubThemes.Default) {
                StatusOnlyCard(protection)
            }
        else -> LimitsCard(protection, limits, registerLine(lastCheck, now))
    }
}

// ---- NORMAL and CALM ----------------------------------------------------------------------

/** What the register line says, and whether the check behind it is still in date. */
private data class RegisterLine(val text: String, val spoken: String, val fresh: Boolean)

/**
 * "Register checked 4 minutes ago · next check in 11" -- or "Checking…" when the last check
 * has run past [RegisterCheck.validUntil]. A stale timestamp is never shown: the line is a
 * claim about the present, and once the check has expired the honest claim is that we do
 * not currently know.
 */
private fun registerLine(check: RegisterCheck?, now: Instant): RegisterLine {
    val nowMs = now.toEpochMilliseconds()
    if (check == null || nowMs >= check.validUntil) {
        return RegisterLine("Checking…", "Register check in progress.", fresh = false)
    }
    val ago = agoLabel(Instant.fromEpochMilliseconds(check.checkedAt))
    val next = ((check.validUntil - nowMs) / 60_000L).coerceAtLeast(0L)
    return RegisterLine(
        text = "Register checked " + ago + " · next check in " + next,
        spoken = "Register checked " + ago + ", next check in " + next + " minutes.",
        fresh = true,
    )
}

@Composable
private fun LimitsCard(protection: ProtectionState, limits: Limits, register: RegisterLine) {
    val calm = protection == ProtectionState.CALM
    // Green only when both things are true: the account is in its normal state and the
    // register check is in date. Either one slipping turns it amber, and the word beside the
    // dot changes with it -- the colour is never the only carrier.
    val status = when {
        calm -> "Calm" to WARN_AMBER
        !register.fresh -> "Checking" to WARN_AMBER
        else -> "Normal" to LIVE_GREEN
    }
    val time = Usage("Time", limits.timeUsed.toDouble(), limits.timeLimit.toDouble(), ::hoursMinutes) { hoursMinutes(it, spoken = true) }
    // MONEY, ON PURPOSE. Every other OS surface here is built so it cannot name an amount
    // (see SlipSurfaceState). A deposit limit is the one exception because it is a protection
    // control the customer set against themselves, not an inducement: showing how much of it
    // is used is the point of the card. Nothing else on it is money, and nothing else may be.
    val deposit = Usage("Deposit", limits.depositUsed, limits.depositLimit, ::formatMoney, ::formatMoney)
    val spoken = "Your protection, " + status.first + ". " + time.spoken() + " " + deposit.spoken() +
        " " + register.spoken

    GlassCard(description = spoken, onClick = openRoute(Routes.RESPONSIBLE_GAMING)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.CenterStart) {
                WidgetHeader(title = "Your protection", subtitle = register.text)
            }
            Spacer(GlanceModifier.width(8.dp))
            StatusPill(word = status.first, colour = status.second)
        }
        Spacer(GlanceModifier.height(6.dp))
        UsageRows(time, deposit)
        Spacer(GlanceModifier.defaultWeight())
        if (calm) {
            // In Calm Mode the way out is the whole footer, in the club colour. The limits
            // are still one tap away through the card itself.
            BreakButton(GlanceModifier.fillMaxWidth(), fill = clubAccent, onFill = onClubAccent)
        } else {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                BreakButton(GlanceModifier.defaultWeight(), fill = ColorProvider(WHITE_10), onFill = ColorProvider(WHITE))
                Spacer(GlanceModifier.width(8.dp))
                WidgetActionButton(
                    label = "My limits",
                    description = "Open my limits",
                    action = openRoute(Routes.RESPONSIBLE_GAMING),
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

@Composable
private fun BreakButton(modifier: GlanceModifier, fill: ColorProvider, onFill: ColorProvider) {
    WidgetActionButton(
        label = "Take a break",
        description = "Open protection tools and take a break",
        action = actionRunCallback<PanicAction>(),
        modifier = modifier,
        fill = fill,
        onFill = onFill,
    )
}

/** A dot and the word, in a translucent pill -- the LivePill's shape, so the family agrees. */
@Composable
private fun StatusPill(word: String, colour: Color) {
    Row(
        modifier = GlanceModifier.height(26.dp).background(ColorProvider(WHITE_10)).cornerRadius(13.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.size(8.dp).background(ColorProvider(colour)).cornerRadius(4.dp),
            contentAlignment = Alignment.Center,
        ) {}
        Spacer(GlanceModifier.width(6.dp))
        Text(text = word.uppercase(), style = TextStyle(ColorProvider(colour), 11.sp, FontWeight.Bold), maxLines = 1)
    }
}

/** One limit: what it is called, how much is used, and how that is written and spoken. */
private class Usage(
    val label: String, val used: Double, val limit: Double,
    private val write: (Double) -> String, private val say: (Double) -> String,
) {
    /** "no limit set" when the limit is zero or absent: a bar against nothing is a lie. */
    val hasLimit: Boolean get() = limit > 0.0
    val fraction: Float get() = if (hasLimit) (used / limit).toFloat().coerceIn(0f, 1f) else 0f
    fun text(): String = if (hasLimit) write(used) + " of " + write(limit) else "no limit set"
    fun spoken(): String = label + ": " + (if (hasLimit) say(used) + " of " + say(limit) + " used." else "no limit set.")
}

/**
 * The two bars, side by side when the launcher's height is tight and stacked when it is not.
 *
 * A 4x2 on the Pixel launcher is about 110dp tall; header, two full-width rows, the register
 * line and a button row do not fit in that, and a card whose buttons fall below the fold has
 * no buttons. Side by side, each bar still has some 150dp of track, which is plenty for a
 * figure the numbers beside it already give exactly.
 */
@Composable
private fun UsageRows(time: Usage, deposit: Usage) {
    val contentWidthDp = LocalSize.current.width.value - 2 * PAD_H.value - 2 * BORDER_WIDTH.value
    val compact = LocalSize.current.height.value < STACK_FROM_HEIGHT_DP
    if (compact) {
        val trackDp = (contentWidthDp - GAP_DP) / 2
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            UsageRow(time, trackDp, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(GAP_DP.dp))
            UsageRow(deposit, trackDp, GlanceModifier.defaultWeight())
        }
    } else {
        UsageRow(time, contentWidthDp, GlanceModifier.fillMaxWidth())
        Spacer(GlanceModifier.height(6.dp))
        UsageRow(deposit, contentWidthDp, GlanceModifier.fillMaxWidth())
    }
}

@Composable
private fun UsageRow(usage: Usage, trackDp: Float, modifier: GlanceModifier) {
    Column(modifier = modifier) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = usage.label.uppercase(),
                style = TextStyle(ColorProvider(GREY), 10.sp, FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(text = usage.text(), style = TextStyle(ColorProvider(WHITE), 11.sp, FontWeight.Medium), maxLines = 1)
        }
        Spacer(GlanceModifier.height(4.dp))
        ProgressTrack(usage.fraction, trackDp)
    }
}

/**
 * Two nested boxes: the track, and a fill whose width is a share of the track's.
 *
 * Glance 1.1.1 does ship a LinearProgressIndicator, but it is the platform ProgressBar with
 * its own height and square ends, and it cannot be given this family's 6dp rounded bar. The
 * width is computed from LocalSize because Glance has no fractional fillMaxWidth -- the same
 * trick the Live card's timeline uses. Past nine tenths the fill turns amber; the numbers
 * beside it say the same thing in words, so the colour is never on its own.
 */
@Composable
private fun ProgressTrack(fraction: Float, trackDp: Float) {
    val fill = if (fraction >= NEAR_LIMIT) ColorProvider(WARN_AMBER) else clubAccent
    Box(
        modifier = GlanceModifier.fillMaxWidth().height(6.dp).background(ColorProvider(WHITE_10)).cornerRadius(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (fraction > 0f) {
            Box(
                modifier = GlanceModifier.width((trackDp * fraction).dp).height(6.dp).background(fill).cornerRadius(3.dp),
                contentAlignment = Alignment.Center,
            ) {}
        }
    }
}

private const val STACK_FROM_HEIGHT_DP = 150f
private const val GAP_DP = 12f
private const val NEAR_LIMIT = 0.9f

// ---- BLOCKED and UNVERIFIED ---------------------------------------------------------------

/**
 * The only card in the family that still says something when the account is protected.
 *
 * No limits, no register detail, no club: the customer is told where they stand and given
 * the one thing that helps. BLOCKED offers help; UNVERIFIED offers the app, which is where
 * verification lives.
 */
@Composable
private fun StatusOnlyCard(protection: ProtectionState) {
    val blocked = protection == ProtectionState.BLOCKED
    val title = if (blocked) "Account protected" else "Verify to continue"
    val body = if (blocked) "Play is paused on this account." else "We need to confirm your details first."
    GlassCard(description = "Your protection. " + title + ". " + body) {
        WidgetHeader(title = "Your protection")
        Spacer(GlanceModifier.height(6.dp))
        Text(text = title, style = TextStyle(ColorProvider(WHITE), 15.sp, FontWeight.Medium), maxLines = 1)
        Spacer(GlanceModifier.height(2.dp))
        Text(text = body, style = TextStyle(ColorProvider(GREY), 12.sp), maxLines = 2)
        Spacer(GlanceModifier.defaultWeight())
        WidgetActionButton(
            label = if (blocked) "Get help" else "Verify",
            description = if (blocked) "Open support and responsible gambling help" else "Open the app to verify your account",
            action = if (blocked) actionRunCallback<PanicAction>() else openRoute(Routes.RESPONSIBLE_GAMING),
            fill = ColorProvider(WHITE_10),
            onFill = ColorProvider(WHITE),
        )
    }
}

// ---- formatting ---------------------------------------------------------------------------

/** "1h 2m", "3h", "45m" on the card; "1 hour 2 minutes" for the screen reader. */
private fun hoursMinutes(minutes: Double, spoken: Boolean = false): String {
    val h = minutes.toInt() / 60
    val m = minutes.toInt() % 60
    val hours = if (!spoken) h.toString() + "h" else if (h == 1) "1 hour" else h.toString() + " hours"
    val mins = if (!spoken) m.toString() + "m" else if (m == 1) "1 minute" else m.toString() + " minutes"
    return when {
        h == 0 -> mins
        m == 0 -> hours
        else -> hours + " " + mins
    }
}

/** The same card on sample data, for the Surface Lab and a future glance preview. */
@Composable
internal fun ProtectionWidgetPreviewBody(protection: ProtectionState = ProtectionState.NORMAL) {
    val now = Clock.System.now()
    val check = RegisterCheck(
        id = "chk-1",
        registerRef = "SR-2026-000123",
        checkedAt = (now - 4.minutes).toEpochMilliseconds(),
        validUntil = (now + 11.minutes).toEpochMilliseconds(),
        excluded = false,
        reason = "Not on the register",
    )
    CompositionLocalProvider(LocalClubTheme provides ClubThemes.HajdukSplit) {
        ProtectionWidgetBody(protection, Limits(timeLimit = 360, timeUsed = 200), check, now)
    }
}
