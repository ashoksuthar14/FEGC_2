package eu.feg.ambient.ui.mapping

import eu.feg.ambient.core.formatKickoff
import eu.feg.ambient.core.formatLiveMinute
import eu.feg.ambient.core.formatOdds
import eu.feg.ambient.data.model.Market
import eu.feg.ambient.data.model.BetStatus
import eu.feg.ambient.data.model.LegStatus
import eu.feg.ambient.data.model.PlacedBet
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.MatchState
import eu.feg.ambient.ui.components.MatchRowUi
import eu.feg.ambient.ui.components.OddsFlash
import eu.feg.ambient.ui.components.OddsState
import eu.feg.ambient.ui.components.RowOdd
import kotlinx.datetime.Instant

/**
 * Turns a domain [Match] into the presentation model the row renders. This is the only
 * place the two vocabularies meet, which keeps `ui/components` free of `data/`
 * (CLAUDE.md rule 7).
 */
fun Match.toRowUi(
    now: Instant,
    selectedOutcomeIds: Set<String> = emptySet(),
    oddsMoves: Map<String, Int> = emptyMap(),
    market: Market? = null,
    isFavourite: Boolean = false,
    /** From the customer's open slip on this match, for the spoken sentence only. */
    legsWon: Int? = null,
    legsTotal: Int? = null,
): MatchRowUi {
    val shown = market ?: markets.firstOrNull()
    val live = state == MatchState.LIVE

    return MatchRowUi(
        id = id,
        homeName = home.name,
        awayName = away.name,
        timeText = if (live) formatLiveMinute(period, minute) else formatKickoff(kickoff, now),
        isLive = live,
        homeScore = homeScore,
        awayScore = awayScore,
        badges = badges,
        // Prematch rows say "Basic offer" above 1/X/2; live rows name the market itself.
        marketLabel = if (live) shown?.name else "Basic offer",
        odds = shown?.outcomes.orEmpty().map { outcome ->
            RowOdd(
                label = outcome.label,
                value = formatOdds(outcome.odds),
                state = when {
                    outcome.locked -> OddsState.LOCKED
                    (id + "/" + outcome.id) in selectedOutcomeIds -> OddsState.SELECTED
                    else -> OddsState.DEFAULT
                },
                isTop = outcome.isTop,
                flash = when (oddsMoves[outcome.id + "@" + id]) {
                    -1 -> OddsFlash.DOWN
                    1 -> OddsFlash.UP
                    else -> OddsFlash.NONE
                },
            )
        },
        isFavourite = isFavourite,
        legsWon = legsWon,
        legsTotal = legsTotal,
    )
}

/**
 * The customer's open slip that includes this match, as (legs won, legs total), or null.
 *
 * Feeds the row's spoken sentence -- "two of your three legs won" -- and nothing visual: the
 * row already shows the match, and a screen-reader user is the one who cannot glance at the
 * My Bets tab to find out how the slip is doing.
 */
fun List<PlacedBet>.slipLegsFor(matchId: String): Pair<Int, Int>? {
    val bet = firstOrNull { it.status == BetStatus.OPEN && it.legs.any { l -> l.matchId == matchId } }
        ?: return null
    return bet.legs.count { it.status == LegStatus.WON } to bet.legs.size
}

/** The market a row's odds group should show: 1/X/2 unless the match has nothing else. */
fun Match.primaryMarket(): Market? =
    markets.firstOrNull { it.name == "Match" } ?: markets.firstOrNull()
