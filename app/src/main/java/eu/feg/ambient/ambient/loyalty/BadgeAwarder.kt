package eu.feg.ambient.ambient.loyalty

import eu.feg.ambient.ambient.engine.LoyaltyFacts
import eu.feg.ambient.ambient.engine.MatchEvent
import eu.feg.ambient.ambient.narrator.MomentType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * What happens when a mission completes: the badge is written down once, and the engine is
 * told about it as an event like any other.
 *
 * THIS CLASS NEVER TOUCHES A SURFACE. No SurfaceController, no NotificationManager, no widget
 * refresh. It hands a [MatchEvent] to [raise] and stops; whether that becomes a widget line,
 * an alert or nothing at all is the engine's decision, scored and budgeted against the same
 * one-alert-a-day as a goal on the customer's slip. Missions that could post their own
 * notification would be a second push channel wearing a loyalty badge, and the whole reason
 * MISSION_COMPLETE is a MomentType is so that cannot happen.
 *
 * WHY IT STILL GOES IN AS A MATCHEVENT. AmbientEngine.onEvent is the only door, and going
 * around it would be exactly the second channel this design forbids -- so the event shape
 * was widened rather than bypassed. MatchEvent carries an optional [LoyaltyFacts], and
 * DefaultMomentBuilder short-circuits its bet-and-club join when it is present: a badge is
 * the customer's by construction, so ownership is Ownership.MINE rather than the NEITHER the
 * join would return for a fixture that does not exist. The football fields below are left
 * empty on purpose and nothing downstream reads them for these two types.
 *
 * Ownership.MINE bases at 0.25 in DefaultRelevanceScorer -- under a followed club's match and
 * well under the customer's own slip. That number is the "missions never nag" promise written
 * as arithmetic: at that base a badge reaches the widget and only clears the alert bar on a
 * quiet day with the budget unspent.
 *
 * Idempotency lives in [BadgeRepository.award], not here: it returns null when the badge is
 * already held, and null is what stops a re-observed completion from being announced twice.
 * This class deliberately keeps no memory of its own, because a second source of truth about
 * "did we already say this" is how the two drift apart.
 */
class BadgeAwarder(
    private val badgeRepository: BadgeRepository,
    /** The engine's door, passed as a function so this package never depends on the engine class. */
    private val raise: suspend (MatchEvent) -> Unit,
    private val now: () -> Instant = { Clock.System.now() },
) {

    /**
     * Awards the badge for [mission] if it is not already held, and raises the moments.
     *
     * Returns the badge on a first award and null on every later call for the same mission.
     * The tier is read before and after the award, on the same store, so a completion that
     * tips the customer over a threshold raises TIER_REACHED in the same breath rather than
     * on the next unrelated recompute.
     */
    suspend fun onCompleted(mission: Mission): Badge? {
        val tierBefore = badgeRepository.tier()
        val at = now()
        val badge = badgeRepository.award(mission.id, at) ?: return null

        val weight = badgeRepository.weight()
        val tierAfter = LoyaltyTier.forWeight(weight)

        raise(
            event(
                subject = MISSION_PREFIX + mission.id,
                type = MomentType.MISSION_COMPLETE,
                at = at,
                facts = LoyaltyFacts(
                    missionTitle = mission.title,
                    badgeName = badge.name,
                    badgeCount = weight,
                    tierName = tierName(tierAfter),
                    badgesToNextTier = toNextTier(tierAfter, weight),
                ),
            ),
        )
        if (tierAfter != tierBefore) {
            raise(
                event(
                    subject = TIER_PREFIX + tierAfter.name.lowercase(),
                    type = MomentType.TIER_REACHED,
                    at = at,
                    facts = LoyaltyFacts(
                        badgeCount = weight,
                        tierName = tierName(tierAfter),
                        badgesToNextTier = toNextTier(tierAfter, weight),
                    ),
                ),
            )
        }
        return badge
    }

    /**
     * A loyalty event in a football envelope.
     *
     * [subject] stands in for the match id, which the scorer and the budget key on. Prefixed
     * so it can never collide with a fixture id, and so the ledger's compliance view can tell
     * a badge row from a goal at a glance. Teams and scores are blank because there are none;
     * DefaultMomentBuilder builds the facts from [LoyaltyFacts] and never reads them.
     */
    private fun event(
        subject: String,
        type: MomentType,
        at: Instant,
        facts: LoyaltyFacts,
    ) = MatchEvent(
        matchId = subject,
        type = type,
        minute = 0,
        homeTeam = "",
        awayTeam = "",
        homeScore = 0,
        awayScore = 0,
        loyalty = facts,
        at = at,
    )

    /** "Silver", never "SILVER": the narrator prints this as a word, not a constant. */
    private fun tierName(tier: LoyaltyTier): String =
        tier.name.lowercase().replaceFirstChar { it.uppercase() }

    /** Null at the top, so the narrator has nothing to promise past Platinum. */
    private fun toNextTier(tier: LoyaltyTier, weight: Int): Int? =
        LoyaltyTier.after(tier)?.let { (it.badgesRequired - weight).coerceAtLeast(0) }

    companion object {
        const val MISSION_PREFIX = "mission:"
        const val TIER_PREFIX = "tier:"
    }
}
