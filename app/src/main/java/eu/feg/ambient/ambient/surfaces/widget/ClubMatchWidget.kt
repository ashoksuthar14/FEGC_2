package eu.feg.ambient.ambient.surfaces.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
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
import eu.feg.ambient.ambient.identity.ClubTheme
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.identity.CrestBitmap
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.surfaces.SlipSurfaceState
import eu.feg.ambient.ambient.surfaces.SpokenSurface
import eu.feg.ambient.ambient.surfaces.WidgetState

/**
 * The customer's club, wearing its own colours.
 *
 * WHY A SIXTH WIDGET RATHER THAN A SETTING ON THE FIRST. The Live Slip card is the operator's
 * card: PSK's glass, PSK's blue, the same on every phone, and it has to stay that way because
 * it is also what a customer under protection sees. This one is the opposite proposition --
 * the whole surface belongs to the club, ground and all -- and the two cannot be the same
 * component with a flag on it without one of them being a compromise. So the five are
 * untouched and this stands beside them.
 *
 * THE SCORE IS THE SAME SCORE. Name, crest, figure, crest, name, on one line with the figure
 * centred: that layout is not up for redesign, it is the thing the card exists to show and it
 * already reads at arm's length. What changes is everything around it.
 *
 * WHAT MAKES IT THE CLUB'S:
 *  - the ground is the club's crest, blown up and blurred into its own colours (ClubBackdrop)
 *  - the accent on the minute, the timeline and the pill is the club's, not the app's green
 *  - the crest is the real generated badge with the club's pattern, not a coloured circle
 *  - the header names the club rather than the competition, because on this card the club is
 *    the subject and the fixture is the news
 *
 * MINIMAL IS THE POINT. There are four things on it -- who, the score, how far through, and
 * what is riding on it -- and no buttons at all. The Live Slip card carries the actions; a
 * second row of them here would turn a poster into a dashboard, and the tap target is the
 * whole card.
 *
 * COMPLIANCE is unchanged and worth restating: SlipSurfaceState has no money field, so this
 * card cannot show a stake or a return however it is dressed. Under CALM, UNVERIFIED or
 * BLOCKED it drops the club entirely and defers to the operator's protected card -- a
 * protection message wearing a fan's colours would be our message dressed as somebody else's.
 */
class ClubMatchWidget : GlanceAppWidget() {

    // Exact, so the backdrop is generated at the size the launcher actually gave us. A
    // stretched bitmap is the one thing that would make this look cheap.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = WidgetStateStore(context)
        provideContent {
            val version by WidgetStoreVersion.flow.collectAsState()
            val frame = remember(version) {
                val s = WidgetStateStore(context)
                s.load() to s.club()
            }
            CompositionLocalProvider(LocalClubTheme provides frame.second) {
                ClubMatchBody(frame.first, frame.second)
            }
        }
    }
}

class ClubMatchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClubMatchWidget()
}

@Composable
internal fun ClubMatchBody(state: WidgetState, club: ClubTheme) {
    val slip = when (state) {
        is WidgetState.Live -> state.slip
        is WidgetState.Settled -> state.slip
        else -> null
    }
    val protection = slip?.protection ?: ProtectionState.NORMAL

    // Protection outranks the paint job, exactly as it does on the Live Slip card.
    if (protection != ProtectionState.NORMAL && protection != ProtectionState.CALM) {
        CompositionLocalProvider(LocalClubTheme provides ClubThemes.Default) {
            ProtectedCard(protection, lastRegisterCheck = null)
        }
        return
    }
    if (slip == null || slip.homeTeam == null) {
        ClubIdleCard(club)
        return
    }
    ClubMatchCard(slip, club)
}

/** The card. Ground, header, score, timeline — and nothing else. */
@Composable
private fun ClubMatchCard(slip: SlipSurfaceState, club: ClubTheme) {
    val size = LocalSize.current
    val density = LocalContext.current.resources.displayMetrics.density
    val accent = club.accentOnDark

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(CLUB_RADIUS)
            .background(
                ImageProvider(
                    ClubBackdrop.of(
                        club,
                        (size.width.value * density).toInt(),
                        (size.height.value * density).toInt(),
                    ),
                ),
                ContentScale.Crop,
            )
            .semantics {
                contentDescription = SpokenSurface.forSlip(slip)
                    ?: (club.name + ". " + (slip.activeMatch ?: "Match in progress"))
            }
            .clickable(openRoute(ROUTE_MY_BETS)),
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            ClubHeader(slip, club, accent)
            Spacer(GlanceModifier.defaultWeight())
            ClubScoreRow(slip, club)
            Spacer(GlanceModifier.defaultWeight())
            ClubFooter(slip, club, accent)
        }
    }
}

/**
 * The club's name, then the fixture, then the minute in the club's colour.
 *
 * The club leads because that is what this card is: on the operator's card the header names
 * the competition, and putting HAJDUK SPLIT there would have been a card contradicting itself.
 * Here it is the subject.
 */
@Composable
private fun ClubHeader(slip: SlipSurfaceState, club: ClubTheme, accent: androidx.compose.ui.graphics.Color) {
    // THE HEADER ONLY CLAIMS THE CLUB IS PLAYING WHEN IT IS.
    //
    // The card is always in the club's colours -- that is the customer's choice of theme and
    // it is true everywhere. The NAME is a different claim: "HAJDUK SPLIT" over a Betis-Real
    // Madrid score is a card contradicting itself, which is the exact mistake LiveMatchCard's
    // own header comment warns about. So when the club is in the fixture it leads, and when it
    // is not the competition leads and the crest stays as what it actually is: the customer's
    // badge, not a claim about the teams.
    val playing = listOfNotNull(slip.homeTeam, slip.awayTeam).any { it.equals(club.name, true) }
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Crest(club, 30.dp)
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = (if (playing) club.name else slip.competition ?: club.name).uppercase(),
                style = TextStyle(ColorProvider(WHITE), 13.sp, FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = if (playing) slip.competition ?: "" else slip.activeMatch ?: "",
                style = TextStyle(ColorProvider(WHITE_60), 11.sp),
                maxLines = 1,
            )
        }
        slip.minute?.let {
            Text(
                text = it.toString() + "'",
                style = TextStyle(ColorProvider(accent), 20.sp, FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

/**
 * Name, crest, score, crest, name — the layout from the Live Slip card, unchanged.
 *
 * The figure is the largest thing on the widget by a wide margin, and the names are weighted
 * equally either side so it sits on the card's centre rather than on whatever is left over
 * after a long name. Short codes below a comfortable width, for the same reason as the
 * original: a truncated "Dinamo Za..." is worse than "DIN".
 */
@Composable
private fun ClubScoreRow(slip: SlipSurfaceState, club: ClubTheme) {
    val home = slip.homeTeam ?: "Home"
    val away = slip.awayTeam ?: "Away"
    val contentWidth = LocalSize.current.width.value - 36f
    val compact = contentWidth < 300f
    val homeTheme = ClubThemes.forTeam(home)
    val awayTheme = ClubThemes.forTeam(away)

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(
            text = if (compact) homeTheme.short else home,
            style = TextStyle(ColorProvider(WHITE), 14.sp, FontWeight.Medium, textAlign = TextAlign.End),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(10.dp))
        Crest(homeTheme, 26.dp)
        Text(
            text = (slip.homeScore ?: 0).toString() + "  " + (slip.awayScore ?: 0),
            style = TextStyle(ColorProvider(WHITE), 42.sp, FontWeight.Bold, textAlign = TextAlign.Center),
            maxLines = 1,
            modifier = GlanceModifier.padding(horizontal = 14.dp),
        )
        Crest(awayTheme, 26.dp)
        Spacer(GlanceModifier.width(10.dp))
        Text(
            text = if (compact) awayTheme.short else away,
            style = TextStyle(ColorProvider(WHITE), 14.sp, FontWeight.Medium, textAlign = TextAlign.Start),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

/**
 * How far through, and what is riding on it.
 *
 * The bar is the club's colour on a translucent track rather than the app's green: green is
 * the operator's "in play" signal everywhere else, and on a card that belongs to the club it
 * would be the one piece of PSK left in the paint. The legs line is counts only.
 */
@Composable
private fun ClubFooter(slip: SlipSurfaceState, club: ClubTheme, accent: androidx.compose.ui.graphics.Color) {
    val minute = (slip.minute ?: 0).coerceIn(0, FULL_TIME)
    val played = minute.toFloat() / FULL_TIME
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(modifier = GlanceModifier.fillMaxWidth().height(4.dp)) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(4.dp)
                    .cornerRadius(2.dp)
                    .background(ColorProvider(accent)),
                contentAlignment = Alignment.Center,
            ) {}
            if (played < 0.98f) {
                Spacer(GlanceModifier.width(3.dp))
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .height(4.dp)
                        .cornerRadius(2.dp)
                        .background(ColorProvider(WHITE_20)),
                    contentAlignment = Alignment.Center,
                ) {}
            }
        }
        Spacer(GlanceModifier.height(7.dp))
        Text(
            text = slip.legsWon.toString() + " of " + slip.legsTotal + " home" +
                (slip.period?.let { "  ·  " + it } ?: ""),
            style = TextStyle(ColorProvider(WHITE_60), 11.sp),
            maxLines = 1,
        )
    }
}

/** Nothing in play. The club still owns the card; it simply has less to say. */
@Composable
private fun ClubIdleCard(club: ClubTheme) {
    val size = LocalSize.current
    val density = LocalContext.current.resources.displayMetrics.density
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(CLUB_RADIUS)
            .background(
                ImageProvider(
                    ClubBackdrop.of(
                        club,
                        (size.width.value * density).toInt(),
                        (size.height.value * density).toInt(),
                    ),
                ),
                ContentScale.Crop,
            )
            .semantics { contentDescription = club.name + ". Nothing in play right now." }
            .clickable(openRoute(ROUTE_LIVE)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(modifier = GlanceModifier.fillMaxSize().padding(18.dp)) {
            Spacer(GlanceModifier.defaultWeight())
            Crest(club, 40.dp)
            Spacer(GlanceModifier.height(10.dp))
            Text(
                text = club.name.uppercase(),
                style = TextStyle(ColorProvider(WHITE), 17.sp, FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = "Nothing in play right now",
                style = TextStyle(ColorProvider(WHITE_60), 12.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

/** The generated badge, at the size asked for. Never a scraped logo — see CrestBitmap. */
@Composable
private fun Crest(theme: ClubTheme, size: androidx.compose.ui.unit.Dp) {
    val density = LocalContext.current.resources.displayMetrics.density
    Image(
        provider = ImageProvider(CrestBitmap.of(theme, (size.value * density).toInt())),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
    )
}

/** Slightly tighter than the glass card's, because this one has no border to soften it. */
private val CLUB_RADIUS = 26.dp
private const val FULL_TIME = 90

/** Two more steps of white, for text that sits over a photograph-like ground. */
internal val WHITE_60 = androidx.compose.ui.graphics.Color(0x99FFFFFF)
internal val WHITE_20 = androidx.compose.ui.graphics.Color(0x33FFFFFF)
