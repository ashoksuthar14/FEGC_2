package eu.feg.ambient.ui.mapping

import eu.feg.ambient.core.formatKickoff
import eu.feg.ambient.core.formatLiveMinute
import eu.feg.ambient.core.formatOdds
import eu.feg.ambient.data.model.Market
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
    )
}

/** The market a row's odds group should show: 1/X/2 unless the match has nothing else. */
fun Match.primaryMarket(): Market? =
    markets.firstOrNull { it.name == "Match" } ?: markets.firstOrNull()
