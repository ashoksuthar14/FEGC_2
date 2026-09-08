package eu.feg.ambient.ambient.recap

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class RecapPeriod { MONTH, SEASON }

/**
 * The "Wrapped" moment: what a customer did with the app over a period, as a gift rather
 * than a nag.
 *
 * THE HARD RULE. A recap counts matches, teams, predictions, check-ins and streaks. It NEVER
 * contains stake, winnings, losses, balance, or the number of bets placed — no money and no
 * wagering volume, in any field, under any name. That is what keeps it outside inducement
 * territory, keeps it safe for a customer who is cutting back (which is why it renders
 * unchanged in Calm Mode), and it is why it is shareable: nobody shares their P&L. A test
 * enforces the rule on this class's field names, so adding such a field fails the build.
 */
@Serializable
data class Recap(
    val period: RecapPeriod,
    val from: Instant,
    val to: Instant,
    val matchesFollowed: Int,
    val teamsFollowed: List<String>,
    /** The most-followed team, with how many of its matches were followed. */
    val topTeam: String?,
    val topTeamCount: Int,
    val predictionsRight: Int,
    val predictionsTotal: Int,
    /** Surfaces tapped — the widget, the lock-screen card, the app opened from a shortcut. */
    val checkIns: Int,
    /** Consecutive match days with at least one check-in. */
    val longestStreak: Int,
    val headline: String,
    val detail: String,
    val spokenText: String,
)

/** Null when the period is too thin to be worth a card — see the builder for the threshold. */
interface RecapBuilder {
    suspend fun build(period: RecapPeriod, now: Instant): Recap?
}
