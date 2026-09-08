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
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import eu.feg.ambient.AmbientApp
import eu.feg.ambient.ambient.digest.Digest
import eu.feg.ambient.ambient.digest.DigestItem
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.surfaces.LegStatus
import eu.feg.ambient.ambient.surfaces.WidgetState
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Step 16 on the home screen: what happened while the customer was away.
 *
 * The shell is not this widget's business: [GlassCard] and [WidgetHeader] draw the card and
 * its opening line, so the five widgets read as one family. A widget that draws its own card
 * is a bug, not a variation.
 */
class DigestWidget : GlanceAppWidget() {

    // Exact, so the body can decide by width -- the same reason the Live Slip card does.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = WidgetStateStore(context)
        val club = store.club()
        // Read here, before provideContent, and handed in as parameters: the body is then a
        // pure function of what it is given, which is what makes the preview body below the
        // same card on sample data. Guarded, because the launcher can compose a widget in a
        // process where the Application has not been created yet.
        val digest = (context.applicationContext as? AmbientApp)?.container?.peekDigest()
        // The next fixture, if the family's shared store happens to know it, so the quiet
        // state has something true to say rather than a blank.
        val idle = store.load() as? WidgetState.Idle
        provideContent {
            // Keyed on the store's version, like AmbientWidget: Glance keeps a composition
            // alive between updates, so anything read once before provideContent stays frozen
            // and the widget silently stops changing. This is what makes "one protection
            // change updates every widget" true rather than nearly true.
            val version by WidgetStoreVersion.flow.collectAsState()
            CompositionLocalProvider(
                LocalClubTheme provides remember(version) { WidgetStateStore(context).club() },
            ) {
                DigestWidgetBody(digest, nextFixture = idle?.nextFixture, kickoff = idle?.kickoff)
            }
        }
    }
}

@Composable
internal fun DigestWidgetBody(digest: Digest?, nextFixture: String? = null, kickoff: Instant? = null) {
    if (digest == null) {
        QuietCard(nextFixture, kickoff)
        return
    }
    val items = digest.items.take(itemsThatFit())
    GlassCard(
        // The narrator's spoken sentence is the description, so TalkBack reads prose rather
        // than three fragments with a tick and a cross spelled out between them.
        description = "While you were away, since " + clockLabel(digest.since) + ". " + digest.spokenText,
        onClick = openRoute(ROUTE_MY_BETS),
    ) {
        // "since 21:40" trails the title rather than sitting under it: a 4x2 is about 110dp
        // tall and a second header line is a digest item that no longer fits.
        WidgetHeader(title = "While you were away", trailing = "since " + clockLabel(digest.since))
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = digest.headline,
            style = TextStyle(ColorProvider(WHITE), 15.sp, FontWeight.Medium),
            maxLines = 1,
        )
        items.forEach {
            Spacer(GlanceModifier.height(3.dp))
            DigestLine(it)
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            WidgetActionButton(
                label = "Open",
                description = "Open my bets",
                action = openRoute(ROUTE_MY_BETS),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetIconButton(
                glyph = "🔊",
                description = "Read this out loud",
                action = actionRunCallback<SpeakWidgetAction>(),
                fill = ColorProvider(WHITE_10),
            )
        }
    }
}

/**
 * One item: a dot AND a glyph, then the words.
 *
 * The dot is what the eye reads across a room; the glyph is what a colour-blind reader and a
 * screen reader get. Neither is allowed to carry the outcome on its own.
 */
@Composable
private fun DigestLine(item: DigestItem) {
    val status = outcomeOf(item)
    val dot = when (status) {
        LegStatus.WON -> WidgetTokens.positive
        LegStatus.LOST -> WidgetTokens.negative
        else -> ColorProvider(GREY)
    }
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Box(
            modifier = GlanceModifier.size(7.dp).background(dot).cornerRadius(4.dp),
            contentAlignment = Alignment.Center,
        ) {}
        Spacer(GlanceModifier.width(7.dp))
        Text(
            text = statusGlyph(status) + "  " + item.text,
            style = TextStyle(
                ColorProvider(if (item.important) WHITE else GREY),
                12.sp,
                if (item.important) FontWeight.Medium else FontWeight.Normal,
            ),
            maxLines = 1,
        )
    }
}

/**
 * Won, lost or neither, read off the item's own words.
 *
 * [DigestItem] carries no outcome field. The wording comes from one table (DigestRanker's
 * textFor): a leg won ends "your pick landed" and a leg lost ends "your pick went", so those
 * two phrases are the whole signal. Everything else -- a slip settling, a goal, half time --
 * is neutral on purpose: a settled slip can have gone either way, and a green dot on a loss
 * is worse than a grey one. PENDING is the neutral value because its glyph is a plain bullet.
 */
private fun outcomeOf(item: DigestItem): LegStatus {
    val text = item.text.lowercase()
    return when {
        text.contains("pick landed") -> LegStatus.WON
        text.contains("pick went") -> LegStatus.LOST
        else -> LegStatus.PENDING
    }
}

/**
 * How many items the card has room for at the height the launcher gave it.
 *
 * Header, headline, footer and the card's own padding are fixed; what is left is the item
 * budget. On the Pixel launcher a 4x2 is about 110dp, which is the headline and no items --
 * the headline is the narrator's summary of them, and the tap opens the whole story. A
 * taller placement earns them back one at a time, never more than three.
 */
@Composable
private fun itemsThatFit(): Int {
    val spare = LocalSize.current.height.value - FIXED_HEIGHT_DP
    return (spare / ITEM_HEIGHT_DP).toInt().coerceIn(0, MAX_ITEMS)
}

private const val FIXED_HEIGHT_DP = 108f
private const val ITEM_HEIGHT_DP = 18f
private const val MAX_ITEMS = 3

/**
 * Nothing to catch up on.
 *
 * NEVER "nothing happened". The digest is null exactly when the customer's attention was
 * not needed (see DigestBuilder), and a card announcing that spends the attention it claims
 * to have saved -- it is the overnight push with the content taken out. So the empty widget
 * is this family's idle card: a quiet line, and the next fixture when the store knows one,
 * which is the one thing on a quiet day a fan actually wants to see.
 */
@Composable
private fun QuietCard(nextFixture: String?, kickoff: Instant?) {
    val next = if (nextFixture != null) {
        "Next up: " + nextFixture + (kickoff?.let { " · " + untilLabel(it) } ?: "")
    } else {
        FOLLOW_PROMPT
    }
    GlassCard(
        description = "Nothing needs you right now. " + next + ".",
        onClick = openRoute(ROUTE_LIVE),
    ) {
        WidgetHeader(title = "While you were away")
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = "Nothing needs you right now",
            style = TextStyle(ColorProvider(WHITE), 15.sp, FontWeight.Medium),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(text = next, style = TextStyle(ColorProvider(GREY), 12.sp), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
    }
}

/** "21:40" in the phone's own zone. Digest times are local by definition: it is when the customer left. */
private fun clockLabel(at: Instant): String {
    val local = at.toLocalDateTime(TimeZone.currentSystemDefault())
    return local.hour.toString().padStart(2, '0') + ":" + local.minute.toString().padStart(2, '0')
}

/** The same card on sample data, for the Surface Lab and a future glance preview. */
@Composable
internal fun DigestWidgetPreviewBody(empty: Boolean = false) {
    val now = Clock.System.now()
    val digest = if (empty) null else Digest(
        items = listOf(
            DigestItem("Betis 2–1 Sevilla — your pick landed", now - 2.hours, "m1", important = true),
            DigestItem("Sparta 0–1 Slavia — your pick went", now - 1.hours, "m2", important = true),
            DigestItem("Liverpool 1–0 Ipswich — half time", now - 20.minutes, "m3", important = false),
        ),
        headline = "Two decided, one still running.",
        detail = "Betis came home, Sparta did not. Liverpool lead at the break.",
        spokenText = "Two of your picks are decided and one is still running. Betis came home, " +
            "Sparta did not, and Liverpool lead at half time.",
        since = now - 3.hours,
        generatedAt = now,
    )
    CompositionLocalProvider(LocalClubTheme provides ClubThemes.HajdukSplit) {
        DigestWidgetBody(digest, nextFixture = "Hajduk Split – Rijeka", kickoff = now + 3.hours)
    }
}
