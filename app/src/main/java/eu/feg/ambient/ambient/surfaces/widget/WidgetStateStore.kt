package eu.feg.ambient.ambient.surfaces.widget

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import eu.feg.ambient.ambient.identity.ClubTheme
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.narrator.NarratedText
import eu.feg.ambient.ambient.narrator.NarratorEngine
import eu.feg.ambient.ambient.surfaces.LegState
import eu.feg.ambient.ambient.surfaces.LegStatus
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.surfaces.SlipSurfaceState
import eu.feg.ambient.ambient.surfaces.WidgetState
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The last [WidgetState] we asked the launcher to draw, kept on disk.
 *
 * WHY: the launcher redraws a widget whenever it feels like it — after a reboot, after our
 * process is killed, after a config change — and it calls provideGlance with nothing but a
 * GlanceId. Anything held only in memory is gone by then and the customer gets a blank card
 * on their home screen, which is the one place a blank card is most expensive.
 *
 * SharedPreferences + kotlinx.serialization, matching AlertBudget: DataStore would be a new
 * dependency for one JSON blob. Glance's own state definition would work too, but it is
 * DataStore underneath and it would put the schema somewhere the rest of the surface package
 * cannot read.
 *
 * WidgetState is a sealed interface over types that carry Duration, Instant and NarratedText,
 * none of which is @Serializable. Rather than annotate types the surfaces package owns for
 * the sake of one consumer, this file keeps a flat mirror ([WidgetSnapshot]) and converts.
 * A mirror is the simpler of the two options the brief offered: manual encoding would mean
 * hand-writing a parser for six shapes, and one flat data class with nullable fields is both
 * shorter and harder to get quietly wrong.
 *
 * COMPLIANCE — the mirror has no field for stake, odds, returns or balance because the types
 * it mirrors have none. Do not add one here either; this file would be an easy back door.
 */
class WidgetStateStore(private val prefs: SharedPreferences?) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
    )

    fun save(state: WidgetState) {
        val json = runCatching { JSON.encodeToString(WidgetSnapshot.from(state)) }.getOrElse {
            Log.w(TAG, "could not encode " + state.javaClass.simpleName, it)
            return
        }
        // A thumb answers one card. When the card changes the question is a new one, so the
        // mark is dropped and the buttons come back — otherwise a single tap would silence
        // the learning signal for the rest of the session.
        val changed = prefs?.getString(KEY, null) != json
        prefs?.edit()?.putString(KEY, json)?.also { e ->
            if (changed) e.remove(KEY_FEEDBACK)
        }?.apply()
    }

    /** Which gesture, if any, the customer has already given on the card now showing. */
    internal fun feedback(): FeedbackMark? = prefs?.getString(KEY_FEEDBACK, null)
        ?.let { name -> FeedbackMark.entries.firstOrNull { it.name == name } }

    internal fun setFeedback(mark: FeedbackMark) {
        prefs?.edit()?.putString(KEY_FEEDBACK, mark.name)?.apply()
    }

    /** N6. Stored as the id and resolved on load, so a palette edit reaches stored widgets. */
    fun saveClub(clubId: String) {
        prefs?.edit()?.putString(KEY_CLUB, clubId)?.apply()
    }

    fun club(): ClubTheme = ClubThemes.byId(prefs?.getString(KEY_CLUB, null))

    /** Idle with nothing to show is the honest answer when there is no stored state. */
    fun load(): WidgetState {
        val json = prefs?.getString(KEY, null) ?: return EMPTY
        return runCatching { JSON.decodeFromString<WidgetSnapshot>(json).toWidgetState() }
            .getOrElse {
                Log.w(TAG, "could not decode stored widget state", it)
                EMPTY
            }
    }

    fun clear() {
        prefs?.edit()?.remove(KEY)?.remove(KEY_FEEDBACK)?.apply()
    }

    private companion object {
        const val TAG = "WidgetStateStore"
        const val PREFS = "ambient_widget"
        const val KEY = "widget_state"
        const val KEY_FEEDBACK = "widget_feedback"
        const val KEY_CLUB = "widget_club"
        val EMPTY: WidgetState = WidgetState.Idle(nextFixture = null, kickoff = null)
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

/**
 * What the customer already told us about the card currently on screen.
 *
 * It is stored beside the snapshot rather than inside it because it is not part of what the
 * engine asked us to draw — it is the answer to it, and it has to survive the redraw that
 * follows the tap.
 */
internal enum class FeedbackMark { UP, DOWN, MUTED }

@Serializable
internal enum class SnapshotKind { PRE_MATCH, LIVE, SETTLED, DIGEST, IDLE, PROTECTED }

@Serializable
internal data class LegSnapshot(
    val description: String,
    val match: String,
    val status: LegStatus,
) {
    fun toLegState() = LegState(description, match, status)

    companion object {
        fun from(leg: LegState) = LegSnapshot(leg.description, leg.match, leg.status)
    }
}

@Serializable
internal data class WidgetSnapshot(
    val kind: SnapshotKind,
    val legs: List<LegSnapshot> = emptyList(),
    // PreMatch. Stored as the absolute kickoff instant rather than the remaining Duration:
    // a countdown frozen at "in 40 minutes" is worse than no countdown at all once the phone
    // has been off for an hour, so the remaining time is recomputed on load.
    val match: String? = null,
    val kickoffAtMillis: Long? = null,
    // Live / Settled.
    val slipId: String? = null,
    val protection: ProtectionState = ProtectionState.NORMAL,
    val activeMatch: String? = null,
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val minute: Int? = null,
    val period: String? = null,
    val minutesRemaining: Int? = null,
    val narratedHeadline: String? = null,
    val narratedDetail: String? = null,
    /** N1: the spoken variant travels with the snapshot, or the widget button has nothing to say. */
    val narratedSpoken: String? = null,
    val narratedEngine: NarratorEngine? = null,
    val narratedLatencyMs: Long = 0L,
    val settled: Boolean = false,
    // Digest.
    val headline: String? = null,
    val detail: String? = null,
    val sinceMillis: Long? = null,
    // Idle.
    val nextFixture: String? = null,
    // Protected.
    val lastRegisterCheckMillis: Long? = null,
) {

    fun toWidgetState(): WidgetState = when (kind) {
        SnapshotKind.PRE_MATCH -> WidgetState.PreMatch(
            match = match.orEmpty(),
            kickoffIn = remainingToKickoff(),
            legs = legs.map { it.toLegState() },
        )

        SnapshotKind.LIVE -> WidgetState.Live(toSlip())
        SnapshotKind.SETTLED -> WidgetState.Settled(toSlip())

        SnapshotKind.DIGEST -> WidgetState.Digest(
            headline = headline.orEmpty(),
            detail = detail.orEmpty(),
            since = sinceMillis?.let { Instant.fromEpochMilliseconds(it) } ?: Clock.System.now(),
        )

        SnapshotKind.IDLE -> WidgetState.Idle(
            nextFixture = nextFixture,
            kickoff = kickoffAtMillis?.let { Instant.fromEpochMilliseconds(it) },
        )

        SnapshotKind.PROTECTED -> WidgetState.Protected(
            protection = protection,
            lastRegisterCheck = lastRegisterCheckMillis?.let { Instant.fromEpochMilliseconds(it) },
        )
    }

    private fun remainingToKickoff(): Duration {
        val at = kickoffAtMillis ?: return Duration.ZERO
        val left = (at - Clock.System.now().toEpochMilliseconds()) / 1000
        return if (left <= 0) Duration.ZERO else left.seconds
    }

    private fun toSlip() = SlipSurfaceState(
        slipId = slipId.orEmpty(),
        protection = protection,
        legs = legs.map { it.toLegState() },
        activeMatch = activeMatch,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        homeScore = homeScore,
        awayScore = awayScore,
        minute = minute,
        period = period,
        minutesRemaining = minutesRemaining,
        narrated = narratedHeadline?.let {
            NarratedText(
                headline = it,
                detail = narratedDetail.orEmpty(),
                spokenText = narratedSpoken.orEmpty(),
                engine = narratedEngine ?: NarratorEngine.TEMPLATE,
                latencyMs = narratedLatencyMs,
            )
        },
        settled = settled,
    )

    companion object {
        fun from(state: WidgetState): WidgetSnapshot = when (state) {
            is WidgetState.PreMatch -> WidgetSnapshot(
                kind = SnapshotKind.PRE_MATCH,
                match = state.match,
                kickoffAtMillis = Clock.System.now().toEpochMilliseconds() +
                    state.kickoffIn.inWholeMilliseconds,
                legs = state.legs.map { LegSnapshot.from(it) },
            )

            is WidgetState.Live -> fromSlip(SnapshotKind.LIVE, state.slip)
            is WidgetState.Settled -> fromSlip(SnapshotKind.SETTLED, state.slip)

            is WidgetState.Digest -> WidgetSnapshot(
                kind = SnapshotKind.DIGEST,
                headline = state.headline,
                detail = state.detail,
                sinceMillis = state.since.toEpochMilliseconds(),
            )

            is WidgetState.Idle -> WidgetSnapshot(
                kind = SnapshotKind.IDLE,
                nextFixture = state.nextFixture,
                kickoffAtMillis = state.kickoff?.toEpochMilliseconds(),
            )

            is WidgetState.Protected -> WidgetSnapshot(
                kind = SnapshotKind.PROTECTED,
                protection = state.protection,
                lastRegisterCheckMillis = state.lastRegisterCheck?.toEpochMilliseconds(),
            )
        }

        private fun fromSlip(kind: SnapshotKind, slip: SlipSurfaceState) = WidgetSnapshot(
            kind = kind,
            slipId = slip.slipId,
            protection = slip.protection,
            legs = slip.legs.map { LegSnapshot.from(it) },
            activeMatch = slip.activeMatch,
            homeTeam = slip.homeTeam,
            awayTeam = slip.awayTeam,
            homeScore = slip.homeScore,
            awayScore = slip.awayScore,
            minute = slip.minute,
            period = slip.period,
            minutesRemaining = slip.minutesRemaining,
            narratedHeadline = slip.narrated?.headline,
            narratedDetail = slip.narrated?.detail,
            narratedSpoken = slip.narrated?.spokenText,
            narratedEngine = slip.narrated?.engine,
            narratedLatencyMs = slip.narrated?.latencyMs ?: 0L,
            settled = slip.settled,
        )
    }
}
