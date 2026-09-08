package eu.feg.ambient.ambient.surfaces.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import eu.feg.ambient.ambient.surfaces.LegState
import eu.feg.ambient.ambient.surfaces.LegStatus
import eu.feg.ambient.ambient.surfaces.SlipSurfaceState
import eu.feg.ambient.ui.theme.PskColors
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * The palette, resolved once from [PskColors].
 *
 * Glance cannot see the Compose theme — a widget renders in the launcher's process from a
 * RemoteViews tree, so PskTheme's CompositionLocal is not available here. Reading the same
 * data class the app's theme is built from is the closest thing to sharing the tokens, and
 * it keeps the hard rule intact: no colour on this surface comes from Glance or Material.
 */
internal object WidgetTokens {
    private val psk = PskColors()

    val brandBlue = ColorProvider(psk.brandBlue)
    val background = ColorProvider(psk.background)
    val surface = ColorProvider(psk.surface)
    val surfaceRaised = ColorProvider(psk.surfaceRaised)
    val textPrimary = ColorProvider(psk.textPrimary)
    val textSecondary = ColorProvider(psk.textSecondary)
    val positive = ColorProvider(psk.positive)
    val negative = ColorProvider(psk.negative)
    val jackpotYellow = ColorProvider(psk.jackpotYellow)
}

/**
 * Type scale.
 *
 * Glance's TextStyle has no fontFeatureSettings, so the tabular-figures rule the app applies
 * to odds cannot be expressed here. It costs nothing: the widget shows minutes and scores,
 * never a price, and those are short enough that reflow is invisible.
 *
 * Sizes stay small and every call site caps maxLines, so a 200% font scale grows the text
 * inside a card that wraps its content rather than clipping it.
 */
internal object WidgetText {
    val chip = TextStyle(WidgetTokens.textPrimary, 22.sp, FontWeight.Bold)
    val title = TextStyle(WidgetTokens.textPrimary, 14.sp, FontWeight.Medium)
    val body = TextStyle(WidgetTokens.textPrimary, 12.sp)
    val meta = TextStyle(WidgetTokens.textSecondary, 11.sp)
    val button = TextStyle(WidgetTokens.textPrimary, 12.sp, FontWeight.Medium)
    val legWon = TextStyle(WidgetTokens.positive, 12.sp)
    val legLost = TextStyle(WidgetTokens.negative, 12.sp)
    val legPending = TextStyle(WidgetTokens.textSecondary, 12.sp)
}

/**
 * The card every state sits in.
 *
 * [description] is the whole-card semantic sentence — a screen reader should get one
 * coherent line rather than the concatenation of six labels, so the children below are not
 * separately described unless they are actionable.
 */
@Composable
internal fun WidgetCard(
    description: String,
    onClick: Action? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var modifier = GlanceModifier
        .fillMaxSize()
        .background(WidgetTokens.background)
        .cornerRadius(16.dp)
        .padding(12.dp)
        .semantics { contentDescription = description }
    if (onClick != null) modifier = modifier.clickable(onClick)
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.Vertical.Top,
        horizontalAlignment = Alignment.Horizontal.Start,
        content = content,
    )
}

/**
 * [modifier] is the caller's business because a button in a row of three needs a weight and
 * a button on its own needs the full width; everything else about it is fixed here.
 */
@Composable
internal fun WidgetActionButton(
    label: String,
    description: String,
    action: Action,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
    fill: ColorProvider = WidgetTokens.brandBlue,
) {
    Box(
        modifier = modifier
            .background(fill)
            .cornerRadius(10.dp)
            .clickable(action)
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = WidgetText.button, maxLines = 1)
    }
}

/**
 * The legs as coloured bars — the "2/3" made visual.
 *
 * Segments carry no text of their own; the row's contentDescription says the same thing in
 * words, which is why colour alone is acceptable here and is not in [WidgetLegLine].
 */
@Composable
internal fun WidgetLegSegments(legs: List<LegState>) {
    if (legs.isEmpty()) return
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .semantics { contentDescription = legsSpoken(legs) },
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        legs.forEachIndexed { index, leg ->
            if (index > 0) Spacer(GlanceModifier.width(4.dp))
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(6.dp)
                    .cornerRadius(3.dp)
                    .background(segmentFill(leg.status)),
                contentAlignment = Alignment.Center,
            ) {}
        }
    }
}

/**
 * One leg as a line of text.
 *
 * The status glyph is the accessibility requirement: won and lost must be distinguishable
 * without seeing the colour, and the widget has no drawable resources of its own to draw an
 * icon from, so the icon is a character. It reads correctly under TalkBack too.
 */
@Composable
internal fun WidgetLegLine(leg: LegState, dimmed: Boolean = false) {
    val style = when {
        dimmed -> WidgetText.legPending
        leg.status == LegStatus.WON -> WidgetText.legWon
        leg.status == LegStatus.LOST -> WidgetText.legLost
        else -> WidgetText.legPending
    }
    Text(
        text = statusGlyph(leg.status) + "  " + leg.description,
        style = style,
        maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth(),
    )
}

internal fun statusGlyph(status: LegStatus): String = when (status) {
    LegStatus.WON -> "✓"
    LegStatus.LOST -> "✗"
    LegStatus.VOID -> "–"
    LegStatus.PENDING -> "•"
}

internal fun statusWord(status: LegStatus): String = when (status) {
    LegStatus.WON -> "won"
    LegStatus.LOST -> "lost"
    LegStatus.VOID -> "void"
    LegStatus.PENDING -> "still running"
}

private fun segmentFill(status: LegStatus): ColorProvider = when (status) {
    LegStatus.WON -> WidgetTokens.positive
    LegStatus.LOST -> WidgetTokens.negative
    LegStatus.VOID -> WidgetTokens.surfaceRaised
    LegStatus.PENDING -> WidgetTokens.surfaceRaised
}

internal fun legsSpoken(legs: List<LegState>): String =
    legs.joinToString(", ") { it.description + " " + statusWord(it.status) }

/** "Kickoff in 40 min", or "Kicking off now" once the countdown has run out. */
internal fun kickoffLabel(kickoffIn: Duration): String {
    val minutes = kickoffIn.inWholeMinutes
    return when {
        minutes <= 0L -> "Kicking off now"
        minutes < 60L -> "Kickoff in " + minutes + " min"
        else -> "Kickoff in " + (minutes / 60L) + " h " + (minutes % 60L) + " min"
    }
}

/** "Kickoff in 40 min" for an absolute kickoff time. Null reads as no time known. */
internal fun untilLabel(kickoff: Instant?): String {
    if (kickoff == null) return "Kickoff time to be confirmed"
    return kickoffLabel(kickoff - Clock.System.now())
}

/** "3 minutes ago", for the register check line. Null reads as never checked. */
internal fun agoLabel(instant: Instant?): String {
    if (instant == null) return "not checked yet"
    val minutes = (Clock.System.now() - instant).inWholeMinutes
    return when {
        minutes <= 0L -> "just now"
        minutes == 1L -> "1 minute ago"
        minutes < 60L -> minutes.toString() + " minutes ago"
        else -> (minutes / 60L).toString() + " h ago"
    }
}

/** "Liverpool 1 – 0 Ipswich", or null when there is no score to show. */
internal fun scoreLine(slip: SlipSurfaceState): String? {
    val home = slip.homeTeam ?: return null
    val away = slip.awayTeam ?: return null
    val homeScore = slip.homeScore ?: return null
    val awayScore = slip.awayScore ?: return null
    return home + " " + homeScore + " – " + awayScore + " " + away
}
