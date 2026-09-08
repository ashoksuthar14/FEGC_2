package eu.feg.ambient.ambient.digest

import eu.feg.ambient.ambient.engine.ledger.LedgerEntry
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.surfaces.ProtectionState
import kotlinx.datetime.Instant

/**
 * Picks the four things worth catching up on, out of everything the ledger recorded.
 *
 * Pure and Android-free on purpose: this is the part of the digest with actual judgement in
 * it, so it has to be testable on the JVM without a Ledger file, a Context or a narrator.
 *
 * SILENCE COUNTS. Rows where the surface was NOTHING are ranked alongside the ones we showed,
 * because a moment that mattered but was not worth interrupting for is exactly what a catch-up
 * is for. Dropping them would make the digest a replay of the notifications the customer has
 * already seen, which is the one thing it must not be.
 */
object DigestRanker {

    /** Beyond four it stops being a glance — the same cap [Digest] documents. */
    const val MAX_ITEMS = 4

    /**
     * @param since only rows created at or after this instant are considered.
     * @param matchOutcomesOnly CALM protection. Keeps the tiers that describe what happened on
     *   the pitch and drops every row that would have to be phrased as a slip or a pick. The
     *   answer is a shorter digest, not a refused one: a scoreline is not the unsafe part.
     */
    fun rank(
        entries: List<LedgerEntry>,
        since: Instant,
        matchOutcomesOnly: Boolean = false,
    ): List<DigestItem> {
        val cutoff = since.toEpochMilliseconds()

        return entries
            .asSequence()
            .filter { it.createdAt >= cutoff }
            .filter { isSafeToRecall(it) }
            .mapNotNull { entry ->
                val tier = tierOf(entry.momentType, matchOutcomesOnly) ?: return@mapNotNull null
                Ranked(entry, tier)
            }
            .groupBy { groupKeyOf(it.entry) }
            // Most significant wins the group; newest breaks a tie. Two goals in the same
            // match are one line in a catch-up, not two.
            .map { (_, group) -> group.minWith(BY_SIGNIFICANCE) }
            .sortedWith(BY_SIGNIFICANCE)
            .take(MAX_ITEMS)
            .map { ranked ->
                DigestItem(
                    text = textFor(ranked.entry, matchOutcomesOnly),
                    at = Instant.fromEpochMilliseconds(ranked.entry.createdAt),
                    momentId = ranked.entry.momentId,
                    important = ranked.tier <= TIER_LEG_DECIDED,
                )
            }
    }

    private data class Ranked(val entry: LedgerEntry, val tier: Int)

    /** Tier first, then recency. Used both to pick a winner per match and to order the card. */
    private val BY_SIGNIFICANCE =
        compareBy<Ranked>({ it.tier }, { -it.entry.createdAt })

    /**
     * A row withheld because the register said excluded, or because age was never verified,
     * does not come back later as a summary. Protection at the time of the decision is the
     * honest test here — the state may have changed since, but our promise was made then.
     */
    private fun isSafeToRecall(entry: LedgerEntry): Boolean =
        entry.protection != ProtectionState.BLOCKED.name &&
            entry.protection != ProtectionState.UNVERIFIED.name &&
            // The engine records every live score change as a GOAL_ON_SLIP *event* and then
            // rules on ownership; a row it declined as "Not yours" is a match the customer has
            // no stake in. Summarising it as "a goal on your slip" would tell them they hold
            // a bet they do not -- a false statement about their own account. Not theirs then
            // means not theirs now, so it never enters the catch-up at all.
            !entry.reason.startsWith(NOT_YOURS) &&
            // Rows written by the demo seeder exist to give the recap a month of history.
            // They are not moments the customer lived through, so they never become a
            // catch-up.
            !entry.reason.startsWith(DEMO_SEED)

    /**
     * The grouping key is the match, and the match is recovered from the ids we already write.
     *
     * DefaultMomentBuilder mints a moment id as "matchId:TYPE@millis", so everything before
     * the first colon is the match. Silence rows carry momentId "none" and instead put the
     * match at the tail of the entry id, "led-<millis>-<matchId>". Both are parsed here.
     *
     * This is an approximation and worth naming as one: LedgerEntry has no matchId field, and
     * adding one touches the engine, the persisted file format and everything that reads it.
     * The failure mode is benign in both directions — an unparseable id falls back to the
     * entry's own id, which is unique, so the worst case is that two rows for one match both
     * survive rather than that two unrelated matches are silently merged.
     */
    private fun groupKeyOf(entry: LedgerEntry): String {
        // The field, now that rows carry one. The id-parsing below stays for rows written
        // before it existed, which are still on devices.
        entry.matchId?.takeIf { it.isNotBlank() }?.let { return it }
        if (entry.momentId.isNotBlank() && entry.momentId != "none") {
            return entry.momentId.substringBefore(":")
        }
        // "led-" then a millis stamp then the match id, which may itself contain dashes.
        val tail = entry.id.removePrefix("led-").substringAfter("-", "")
        return tail.ifBlank { entry.id }
    }

    /**
     * Rank order, and the reason for it: what has already been decided outranks what is still
     * being decided, which outranks what has not started. A settled slip is the only item the
     * customer can do nothing about and everything else is context for it.
     *
     * "Followed-team results" is HALFTIME and MINUTES_REMAINING. The ledger records a moment
     * type but not its ownership, so a followed-team result cannot be told apart from any
     * other result after the fact; these two types are the ones the engine only ever raises
     * about a match the customer is watching, which makes them the closest honest stand-in.
     */
    private fun tierOf(momentType: String, matchOutcomesOnly: Boolean): Int? {
        val tier = when (momentType) {
            MomentType.SLIP_SETTLED.name -> TIER_SETTLED
            MomentType.LEG_DECIDED.name, MomentType.LEG_LOST.name -> TIER_LEG_DECIDED
            MomentType.GOAL_ON_SLIP.name -> TIER_GOAL
            MomentType.HALFTIME.name, MomentType.MINUTES_REMAINING.name -> TIER_FOLLOWED_RESULT
            MomentType.KICKOFF_FOLLOWED.name -> TIER_KICKOFF
            // Unknown types, spoken rows and AWAY_DIGEST itself. A digest that lists a
            // previous digest is a hall of mirrors.
            else -> return null
        }
        if (matchOutcomesOnly && tier < TIER_GOAL) return null
        return tier
    }

    /**
     * Deliberately vague. These fragments are joined with commas behind "While you were away",
     * and the ledger holds no team names or scores to make them specific with. Saying less is
     * the safe direction: the card is a reason to open the app, not a substitute for it.
     *
     * The CALM wording drops "your slip" and "your pick" along with the tiers that needed
     * them, so nothing in a calm digest reads as an account of what the customer has staked.
     */
    private fun textFor(entry: LedgerEntry, matchOutcomesOnly: Boolean): String {
        val match = matchLabel(entry)
        val outcome = when (entry.momentType) {
            MomentType.SLIP_SETTLED.name -> "your slip settled"
            MomentType.LEG_DECIDED.name -> "your pick landed"
            MomentType.LEG_LOST.name -> "your pick went"
            MomentType.GOAL_ON_SLIP.name ->
                if (matchOutcomesOnly) "a goal went in" else "a goal on your slip"
            MomentType.HALFTIME.name -> "half time"
            MomentType.MINUTES_REMAINING.name -> "into the last minutes"
            MomentType.KICKOFF_FOLLOWED.name -> "kick off"
            else -> "something happened"
        }
        // "Betis 2-1 - your pick landed" when the row knows its match, and the old vague
        // wording when it does not. Rows written before the ledger carried a match still have
        // to render, and they render as they always did rather than as a blank.
        return if (match == null) outcome else match + " \u2014 " + outcome
    }

    /** "Betis 2-1", or just the fixture when the score was never recorded. */
    private fun matchLabel(entry: LedgerEntry): String? {
        val home = entry.homeTeam ?: return null
        val away = entry.awayTeam ?: return null
        val homeScore = entry.homeScore
        val awayScore = entry.awayScore
        return if (homeScore != null && awayScore != null) {
            home + " " + homeScore + "\u2013" + awayScore + " " + away
        } else {
            home + " v " + away
        }
    }

    /** AmbientEngine's wording for an ownership rejection; matched on prefix, not equality. */
    private const val NOT_YOURS = "Not yours"
    private const val DEMO_SEED = "Demo seed"

    private const val TIER_SETTLED = 0
    private const val TIER_LEG_DECIDED = 1
    private const val TIER_GOAL = 2
    private const val TIER_FOLLOWED_RESULT = 3
    private const val TIER_KICKOFF = 4
}
