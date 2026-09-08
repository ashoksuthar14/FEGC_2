package eu.feg.ambient.ambient.surfaces

import kotlin.time.Duration.Companion.minutes

/**
 * Ready-made slips so every surface has something real to render before the Moment Engine
 * exists. The Surface Lab picks between these; step 13B replaces the Lab, not this file.
 *
 * Fixtures match the mock data the rest of the app uses, so a judge sees the same clubs on
 * the lock screen as in the offer.
 */
object DemoSurfaceData {

    /** Liverpool–Ipswich 1–0 at 61', two legs home, one still running → "2/3 ✓ · 61'". */
    fun threeLegLive(protection: ProtectionState = ProtectionState.NORMAL) = SlipSurfaceState(
        slipId = "demo-live",
        protection = protection,
        legs = listOf(
            LegState("Liverpool to win", "Liverpool – Ipswich", LegStatus.PENDING),
            LegState("Betis to win", "Betis – Real Madrid", LegStatus.WON),
            LegState("Sparta to win", "Sparta – Plzeň", LegStatus.WON),
        ),
        activeMatch = "Liverpool – Ipswich",
        homeTeam = "Liverpool",
        awayTeam = "Ipswich",
        homeScore = 1,
        awayScore = 0,
        minute = 61,
        period = "2. poluvrijeme",
        minutesRemaining = 29,
        competition = "Premier League",
        region = "ENG",
        goalMinutes = listOf(23),
    )

    /** One won, one lost, nothing left running. */
    fun twoLegSettled(protection: ProtectionState = ProtectionState.NORMAL) = SlipSurfaceState(
        slipId = "demo-settled",
        protection = protection,
        legs = listOf(
            LegState("Genoa to win", "Genoa – Como", LegStatus.WON),
            LegState("Varaždin to win", "Varaždin – Istria 1961", LegStatus.LOST),
        ),
        activeMatch = null,
        homeTeam = "Genoa",
        awayTeam = "Como",
        homeScore = 2,
        awayScore = 1,
        minute = 90,
        period = "Kraj",
        minutesRemaining = 0,
        settled = true,
    )

    /** Kickoff in 40 minutes, nothing decided yet. */
    fun oneLegPreMatch(protection: ProtectionState = ProtectionState.NORMAL) = SlipSurfaceState(
        slipId = "demo-prematch",
        protection = protection,
        legs = listOf(
            LegState("Varaždin to win", "Varaždin – Istria 1961", LegStatus.PENDING),
        ),
        activeMatch = null,
        homeTeam = "Varaždin",
        awayTeam = "Istria 1961",
        minutesRemaining = null,
    )

    val preMatchWidget: WidgetState
        get() = WidgetState.PreMatch(
            match = "Varaždin – Istria 1961",
            kickoffIn = 40.minutes,
            legs = oneLegPreMatch().legs,
        )

    val all: List<SlipSurfaceState>
        get() = listOf(threeLegLive(), twoLegSettled(), oneLegPreMatch())
}
