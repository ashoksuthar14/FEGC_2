package eu.feg.ambient.ambient.engine.moment

import eu.feg.ambient.ambient.engine.MatchEvent
import eu.feg.ambient.ambient.engine.Moment
import eu.feg.ambient.ambient.engine.MomentBuilder
import eu.feg.ambient.ambient.engine.Ownership
import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.data.model.BetStatus
import eu.feg.ambient.data.model.Leg
import eu.feg.ambient.data.model.LegStatus
import eu.feg.ambient.data.model.PlacedBet
import eu.feg.ambient.data.repo.BetRepository

/**
 * Turns a raw [MatchEvent] into a [Moment] — or into nothing at all.
 *
 * The cheapest work is the work that never starts. Everything downstream of this class costs
 * something: a relevance score, a bandit sample, a narration, a ledger row. So the join with
 * local state happens first and the answer is allowed to be null. A match the user has muted
 * and a match the user owns no part of both stop here, before the narrator is ever asked.
 *
 * [protection] is deliberately *not* a filter here. A blocked or unverified user still needs a
 * ledger row saying we chose silence and why, and that row needs a moment id to point at —
 * DefaultAttentionBudget returns the empty set for those states instead. Refusing to build
 * would make the compliance story unauditable, which is the opposite of the point.
 */
class DefaultMomentBuilder(
    private val betRepository: BetRepository,
    /**
     * There is no follow store yet. Step 16 introduces one and passes its reader in here;
     * until then every event is judged on slip ownership alone.
     */
    private val followedTeams: () -> Set<String> = { emptySet() },
    /** Same shape, same reason: step 16 owns per-match mutes. */
    private val mutedMatches: () -> Set<String> = { emptySet() },
) : MomentBuilder {

    override suspend fun build(event: MatchEvent, protection: ProtectionState): Moment? {
        if (mutedMatches().contains(event.matchId)) return null

        val bet = openBetOn(event.matchId)
        val ownership = ownershipOf(event, bet)
        if (ownership == Ownership.NEITHER) return null

        return Moment(
            id = idFor(event),
            type = event.type,
            facts = factsFor(event, bet, ownership),
            slipId = bet?.id,
            matchId = event.matchId,
            ownership = ownership,
            createdAt = event.at,
        )
    }

    /**
     * The first open slip touching this match. One slip is enough: the surfaces show a single
     * slip at a time, and picking the oldest keeps that choice stable across ticks rather than
     * flickering between two bets on the same fixture.
     */
    private fun openBetOn(matchId: String): PlacedBet? =
        betRepository.placedBets.value
            .filter { it.status == BetStatus.OPEN }
            .firstOrNull { bet -> bet.legs.any { it.matchId == matchId } }

    /**
     * Matched on match id rather than on leg status. A LEG_DECIDED event arrives at the
     * instant the leg flips, and whether the repository has already written WON is a race we
     * do not want ownership to depend on.
     */
    private fun ownershipOf(event: MatchEvent, bet: PlacedBet?): Ownership = when {
        bet != null && decidesALeg(event) -> Ownership.DECIDES_A_LEG
        bet != null -> Ownership.ON_MY_SLIP
        followedTeamIn(event) != null -> Ownership.FOLLOWED_TEAM
        else -> Ownership.NEITHER
    }

    /**
     * Two ways an event settles a leg: it says so outright, or it is a goal late enough that
     * the scoreline it produced is unlikely to be undone. Ten minutes is the window the
     * product uses everywhere for "this is now about to be true".
     */
    private fun decidesALeg(event: MatchEvent): Boolean = when (event.type) {
        MomentType.LEG_DECIDED, MomentType.LEG_LOST, MomentType.SLIP_SETTLED -> true
        MomentType.GOAL_ON_SLIP -> event.minute >= FULL_TIME - DECIDING_WINDOW_MINUTES
        else -> false
    }

    private fun followedTeamIn(event: MatchEvent): String? {
        val followed = followedTeams()
        if (followed.isEmpty()) return null
        return when {
            followed.contains(event.homeTeam) -> event.homeTeam
            followed.contains(event.awayTeam) -> event.awayTeam
            else -> null
        }
    }

    /**
     * Everything the narrator is allowed to know, and nothing else.
     *
     * COMPLIANCE — the leg contributes its [Leg.description] and never its [Leg.odds], and the
     * slip contributes leg counts and never its stake. MomentFacts has no field to carry money
     * and this builder must never be the reason one is added.
     */
    private fun factsFor(event: MatchEvent, bet: PlacedBet?, ownership: Ownership): MomentFacts {
        val legs = bet?.legs.orEmpty()
        val mine = legs.firstOrNull { it.matchId == event.matchId }
        return MomentFacts(
            type = event.type,
            homeTeam = event.homeTeam,
            awayTeam = event.awayTeam,
            homeScore = event.homeScore,
            awayScore = event.awayScore,
            minute = event.minute,
            period = event.period,
            scorer = event.scorer,
            legsTotal = if (legs.isEmpty()) null else legs.size,
            legsWon = if (legs.isEmpty()) null else legs.count { it.status == LegStatus.WON },
            legsLost = if (legs.isEmpty()) null else legs.count { it.status == LegStatus.LOST },
            myLegDescription = mine?.description,
            minutesRemaining = (FULL_TIME - event.minute).coerceAtLeast(0),
            followedTeam = if (ownership == Ownership.FOLLOWED_TEAM) followedTeamIn(event) else null,
        )
    }

    /**
     * Stable within one event and unique across events: the ledger joins reward rows back to
     * this id later, so a second goal in the same minute must not collide with the first.
     */
    private fun idFor(event: MatchEvent): String =
        event.matchId + ":" + event.type.name + "@" + event.at.toEpochMilliseconds()

    private companion object {
        const val FULL_TIME = 90

        /** "Late enough to decide it" — the same ten minutes the surfaces already use. */
        const val DECIDING_WINDOW_MINUTES = 10
    }
}
