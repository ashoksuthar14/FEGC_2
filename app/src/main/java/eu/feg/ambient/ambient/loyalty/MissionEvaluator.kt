package eu.feg.ambient.ambient.loyalty

import eu.feg.ambient.ambient.engine.ledger.LedgerEntry
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.data.model.BetSource
import eu.feg.ambient.data.model.Limits
import eu.feg.ambient.data.model.PlacedBet
import eu.feg.ambient.data.model.UserState
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * What the app currently knows about the customer, reduced to the seven counts a mission can
 * be about.
 *
 * Every field is a count of something the customer DID, never something they paid. That is
 * not a convention; it is the shape of [MissionType], which has no wagering case for this
 * class to feed. If a field named "stake" or "betsPlaced" ever appears here, the mission
 * model has already been broken somewhere else and this is where it would show.
 */
data class MissionSignals(
    val followedClubs: Int = 0,
    val liveCheckIns: Int = 0,
    val widgetPlaced: Boolean = false,
    val retailSlipsScanned: Int = 0,
    val predictionsMade: Int = 0,
    val limitSet: Boolean = false,
    val streakDays: Int = 0,
)

/**
 * The judgement behind mission progress, with nothing else attached to it.
 *
 * Pure, Android-free and synchronous, so the rules can be pinned by a JVM test without a
 * device, a store or a coroutine scope. The tracker's only job is to feed this the current
 * state and write the answer down; anything that looks like a decision belongs in here.
 *
 * Progress is ABSOLUTE. The tracker recomputes from the sources on every change and the
 * store is monotonic, so the same event observed ten times still yields the same number.
 * An incrementing design would count it ten times.
 */
class MissionEvaluator {

    /** The absolute progress of every mission type, from one snapshot of the signals. */
    fun progress(signals: MissionSignals): Map<MissionType, Int> =
        MissionType.entries.associateWith { progressOf(it, signals) }

    fun progressOf(type: MissionType, signals: MissionSignals): Int = when (type) {
        MissionType.FOLLOW_TEAMS -> signals.followedClubs
        MissionType.CHECK_IN_LIVE -> signals.liveCheckIns
        MissionType.USE_WIDGET -> if (signals.widgetPlaced) 1 else 0
        MissionType.SCAN_SHOP_SLIP -> signals.retailSlipsScanned
        MissionType.PREDICT_RESULT -> signals.predictionsMade
        MissionType.SET_A_LIMIT -> if (signals.limitSet) 1 else 0
        MissionType.KEEP_STREAK -> signals.streakDays
    }

    /**
     * Reduces the raw app state to [MissionSignals].
     *
     * Kept here rather than in the tracker for the same reason [progressOf] is: which ledger
     * rows count as a check-in is a rule, and rules that live inside a flow collector cannot
     * be tested without standing the whole pipeline up.
     */
    fun signalsFrom(
        user: UserState,
        bets: List<PlacedBet>,
        entries: List<LedgerEntry>,
        widgetPlaced: Boolean,
        timeZone: TimeZone,
    ): MissionSignals = MissionSignals(
        followedClubs = followedClubs(user),
        liveCheckIns = liveCheckIns(entries),
        widgetPlaced = widgetPlaced,
        retailSlipsScanned = retailSlipsScanned(bets),
        predictionsMade = 0,
        limitSet = limitSet(user.limits),
        streakDays = distinctDays(entries, timeZone),
    )

    /**
     * How many clubs the customer follows.
     *
     * The themed club counts even if it is somehow missing from the set, because choosing a
     * club to wear is following it by any reading a customer would give the word, and a
     * mission that disagreed with the home screen would look broken rather than strict.
     * setMyClub keeps the two in step; this is the belt to that pair of braces.
     */
    fun followedClubs(user: UserState): Int =
        (user.followedClubIds + listOfNotNull(user.myClubId?.takeIf { it.isNotBlank() })).size

    /**
     * Distinct matches for which a live moment actually reached a surface.
     *
     * The ledger records decisions, not screen opens: there is no "customer opened the match
     * page" row anywhere in this app. So a check-in is read as "we showed this customer
     * something about this match while it was in play" -- a row with [LedgerEntry.shownAt]
     * set and a type that only occurs during play. Kick-off warnings, digests and mission
     * moments are excluded because none of them means a match was being watched.
     */
    fun liveCheckIns(entries: List<LedgerEntry>): Int = entries
        .filter { it.shownAt != null && it.matchId != null && it.momentType in LIVE_TYPES }
        .distinctBy { it.matchId }
        .size

    /** Slips that came in on paper. [BetSource.RETAIL] is set only by the scanner path. */
    fun retailSlipsScanned(bets: List<PlacedBet>): Int =
        bets.count { it.source == BetSource.RETAIL }

    /**
     * Whether the customer has set a limit of their own.
     *
     * Compared on the CAPS, not the whole object: [Limits] also carries the used amounts,
     * which move whenever the customer deposits or plays, and a limit that "sets itself"
     * because the usage counter ticked would reward the wrong thing entirely.
     */
    fun limitSet(limits: Limits): Boolean {
        val defaults = Limits()
        return limits.depositLimit != defaults.depositLimit ||
            limits.lossLimit != defaults.lossLimit ||
            limits.timeLimit != defaults.timeLimit
    }

    /**
     * Distinct local days with any ledger row.
     *
     * Distinct, not consecutive: the shipped mission copy says "three separate days. No
     * hurry, and nothing expires", and a streak that resets when someone takes a day off
     * is the pressure mechanic the copy is promising not to be. Days are in the customer's
     * zone, because "yesterday" to them is what a streak is about.
     */
    fun distinctDays(entries: List<LedgerEntry>, timeZone: TimeZone): Int = entries
        .map { Instant.fromEpochMilliseconds(it.createdAt).toLocalDateTime(timeZone).date }
        .distinct()
        .size

    private companion object {
        /** The types that only ever fire while a match is being played. */
        val LIVE_TYPES = setOf(
            MomentType.GOAL_ON_SLIP.name,
            MomentType.LEG_DECIDED.name,
            MomentType.LEG_LOST.name,
            MomentType.SLIP_SETTLED.name,
            MomentType.HALFTIME.name,
            MomentType.MINUTES_REMAINING.name,
        )
    }
}
