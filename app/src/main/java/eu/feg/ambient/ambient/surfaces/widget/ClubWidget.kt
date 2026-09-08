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
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import eu.feg.ambient.AmbientApp
import eu.feg.ambient.ambient.identity.ClubTheme
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.MatchState
import eu.feg.ambient.data.model.Team
import eu.feg.ambient.ui.nav.Routes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * N6 on the home screen: the customer's club, its next fixture and its last result.
 *
 * The shell is not this widget's business: [GlassCard] and [WidgetHeader] draw the card and
 * its opening line, so the five widgets read as one family. A widget that draws its own card
 * is a bug, not a variation.
 */
class ClubWidget : GlanceAppWidget() {

    // Exact, so the body can decide by width -- the same reason the Live Slip card does.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val club = WidgetStateStore(context).club()
        provideContent {
            // Keyed on the store's version, like AmbientWidget: Glance keeps a composition
            // alive between updates, so anything read once before provideContent stays frozen
            // and the widget silently stops changing. This is what makes "one protection
            // change updates every widget" true rather than nearly true.
            val version by WidgetStoreVersion.flow.collectAsState()
            CompositionLocalProvider(
                LocalClubTheme provides remember(version) { WidgetStateStore(context).club() },
            ) {
                ClubWidgetBody()
            }
        }
    }
}

/**
 * What the card says about the club, resolved from the fixtures.
 *
 * Read inside the composition rather than in provideGlance, because Glance keeps the
 * composition alive between updates (see WidgetStoreVersion) and a countdown read once
 * before provideContent would say "in 3h 40m" all evening. None of the reads suspend --
 * the match list is a StateFlow and the leagues are a plain list -- so there is no work to
 * hoist. The fields are names and a kickoff instant: no odds, no market, no money, because
 * the club card is about the club, not about a bet.
 */
private data class ClubFixtures(
    val competition: String?,
    val next: Match?,
    val last: Match?,
)

@Composable
internal fun ClubWidgetBody() {
    val club = LocalClubTheme.current
    if (club.clubId.isEmpty()) {
        NoClubCard()
        return
    }
    // Guarded: the launcher can compose this in a process where the Application has not
    // been created yet, and a card with no fixtures beats a crash on the home screen.
    val app = LocalContext.current.applicationContext as? AmbientApp
    val fixtures = app?.container?.let { c ->
        clubFixtures(
            matches = c.matchRepository.matches.value,
            leagueNames = c.matchRepository.leagues.associate { it.id to it.name },
            club = club,
        )
    } ?: ClubFixtures(competition = null, next = null, last = null)
    val now = app?.container?.clock?.now() ?: Clock.System.now()
    ClubCard(club, fixtures, now)
}

/** The same card on sample data, for the Surface Lab and a future glance preview. */
@Composable
internal fun ClubWidgetPreviewBody() {
    val club = ClubThemes.HajdukSplit
    val now = Clock.System.now()
    val leagueId = "hr_hnl"
    val fixtures = ClubFixtures(
        competition = "SuperSport HNL",
        next = sampleMatch(
            "club-next", leagueId, "Hajduk Split", "Rijeka", now + 3.hours + 40.minutes, MatchState.PREMATCH,
        ),
        last = sampleMatch(
            "club-last", leagueId, "Hajduk Split", "Osijek", now - 6.days, MatchState.FINISHED, 2, 1,
        ),
    )
    CompositionLocalProvider(LocalClubTheme provides club) {
        ClubCard(club, fixtures, now)
    }
}

// ---- the card --------------------------------------------------------------------------

/**
 * Header, fixture line, last result, two actions. The countdown sits at the end of the
 * fixture line rather than under it: a 4x2 is about 110dp tall, and a card whose buttons
 * fall below the fold has no buttons.
 */
@Composable
private fun ClubCard(club: ClubTheme, fixtures: ClubFixtures, now: Instant) {
    val next = fixtures.next
    val last = fixtures.last
    val nextLine = next?.let { it.home.name + " – " + it.away.name }
    val whenLine = next?.let { countdownLabel(it, now) }
    val lastLine = last?.let {
        "Last: " + it.home.name + " " + (it.homeScore ?: 0) + "–" + (it.awayScore ?: 0) + " " + it.away.name
    }
    // One sentence for TalkBack: the club, then the fixture and when, then the last result.
    val nextSpoken = if (next != null) {
        "Next: " + next.home.name + " against " + next.away.name + ", " + whenLine + "."
    } else {
        "No fixture scheduled."
    }
    val spoken = club.name + ". " + nextSpoken + (lastLine?.let { " " + it + "." } ?: "")

    GlassCard(description = spoken) {
        WidgetHeader(
            title = club.name,
            subtitle = fixtures.competition ?: "No fixture scheduled",
            titleColor = club.accentOnDark,
        )
        Spacer(GlanceModifier.height(6.dp))
        FixtureRow(next, whenLine, nextLine)
        // The last result is the line that gives way: at the 110dp minimum a 4x2 leaves
        // about 80dp inside the shell, and the buttons matter more than a week-old score.
        val contentHeightDp = LocalSize.current.height.value - 2 * PAD_V.value - 2 * BORDER_WIDTH.value
        if (lastLine != null && contentHeightDp >= 100f) {
            Spacer(GlanceModifier.height(2.dp))
            Text(text = lastLine, style = TextStyle(ColorProvider(GREY), 12.sp), maxLines = 1)
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WidgetActionButton(
                label = "Remind me",
                description = "Remind me when " + (nextLine ?: club.name) + " kicks off",
                action = actionRunCallback<RemindMeAction>(),
                modifier = GlanceModifier.defaultWeight(),
                fill = ColorProvider(WHITE_10),
                onFill = ColorProvider(WHITE),
            )
            Spacer(GlanceModifier.width(8.dp))
            WidgetActionButton(
                label = "Open match",
                description = "Open " + (nextLine ?: "the live matches"),
                action = openRoute(ROUTE_LIVE),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

/** Names on the left, countdown on the right. Below a comfortable width the names give way to their codes. */
@Composable
private fun FixtureRow(next: Match?, whenLine: String?, nextLine: String?) {
    val contentWidthDp = LocalSize.current.width.value - 2 * PAD_H.value
    val compact = contentWidthDp < 260f
    val label = when {
        next == null -> "No fixture scheduled"
        compact -> ClubThemes.forTeam(next.home.name).short + " – " + ClubThemes.forTeam(next.away.name).short
        else -> nextLine.orEmpty()
    }
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        Text(
            text = label,
            style = TextStyle(ColorProvider(WHITE), 17.sp, FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (whenLine != null) {
            Spacer(GlanceModifier.width(8.dp))
            // Green is "on", as on the live pill; the countdown itself stays quiet.
            val colour = if (next?.state == MatchState.LIVE) LIVE_GREEN else GREY
            Text(text = whenLine, style = TextStyle(ColorProvider(colour), 12.sp, FontWeight.Medium), maxLines = 1)
        }
    }
}

/** No club yet: the card explains itself and offers the one tap that fixes it. */
@Composable
private fun NoClubCard() {
    GlassCard(description = "My club. Follow a club to see its next fixture and last result here.") {
        WidgetHeader(title = "My club")
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = "Follow a club to see it here",
            style = TextStyle(ColorProvider(WHITE), 15.sp, FontWeight.Medium),
            maxLines = 2,
        )
        Spacer(GlanceModifier.defaultWeight())
        // The Home screen carries the club strip, so that is where the tap lands.
        WidgetActionButton(
            label = "Choose a club",
            description = "Open the app and choose a club to follow",
            action = openRoute(Routes.SPORT),
        )
    }
}

// ---- fixtures ----------------------------------------------------------------------------

/** The club's next fixture (a live one first, then the earliest kickoff) and its last result. */
private fun clubFixtures(matches: List<Match>, leagueNames: Map<String, String>, club: ClubTheme): ClubFixtures {
    val ours = matches.filter { isClub(it.home.name, club) || isClub(it.away.name, club) }
    val next = ours.firstOrNull { it.state == MatchState.LIVE }
        ?: ours.filter { it.state == MatchState.PREMATCH }.minByOrNull { it.kickoff }
    val last = ours.filter { it.state == MatchState.FINISHED }.maxByOrNull { it.kickoff }
    return ClubFixtures(
        competition = (next ?: last)?.let { leagueNames[it.leagueId] },
        next = next,
        last = last,
    )
}

/** Fixtures name teams as text, so the club is matched by name, the way the narrator's facts are. */
private fun isClub(teamName: String, club: ClubTheme): Boolean =
    ClubThemes.byName(teamName)?.clubId == club.clubId

/** "in 3h 40m", "in 25m", "Kicking off now" at or past kickoff, "Live now" once it is on. */
private fun countdownLabel(match: Match, now: Instant): String {
    if (match.state == MatchState.LIVE) return "Live now"
    val minutes = (match.kickoff - now).inWholeMinutes
    return when {
        minutes <= 0L -> "Kicking off now"
        minutes < 60L -> "in " + minutes + "m"
        else -> "in " + (minutes / 60L) + "h " + (minutes % 60L) + "m"
    }
}

private fun sampleMatch(
    id: String,
    leagueId: String,
    home: String,
    away: String,
    kickoff: Instant,
    state: MatchState,
    homeScore: Int? = null,
    awayScore: Int? = null,
) = Match(
    id = id,
    leagueId = leagueId,
    home = Team(id = home.lowercase(), name = home),
    away = Team(id = away.lowercase(), name = away),
    kickoff = kickoff,
    state = state,
    homeScore = homeScore,
    awayScore = awayScore,
)
