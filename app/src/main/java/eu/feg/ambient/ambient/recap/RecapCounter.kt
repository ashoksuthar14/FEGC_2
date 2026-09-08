package eu.feg.ambient.ambient.recap

import eu.feg.ambient.ambient.engine.ledger.LedgerEntry
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.surfaces.ProtectionState
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The arithmetic of a recap, kept pure so it can be tested without a Ledger file, a Context
 * or a narrator — the same split DigestRanker makes for the digest.
 *
 * WHAT "FOLLOWED" MEANS. The ledger has no "followed" flag. What it does have is a row for
 * every moment the engine considered raising for this customer about a match — including the
 * ones it chose to stay silent on — and the engine only considers a match the customer has a
 * reason to care about: a leg on it, or a team they follow. So "matches followed" is the set
 * of matches the engine had something to say about, minus the rows it rejected as not theirs.
 * That is an approximation, and an honest one: it never counts a match the customer was not
 * actually watching, which is the direction a shareable card has to err in.
 *
 * NOTHING HERE IS MONEY. A "prediction" is a leg outcome — LEG_DECIDED landed, LEG_LOST did
 * not — and it is counted as an outcome, never valued. There is no field on [LedgerEntry] for
 * a stake or a return, so there is nothing here that could be summed even by accident.
 */
object RecapCounter {

    data class Counts(
        val matchesFollowed: Int,
        val teamsFollowed: List<String>,
        val topTeam: String?,
        val topTeamCount: Int,
        val predictionsRight: Int,
        val predictionsTotal: Int,
        val checkIns: Int,
        val longestStreak: Int,
    )

    /**
     * @param followedClub the club from UserState, by name. Counted as followed even when
     *   none of its matches appear in the window — following is a choice, not a statistic.
     * @param zone the device zone. Streaks are calendar days as the customer lived them, so
     *   a check-in at 23:50 and one at 00:10 are two days, which is what a streak means.
     */
    fun count(
        entries: List<LedgerEntry>,
        from: Instant,
        to: Instant,
        followedClub: String?,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): Counts {
        val fromMs = from.toEpochMilliseconds()
        val toMs = to.toEpochMilliseconds()
        val own = entries.filter { it.createdAt in fromMs..toMs && isCustomersOwn(it) }

        val byMatch = own
            .mapNotNull { entry -> matchKeyOf(entry)?.let { it to entry } }
            .groupBy({ it.first }, { it.second })

        // One tally per match, not per row. A match with five goals in it is still one
        // match with Sparta in it, and "Sparta, every single week" has to mean weeks.
        val teamTally = linkedMapOf<String, Int>()
        byMatch.values.forEach { rows ->
            val named = rows.firstOrNull { it.homeTeam != null && it.awayTeam != null }
            listOfNotNull(named?.homeTeam, named?.awayTeam).forEach { team ->
                teamTally[team] = (teamTally[team] ?: 0) + 1
            }
        }
        val top = teamTally.maxByOrNull { it.value }
        val teams = teamTally.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .let { ranked -> if (followedClub != null && followedClub !in ranked) ranked + followedClub else ranked }

        // Distinct by moment, because a spoken row copies its origin's moment id and type:
        // reading a decided leg out loud must not count it as a second prediction.
        val predictions = own
            .filter { it.momentType == LEG_DECIDED || it.momentType == LEG_LOST }
            .distinctBy { predictionKeyOf(it) }

        // A check-in is any surface the customer reached for — the widget, the lock screen,
        // the speaker button. Spoken rows carry tappedAt for exactly this reason.
        val checkInDays = own
            .mapNotNull { it.tappedAt }
            .map { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date.toEpochDays() }

        return Counts(
            matchesFollowed = byMatch.size,
            teamsFollowed = teams,
            topTeam = top?.key,
            topTeamCount = top?.value ?: 0,
            predictionsRight = predictions.count { it.momentType == LEG_DECIDED },
            predictionsTotal = predictions.size,
            checkIns = checkInDays.size,
            longestStreak = longestRun(checkInDays),
        )
    }

    /**
     * The same rule the digest applies, for the same reason. A row withheld under BLOCKED or
     * UNVERIFIED was a promise made at the time; a row the engine rejected as "Not yours" is
     * a match the customer has no leg on and no team in, and counting it as followed would
     * be a false statement about them on a card they are invited to share.
     */
    private fun isCustomersOwn(entry: LedgerEntry): Boolean =
        entry.protection != ProtectionState.BLOCKED.name &&
            entry.protection != ProtectionState.UNVERIFIED.name &&
            !entry.reason.startsWith(NOT_YOURS)

    /**
     * The match, recovered the way DigestRanker recovers it: the field first, then the
     * "matchId:TYPE@millis" moment id, then the team pair. A row that has none of these —
     * a spoken row, an old digest row — is not a match and is not counted as one.
     */
    private fun matchKeyOf(entry: LedgerEntry): String? {
        entry.matchId?.takeIf { it.isNotBlank() }?.let { return it }
        if (entry.momentId.isNotBlank() && entry.momentId != NO_MOMENT) {
            return entry.momentId.substringBefore(":")
        }
        val home = entry.homeTeam ?: return null
        val away = entry.awayTeam ?: return null
        return home + " v " + away
    }

    private fun predictionKeyOf(entry: LedgerEntry): String =
        if (entry.momentId.isNotBlank() && entry.momentId != NO_MOMENT) entry.momentId else entry.id

    /** Longest run of consecutive epoch days. Duplicates collapse; gaps break the run. */
    private fun longestRun(days: List<Int>): Int {
        val sorted = days.distinct().sorted()
        var best = 0
        var run = 0
        var previous: Int? = null
        for (day in sorted) {
            run = if (previous != null && day == previous + 1) run + 1 else 1
            if (run > best) best = run
            previous = day
        }
        return best
    }

    private val LEG_DECIDED = MomentType.LEG_DECIDED.name
    private val LEG_LOST = MomentType.LEG_LOST.name

    /** AmbientEngine's wording for an ownership rejection; matched on prefix, not equality. */
    private const val NOT_YOURS = "Not yours"

    /** What the engine writes as the moment id on a silence row. */
    private const val NO_MOMENT = "none"
}
