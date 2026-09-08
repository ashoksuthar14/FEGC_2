package eu.feg.ambient.ambient.surfaces.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.layout.size
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import eu.feg.ambient.ambient.identity.CrestBitmap
import eu.feg.ambient.ambient.identity.ClubThemes
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
    /**
     * The one number the card exists to show — a score, a count, a countdown.
     *
     * Everything else on the card is deliberately quieter than this. A widget is read at
     * arm's length in about half a second, and a card where four things compete at 12sp
     * communicates nothing in that time; one number at 34sp and a caption communicates the
     * whole state. 34 rather than 40 so that a 200% font scale still lands inside a 2x2.
     */
    val hero = TextStyle(WidgetTokens.textPrimary, 34.sp, FontWeight.Bold)

    /** The unit that trails a hero number — "min", "left" — set small and aligned to its base. */
    val heroUnit = TextStyle(WidgetTokens.textSecondary, 13.sp, FontWeight.Medium)

    /** The caption above the number. Written upper-case at the call site; there is no tracking
     * in Glance's TextStyle, so case is what carries the label voice. */
    val label = TextStyle(WidgetTokens.textSecondary, 10.sp, FontWeight.Medium)

    val title = TextStyle(WidgetTokens.textPrimary, 14.sp, FontWeight.Medium)
    val body = TextStyle(WidgetTokens.textPrimary, 12.sp)
    val meta = TextStyle(WidgetTokens.textSecondary, 11.sp)
    val legWon = TextStyle(WidgetTokens.positive, 12.sp)
    val legLost = TextStyle(WidgetTokens.negative, 12.sp)
    val legPending = TextStyle(WidgetTokens.textSecondary, 12.sp)
}

/**
 * Caption, then number. The two-line shape every card is built from.
 *
 * [unit] sits on the number's baseline rather than after it in the same string, so "40 min"
 * reads as a big 40 with a small unit instead of two words at the same weight.
 */
@Composable
internal fun WidgetHero(
    label: String,
    value: String,
    unit: String? = null,
) {
    Text(text = label.uppercase(), style = WidgetText.label, maxLines = 1)
    Row(verticalAlignment = Alignment.Vertical.Bottom) {
        Text(text = value, style = WidgetText.hero, maxLines = 1)
        if (unit != null) {
            Spacer(GlanceModifier.width(4.dp))
            // Nudged up off the baseline so the unit optically centres against the digits.
            Box(modifier = GlanceModifier.padding(bottom = 5.dp)) {
                Text(text = unit, style = WidgetText.heroUnit, maxLines = 1)
            }
        }
    }
}

/**
 * A square, glyph-only control.
 *
 * The card used to carry three filled pills with words on them, which on a 2x2 left the
 * numbers fighting the buttons for the same space. Icon buttons give the same five actions
 * back a fifth of the room — and the label still exists for a screen reader, which is the
 * only reader that needed the word in the first place.
 */
@Composable
internal fun WidgetIconButton(
    glyph: String,
    description: String,
    action: Action,
    modifier: GlanceModifier = GlanceModifier,
    fill: ColorProvider = WidgetTokens.surfaceRaised,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .background(fill)
            .cornerRadius(10.dp)
            .clickable(action)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, style = TextStyle(WidgetTokens.textPrimary, 15.sp), maxLines = 1)
    }
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
        // N6: the ground carries a wash of the club colour rather than a flat near-black.
        // Fourteen per cent, so every contrast ratio computed against the dark background
        // still holds -- see tintedSurface. A card in the club's full colour would be a fan
        // app; a card with a trace of it is the customer's corner of the operator's.
        .background(ColorProvider(LocalClubTheme.current.surfaceTint))
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
 * N6: whose colours this widget is drawn in.
 *
 * A CompositionLocal rather than a parameter threaded through nine card composables. The club
 * is a property of the whole card, not of any one element, and passing it by hand would mean
 * every future state having to remember to forward it — which is the sort of thing that gets
 * forgotten and ships as one un-themed card.
 */
internal val LocalClubTheme = staticCompositionLocalOf { ClubThemes.Default }

/** The club colour as an accent, and the text colour that is legible on it. */
internal val clubAccent: ColorProvider
    @Composable get() = ColorProvider(LocalClubTheme.current.primary)

internal val onClubAccent: ColorProvider
    @Composable get() = ColorProvider(LocalClubTheme.current.onPrimary)

/**
 * [modifier] is the caller's business because a button in a row of three needs a weight and
 * a button on its own needs the full width; everything else about it is fixed here.
 *
 * The label colour follows the fill rather than being fixed white: on Hajduk's white and
 * Varaždin's yellow, white text is an invisible control. [onFill] defaults to the club's
 * computed contrast colour and is overridden only where the fill is one of ours.
 */
@Composable
internal fun WidgetActionButton(
    label: String,
    description: String,
    action: Action,
    modifier: GlanceModifier = GlanceModifier.fillMaxWidth(),
    fill: ColorProvider = clubAccent,
    onFill: ColorProvider = onClubAccent,
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
        Text(
            text = label,
            style = TextStyle(onFill, 12.sp, FontWeight.Medium),
            maxLines = 1,
        )
    }
}

/**
 * The club's mark on the widget: initials in a coloured circle, the Glance twin of
 * CrestBadge. Drawn rather than downloaded, for the same trademark reason.
 */
@Composable
internal fun WidgetCrest(size: androidx.compose.ui.unit.Dp = 24.dp) {
    // The same drawn badge the notification and the shortcuts use, so the mark is identical
    // wherever the customer meets it. See CrestBitmap for why a real crest cannot ship.
    Image(
        provider = ImageProvider(CrestBitmap.of(LocalClubTheme.current, CREST_PX)),
        // The card's own description names the club; a second announcement here would make
        // a screen reader say it twice.
        contentDescription = null,
        modifier = GlanceModifier.size(size),
    )
}

private const val CREST_PX = 144

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

/**
 * The legs as dots, one per leg, with the count spelled out beside them.
 *
 * This replaces the full-width bar on the cards. At 6 dp the bar was a green line with no
 * label -- it said "something is green" and nothing else. Dots are the same information at
 * the same size but read as a count, and the count is written next to them so nobody has to
 * decode colours at all.
 */
@Composable
internal fun WidgetLegDots(legs: List<LegState>, won: Int, total: Int) {
    if (legs.isEmpty()) return
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .semantics { contentDescription = legsSpoken(legs) },
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        legs.take(MAX_DOTS).forEachIndexed { index, leg ->
            if (index > 0) Spacer(GlanceModifier.width(5.dp))
            Box(
                modifier = GlanceModifier
                    .size(9.dp)
                    .cornerRadius(5.dp)
                    .background(segmentFill(leg.status)),
                contentAlignment = Alignment.Center,
            ) {}
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(text = won.toString() + " of " + total + " home", style = WidgetText.meta, maxLines = 1)
    }
}

private const val MAX_DOTS = 6

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
