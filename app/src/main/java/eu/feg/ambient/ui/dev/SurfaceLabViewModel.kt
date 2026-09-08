package eu.feg.ambient.ui.dev

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.NarratedText
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.Tone
import eu.feg.ambient.ambient.surfaces.AndroidSurfaceController
import eu.feg.ambient.ambient.surfaces.LegState
import eu.feg.ambient.ambient.surfaces.LegStatus
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.identity.ClubTheme
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.recap.RecapPeriod
import eu.feg.ambient.ambient.surfaces.SlipSurfaceState
import eu.feg.ambient.ambient.surfaces.SpeakResult
import eu.feg.ambient.ambient.surfaces.SpokenSurface
import eu.feg.ambient.ambient.surfaces.SurfaceDiagnostics
import eu.feg.ambient.ambient.surfaces.WidgetState
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.ui.nav.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

/** The three ready-made slips from DemoSurfaceData, with labels short enough for a chip. */
enum class DemoSlip(val label: String) {
    THREE_LEG_LIVE("3 legs · live"),
    TWO_LEG_SETTLED("2 legs · settled"),
    ONE_LEG_PRE_MATCH("1 leg · pre-match"),
}

/** One button per WidgetState variant, so the Lab can force any of them on demand. */
enum class WidgetKind(val label: String) {
    PRE_MATCH("PreMatch"),
    LIVE("Live"),
    SETTLED("Settled"),
    DIGEST("Digest"),
    IDLE("Idle"),
    PROTECTED("Protected"),
}

data class SurfaceLabUiState(
    val slip: SlipSurfaceState,
    val demo: DemoSlip = DemoSlip.THREE_LEG_LIVE,
    val protection: ProtectionState = ProtectionState.NORMAL,
    val tone: Tone = Tone.PLAIN,
    val language: NarratorLanguage = NarratorLanguage.EN,
    /** The text the last action generated — shown with its engine, which must be the true one. */
    val narrated: NarratedText? = null,
    val lastAction: String = "nothing yet",
    val widgetKind: WidgetKind? = null,
    val alertNotice: String? = null,
    val shortcutNotice: String? = null,
    /** N1: what the last "Speak current moment" tap actually did. */
    val speakNotice: String? = null,
    /** Step 16: the digest the Lab last built, in words. */
    val digestNotice: String? = null,
    /** 17B: the recap the Lab last generated, with its raw counts. */
    val recapNotice: String? = null,
    val running: Boolean = false,
)

/**
 * Step 14D. Drives every OS surface by hand so they can be demonstrated without waiting for
 * a match, and so the stage demo has a manual fallback if the simulator misbehaves.
 *
 * Every action here goes through [AppContainer.surfaceController] and never touches a
 * renderer. Step 13B's Moment Engine replaces this class by calling the same methods, which
 * is only a drop-in for as long as that stays true.
 */
class SurfaceLabViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SurfaceLabUiState(slip = container.demoData.threeLegLive()))
    val state: StateFlow<SurfaceLabUiState> = _state.asStateFlow()

    /** Straight from the controller: the Lab reports what the system did, not what we asked. */
    val diagnostics: StateFlow<SurfaceDiagnostics> = container.surfaceController.diagnostics

    /** resetAlertBudget and the permission re-read are demo affordances, not part of the contract. */

    // ---- builder -------------------------------------------------------------------------

    fun selectSlip(demo: DemoSlip) {
        val protection = _state.value.protection
        _state.value = _state.value.copy(
            demo = demo,
            slip = buildSlip(demo, protection),
            narrated = null,
            lastAction = "Slip " + demo.label,
        )
    }

    /**
     * Calm Mode is the demo moment and it has to land at once, so an active Live Update is
     * re-posted here rather than waiting for the operator to press another button.
     */
    fun setProtection(protection: ProtectionState) {
        viewModelScope.launch {
            val slip = _state.value.slip.copy(protection = protection)
            _state.value = _state.value.copy(
                protection = protection,
                slip = slip,
                lastAction = "Protection " + protection.name,
            )
            if (diagnostics.value.activeLiveUpdateSlipId != null) {
                container.surfaceController.updateLiveUpdate(slip)
            }
        }
    }

    fun setTone(tone: Tone) {
        _state.value = _state.value.copy(tone = tone)
    }

    fun setLanguage(language: NarratorLanguage) {
        _state.value = _state.value.copy(language = language)
    }

    // ---- live update ---------------------------------------------------------------------

    fun start() = act("Start", MomentType.KICKOFF_FOLLOWED, start = true) { it }

    fun advanceMinute() = act("+1 minute", MomentType.MINUTES_REMAINING) { slip ->
        slip.copy(
            minute = (slip.minute ?: 0) + 1,
            minutesRemaining = slip.minutesRemaining?.let { (it - 1).coerceAtLeast(0) },
        )
    }

    fun homeGoal() = act("Home goal", MomentType.GOAL_ON_SLIP) { slip ->
        slip.copy(homeScore = (slip.homeScore ?: 0) + 1)
    }

    fun awayGoal() = act("Away goal", MomentType.GOAL_ON_SLIP) { slip ->
        slip.copy(awayScore = (slip.awayScore ?: 0) + 1)
    }

    fun winNextLeg() = act("Win next leg", MomentType.LEG_DECIDED) { slip ->
        slip.copy(legs = slip.legs.decideFirstPending(LegStatus.WON))
    }

    fun loseNextLeg() = act("Lose next leg", MomentType.LEG_LOST) { slip ->
        slip.copy(legs = slip.legs.decideFirstPending(LegStatus.LOST))
    }

    /**
     * Only flips the settled flag. Pending legs are left as they are on purpose: the operator
     * decides each leg with Win/Lose, and inventing an outcome here would put a result on the
     * lock screen that nobody asked for.
     */
    fun settleSlip() = act("Settle slip", MomentType.SLIP_SETTLED) { slip ->
        slip.copy(settled = true)
    }

    fun endLiveUpdate() {
        viewModelScope.launch {
            val slip = _state.value.slip
            container.surfaceController.endLiveUpdate(slip.slipId, settled = slip.settled)
            _state.value = _state.value.copy(lastAction = "End / dismiss")
        }
    }

    // ---- widget --------------------------------------------------------------------------

    fun showWidget(kind: WidgetKind) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(running = true)

            // Only the digest is written by the narrator; the rest render from the slip itself.
            val narrated = if (kind == WidgetKind.DIGEST) {
                container.narrator.narrate(
                    facts(MomentType.AWAY_DIGEST, current.slip),
                    current.tone,
                    current.language,
                )
            } else {
                current.narrated
            }

            container.surfaceController.refreshWidget(widgetState(kind, current.slip, narrated))
            _state.value = _state.value.copy(
                running = false,
                narrated = narrated,
                widgetKind = kind,
                lastAction = "Widget " + kind.label,
            )
        }
    }

    /** Re-sends whatever the widget is already showing, to prove the update path works. */
    fun forceRefreshWidget() = showWidget(_state.value.widgetKind ?: WidgetKind.LIVE)

    // ---- alerts and shortcuts ------------------------------------------------------------

    fun sendSettlementAlert() {
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(running = true, alertNotice = null)
            val narrated = container.narrator.narrate(
                facts(MomentType.SLIP_SETTLED, current.slip),
                current.tone,
                current.language,
            )
            val sent = container.surfaceController.postAlert(
                headline = narrated.headline,
                detail = narrated.detail,
                deepLink = Routes.MY_BETS,
            )
            _state.value = _state.value.copy(
                running = false,
                narrated = narrated,
                alertNotice = if (sent) "Alert posted." else "Budget spent — nothing was posted.",
                lastAction = "Send settlement alert",
            )
        }
    }

    /**
     * Reads the slip currently loaded in the Lab, so the speaker can be demonstrated without
     * waiting for a live match — and so the silent-phone and missing-voice paths can be shown
     * on stage rather than described.
     */
    fun speakCurrentMoment() {
        viewModelScope.launch {
            val slip = _state.value.slip.copy(
                protection = _state.value.protection,
                narrated = _state.value.narrated,
            )
            val result = container.spokenMoments.speakSlip(slip, Surface.IN_APP)
            _state.value = _state.value.copy(
                speakNotice = describe(result, SpokenSurface.forSlip(slip)),
                lastAction = "Speak current moment",
            )
        }
    }

    private fun describe(result: SpeakResult, spoken: String?): String = when (result) {
        is SpeakResult.Spoken -> "Spoken: \"" + spoken + "\""
        is SpeakResult.Silenced -> "Silenced — the phone is on silent or in Do Not Disturb."
        is SpeakResult.LanguageFallback ->
            if (result.usedEnglish) {
                "LanguageFallback — no Croatian voice installed, read in English."
            } else {
                "LanguageFallback — read in the device's default voice."
            }
        is SpeakResult.Unavailable -> "Unavailable — " + result.reason + "."
    }

    /**
     * N6 on stage. Switching club here goes through the same repository the Home strip
     * writes to, so the surfaces repaint by the same path — this is a shortcut to the
     * control, not a second way of doing it.
     */
    val myClubTheme: StateFlow<ClubTheme> = container.myClubTheme

    fun setMyClub(clubId: String?) {
        container.userStateRepository.setMyClub(clubId)
        _state.value = _state.value.copy(
            lastAction = "Club: " + (ClubThemes.byId(clubId).name),
        )
    }

    // --- step 16: the digest, on demand ---------------------------------------------------

    /** Winds the clock back so the away threshold is met without waiting ninety minutes. */
    fun simulateAway(minutes: Int = 90) {
        container.awayTracker.simulateAway(minutes)
        _state.value = _state.value.copy(
            digestNotice = "Away for " + minutes + " min. Next widget redraw shows the digest.",
            lastAction = "Simulate " + minutes + " minutes away",
        )
        // The widget is the trigger, so poking it is the honest way to demonstrate it.
        viewModelScope.launch { container.surfaceController.refreshWidget(currentWidget()) }
    }

    /**
     * Builds the digest here and now and shows what it would say.
     *
     * Deliberately does NOT mark it shown: the Lab is for looking at the thing, and marking
     * it here would consume the away-period that the demo is about to use on the widget.
     */
    fun buildDigestNow() {
        viewModelScope.launch {
            val since = container.awayTracker.lastInteractionAt()
            val digest = since?.let { container.digestBuilder.build(it) }
            _state.value = _state.value.copy(
                digestNotice = when {
                    since == null -> "No interaction recorded yet — open the app first."
                    digest == null ->
                        "Null, correctly: nothing worth a card. A digest that says " +
                            "\"nothing happened\" is the one thing it must never say."
                    else -> digest.headline + " — " + digest.detail +
                        "  [" + digest.items.size + " items] " +
                        digest.items.joinToString(" · ") { it.text }
                },
                lastAction = "Build digest now",
            )
        }
    }

    /** N5 on demand. Does not mark the recap seen -- the Lab is for looking. */
    fun generateRecap(period: RecapPeriod) {
        viewModelScope.launch {
            val recap = container.recapBuilder.build(period, container.clock.now())
            _state.value = _state.value.copy(
                recapNotice = if (recap == null) {
                    "Null, correctly: fewer than 3 matches followed this " +
                        period.name.lowercase() + ". Seed a month first (Surface demo: seed)."
                } else {
                    recap.headline + " \u2014 " + recap.detail +
                        "\n\u201c" + recap.spokenText + "\u201d" +
                        "\nmatches " + recap.matchesFollowed +
                        " \u00b7 teams " + recap.teamsFollowed.size +
                        " \u00b7 top " + (recap.topTeam ?: "-") + " \u00d7" + recap.topTeamCount +
                        " \u00b7 predictions " + recap.predictionsRight + "/" + recap.predictionsTotal +
                        " \u00b7 check-ins " + recap.checkIns +
                        " \u00b7 streak " + recap.longestStreak
                },
                lastAction = "Generate recap (" + period.name.lowercase() + ")",
            )
        }
    }

    fun rebuildShortcuts() {
        viewModelScope.launch {
            val protection = container.protectionEvaluator.evaluate()
            container.surfaceController.refreshShortcuts(protection)
            _state.value = _state.value.copy(
                shortcutNotice = "Published for " + protection.name + ".",
                lastAction = "Rebuild shortcuts",
            )
        }
    }

    private fun currentWidget() =
        container.surfaceController.currentWidgetState()
            ?: WidgetState.Idle(nextFixture = null, kickoff = null)

    fun resetAlertBudget() {
        container.surfaceController.resetAlertBudget()
        _state.value = _state.value.copy(alertNotice = "Budget reset (demo only).")
    }

    fun refreshShortcuts() {
        viewModelScope.launch {
            val protection = _state.value.protection
            container.surfaceController.refreshShortcuts(protection)
            _state.value = _state.value.copy(
                shortcutNotice = "Shortcuts refreshed for " + protection.name + ".",
                lastAction = "Refresh shortcuts",
            )
        }
    }

    /** Called after the permission dialog closes, and when the screen comes back into view. */
    fun refreshDiagnostics() {
        container.surfaceController.refreshDiagnostics()
    }

    // ---- internals -----------------------------------------------------------------------

    /**
     * The one shape every Live Update button takes: transform the slip, ask the narrator for
     * fresh text about it, then hand the result to the controller.
     */
    private fun act(
        label: String,
        type: MomentType,
        start: Boolean = false,
        transform: (SlipSurfaceState) -> SlipSurfaceState,
    ) {
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(running = true)

            val base = transform(current.slip)
            val narrated = container.narrator.narrate(facts(type, base), current.tone, current.language)
            val next = base.copy(narrated = narrated)

            if (start) {
                container.surfaceController.startLiveUpdate(next)
            } else {
                container.surfaceController.updateLiveUpdate(next)
            }

            _state.value = _state.value.copy(
                running = false,
                slip = next,
                narrated = narrated,
                lastAction = label,
            )
        }
    }

    private fun buildSlip(demo: DemoSlip, protection: ProtectionState) = when (demo) {
        DemoSlip.THREE_LEG_LIVE -> container.demoData.threeLegLive(protection)
        DemoSlip.TWO_LEG_SETTLED -> container.demoData.twoLegSettled(protection)
        DemoSlip.ONE_LEG_PRE_MATCH -> container.demoData.oneLegPreMatch(protection)
    }

    private fun widgetState(
        kind: WidgetKind,
        slip: SlipSurfaceState,
        narrated: NarratedText?,
    ): WidgetState = when (kind) {
        WidgetKind.PRE_MATCH -> WidgetState.PreMatch(
            match = slip.activeMatch ?: slip.legs.firstOrNull()?.match.orEmpty(),
            kickoffIn = KICKOFF_IN_MINUTES.minutes,
            legs = slip.legs,
        )
        WidgetKind.LIVE -> WidgetState.Live(slip)
        WidgetKind.SETTLED -> WidgetState.Settled(slip.copy(settled = true))
        WidgetKind.DIGEST -> WidgetState.Digest(
            headline = narrated?.headline ?: "While you were away",
            detail = narrated?.detail.orEmpty(),
            since = Clock.System.now(),
        )
        WidgetKind.IDLE -> WidgetState.Idle(
            nextFixture = slip.legs.firstOrNull()?.match,
            kickoff = null,
        )
        WidgetKind.PROTECTED -> WidgetState.Protected(
            protection = slip.protection,
            lastRegisterCheck = Clock.System.now(),
        )
    }

    /**
     * COMPLIANCE — every field here comes from the slip's own words and scores. There is no
     * odds, stake or balance to copy across, and MomentFacts has nowhere to put one.
     */
    private fun facts(type: MomentType, slip: SlipSurfaceState) = MomentFacts(
        type = type,
        homeTeam = slip.homeTeam,
        awayTeam = slip.awayTeam,
        homeScore = slip.homeScore,
        awayScore = slip.awayScore,
        minute = slip.minute,
        period = slip.period,
        legsTotal = slip.legsTotal,
        legsWon = slip.legsWon,
        legsLost = slip.legsLost,
        myLegDescription = slip.legs.firstOrNull { it.status == LegStatus.PENDING }?.description
            ?: slip.legs.firstOrNull()?.description,
        minutesRemaining = slip.minutesRemaining,
        // Only the kickoff moment is about a followed team; elsewhere it would just be noise.
        followedTeam = if (type == MomentType.KICKOFF_FOLLOWED) slip.homeTeam else null,
        kickoffInMinutes = if (type == MomentType.KICKOFF_FOLLOWED) KICKOFF_IN_MINUTES else null,
        digestItems = if (type == MomentType.AWAY_DIGEST) {
            slip.legs.map { it.description + " — " + it.status.name.lowercase() }
        } else {
            emptyList()
        },
    )

    private fun List<LegState>.decideFirstPending(status: LegStatus): List<LegState> {
        val index = indexOfFirst { it.status == LegStatus.PENDING }
        if (index < 0) return this
        return toMutableList().also { it[index] = it[index].copy(status = status) }
    }

    private companion object {
        /** Matches DemoSurfaceData.preMatchWidget, so both pre-match paths tell one story. */
        const val KICKOFF_IN_MINUTES = 40
    }
}
