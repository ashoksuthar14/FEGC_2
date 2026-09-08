package eu.feg.ambient.ambient.loyalty

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Missions, badges and tiers — the loyalty mechanic with the money taken out of it.
 *
 * WHY THIS FILE IS WRITTEN THE WAY IT IS. Betano and Superbet ship this exact feature, and the
 * write-ups on it are blunt about where it lands: rewards are bonus funds, free spins and
 * mystery boxes, and players keep wagering to earn points while losing money. That is a harm
 * pattern regulators are actively looking at, and it is not a pattern you avoid by being
 * careful — every one of those products was built by people who were being careful.
 *
 * So the three rules are enforced HERE, in the types, and not in review comments:
 *
 *  1. No mission can require a wager. [MissionType] has no wagering case and [Mission] has no
 *     stake, amount or bet-count field, so "place five bets" cannot be written down.
 *  2. No reward can be money. [PerkCategory] has no gambling case, so a free bet has nowhere
 *     to go. Adding one means adding an enum entry, which is a decision somebody has to make
 *     in a diff rather than something that arrives by accident.
 *  3. No reward is randomised. [Perk] carries a stated [Perk.badgeCost] and a [Perk.stock] and
 *     nothing resembling a probability, a weight or a draw. There is no mystery box because
 *     there is no field that could describe one. Belgium treats loot boxes as gambling and
 *     several EU states are close behind.
 *
 * The same technique as MomentFacts having no money field: if a rule can be broken by writing
 * ordinary code, the model is wrong.
 */
@Serializable
enum class MissionType {
    /** Following a club. Costs nothing and is the first thing the product asks for anyway. */
    FOLLOW_TEAMS,

    /** Opening a live match. Watching is not staking. */
    CHECK_IN_LIVE,

    /** Placing or using a home-screen widget. */
    USE_WIDGET,

    /**
     * Scanning a retail slip already in the customer's hand.
     *
     * NOT a wagering mission, and the distinction is the whole argument: the slip exists
     * before the mission is aware of it, so nothing here can be progressed by betting more.
     * A customer who never scans one simply never completes it.
     */
    SCAN_SHOP_SLIP,

    /** A free score prediction. No stake attaches to it. */
    PREDICT_RESULT,

    /**
     * Setting a deposit limit.
     *
     * The one that makes this feature worth building. Every competitor rewards you for
     * spending; nobody rewards you for protecting yourself.
     */
    SET_A_LIMIT,

    /** Consecutive days with any check-in. A streak of attention, never of stakes. */
    KEEP_STREAK,

    // THERE IS DELIBERATELY NO PLACE_BETS OR WAGER_AMOUNT CASE.
    //
    // A mission that progresses when the customer wagers is the harm pattern this whole
    // design exists to avoid: it pays people to keep betting while they are losing. Do not
    // add one. If a future requirement seems to need it, the requirement is the thing to
    // push back on, and the absence of this case is what forces that conversation to happen.
}

/**
 * One mission.
 *
 * There is no stake, no amount and no bet count, and there is no field that could hold one.
 * [target] and [progress] are counts of the events named by [type], all of which are things a
 * customer does rather than things a customer pays for.
 */
@Serializable
data class Mission(
    val id: String,
    val title: String,
    val description: String,
    val type: MissionType,
    val target: Int,
    val progress: Int = 0,
    /** Null means it does not expire. Most of them do not, on purpose — see [isUrgent]. */
    val expiresAt: Instant? = null,
    val badgeId: String,
) {
    val isComplete: Boolean get() = progress >= target

    /**
     * Always false today, and kept as a single named place rather than scattered checks.
     *
     * Countdown timers are a pressure mechanic. If an expiring mission is ever introduced, the
     * copy for it still has to clear NarratorGuard, which rejects "expires soon", "last
     * chance" and "hurry" — so an expiry can exist as a fact without being sold as urgency.
     */
    val isUrgent: Boolean get() = false
}

/**
 * A badge, and what it is worth toward a tier.
 *
 * [weight] exists so a harder mission can count for more than an easy one without needing a
 * second currency. Tier is computed from summed weight, never from the number of rows.
 */
@Serializable
data class Badge(
    val id: String,
    val name: String,
    /** Resolved from a stable key in the catalogue; the JSON never carries a resource id. */
    val iconRes: Int,
    val earnedAt: Instant? = null,
    val weight: Int = 1,
)

/**
 * Tiers, by summed badge weight.
 *
 * A tier unlocks perks and nothing else. It carries no odds, no limits, no priority in any
 * queue, and no effect whatever on what the customer can stake — a "VIP tier" that changes
 * the betting experience is the mechanic this design is explicitly not.
 */
enum class LoyaltyTier(val badgesRequired: Int) {
    BRONZE(0),
    SILVER(3),
    GOLD(8),
    PLATINUM(15),
    ;

    companion object {
        /** The highest tier the weight reaches. */
        fun forWeight(weight: Int): LoyaltyTier =
            entries.last { weight >= it.badgesRequired }

        /** The next tier up, or null at the top. */
        fun after(tier: LoyaltyTier): LoyaltyTier? =
            entries.getOrNull(entries.indexOf(tier) + 1)
    }
}

/**
 * What a perk can be.
 *
 * THERE IS NO FREE_BET, BONUS, CASHBACK OR ODDS_BOOST, and the omission is the feature. A
 * reward that is gambling credit can only be spent by gambling, which makes the loyalty
 * programme a machine for converting loyalty into stakes — and makes it unavailable to
 * exactly the customers who most need to be able to take a break.
 *
 * Everything here can be redeemed by someone who has self-excluded. That is the test.
 */
@Serializable
enum class PerkCategory {
    MATCH_TICKET,
    MERCHANDISE,
    EXPERIENCE,
    FEATURE_ACCESS,
    PARTNER_VOUCHER,
    CHARITY_DONATION,
}

/**
 * One reward, at a stated price, with a stated outcome.
 *
 * Deterministic by construction: [badgeCost] is what it costs and the perk is what you get.
 * There is no probability, no rarity, no draw and no reveal, because there is no field that
 * could carry one.
 */
@Serializable
data class Perk(
    val id: String,
    val name: String,
    val description: String,
    val category: PerkCategory,
    val badgeCost: Int,
    val tierRequired: LoyaltyTier,
    val termsUrl: String,
    /** Plain language, shown before redemption. Never a link the customer has to chase. */
    val terms: String,
    val stock: Int,
) {
    val inStock: Boolean get() = stock > 0
}

/** A perk the customer has taken, and the code they got for it. */
@Serializable
data class Redemption(
    val id: String,
    val perkId: String,
    val code: String,
    val redeemedAt: Instant,
    val badgesSpent: Int,
)

/** Everything the loyalty screens draw, in one snapshot. */
data class LoyaltyState(
    val missions: List<Mission> = emptyList(),
    val badges: List<Badge> = emptyList(),
    val redemptions: List<Redemption> = emptyList(),
    val paused: Boolean = false,
) {
    /** Tier is by summed weight, never by row count. */
    val badgeWeight: Int get() = badges.sumOf { it.weight }

    val tier: LoyaltyTier get() = LoyaltyTier.forWeight(badgeWeight)

    val nextTier: LoyaltyTier? get() = LoyaltyTier.after(tier)

    /** Badges still to earn for the next tier, or null at the top. */
    val toNextTier: Int?
        get() = nextTier?.let { (it.badgesRequired - badgeWeight).coerceAtLeast(0) }

    /** Weight earned but not yet spent on a perk. */
    val spendableBadges: Int
        get() = badgeWeight - redemptions.sumOf { it.badgesSpent }

    val active: List<Mission> get() = missions.filterNot { it.isComplete }

    val done: List<Mission> get() = missions.filter { it.isComplete }

    /** The nearest mission to completion, for the widget's one line. */
    val nearest: Mission?
        get() = active.maxByOrNull { it.progress.toDouble() / it.target.coerceAtLeast(1) }
}
