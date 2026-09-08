package eu.feg.ambient.ambient.loyalty

import eu.feg.ambient.ambient.loyalty.store.LoyaltyStore
import eu.feg.ambient.ambient.loyalty.store.RedemptionRow
import eu.feg.ambient.ambient.surfaces.ProtectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Missions with their progress filled in.
 *
 * The catalogue is the definition and the store is the state; neither knows about the other,
 * which is what lets a mission be re-worded in a fixture file without touching what anyone has
 * already earned.
 */
class MissionRepository(
    private val catalogue: LoyaltyCatalogue,
    private val store: LoyaltyStore,
) {
    val missions: StateFlow<List<Mission>>
        get() = _missions

    private lateinit var _missions: StateFlow<List<Mission>>

    fun start(scope: CoroutineScope) {
        _missions = store.missions
            .map { snapshot() }
            .stateIn(scope, SharingStarted.Eagerly, snapshot())
    }

    fun snapshot(): List<Mission> =
        catalogue.missions.map { it.toMission(store.progressOf(it.id)) }

    fun mission(id: String): Mission? = snapshot().firstOrNull { it.id == id }

    /** Every mission of a type, because two missions can watch the same event at once. */
    fun of(type: MissionType): List<Mission> = snapshot().filter { it.type == type }

    /**
     * Records progress toward a mission.
     *
     * Takes the absolute count rather than an increment. The tracker recomputes from the
     * ledger and the repositories on every relevant change, so it always knows the true
     * total; an increment API would double-count the moment anything observed an event twice.
     */
    fun record(missionId: String, progress: Int, at: Instant) {
        val entry = catalogue.mission(missionId) ?: return
        val complete = progress >= entry.target
        store.setProgress(
            missionId = missionId,
            progress = progress.coerceAtMost(entry.target),
            completedAt = if (complete) at.toEpochMilliseconds() else null,
        )
    }
}

/** Earned badges, and the one rule that matters: a badge is awarded exactly once. */
class BadgeRepository(
    private val catalogue: LoyaltyCatalogue,
    private val store: LoyaltyStore,
) {
    val badges: StateFlow<List<Badge>>
        get() = _badges

    private lateinit var _badges: StateFlow<List<Badge>>

    fun start(scope: CoroutineScope) {
        _badges = store.badges
            .map { snapshot() }
            .stateIn(scope, SharingStarted.Eagerly, snapshot())
    }

    fun snapshot(): List<Badge> = store.badges.value.mapNotNull { row ->
        val entry = catalogue.missions.firstOrNull { it.badgeId == row.badgeId } ?: return@mapNotNull null
        catalogue.badgeFor(entry).copy(
            earnedAt = Instant.fromEpochMilliseconds(row.earnedAt),
            weight = row.weight,
        )
    }

    /**
     * Awards the badge for a completed mission.
     *
     * Returns null when it was already held. That return value is load-bearing: it is what
     * stops a re-observed completion from raising a second MISSION_COMPLETE moment, which
     * would be the same badge announced twice.
     */
    fun award(missionId: String, at: Instant): Badge? {
        val entry = catalogue.mission(missionId) ?: return null
        val awarded = store.awardBadge(
            badgeId = entry.badgeId,
            missionId = missionId,
            earnedAt = at.toEpochMilliseconds(),
            weight = entry.weight,
        )
        if (!awarded) return null
        return catalogue.badgeFor(entry).copy(earnedAt = at)
    }

    fun holds(badgeId: String): Boolean = store.hasBadge(badgeId)

    /** Tier is by summed weight, never by row count. */
    fun weight(): Int = store.badgeWeight()

    fun tier(): LoyaltyTier = LoyaltyTier.forWeight(weight())
}

/**
 * One snapshot of everything the loyalty screens and the season widget draw, plus redemption.
 *
 * PROTECTION IS READ HERE, not in the screens. CALM pauses the mechanic: progress stops, but
 * what has already been earned stays visible, because taking someone's badges away for
 * slowing down would be the exact opposite of the message. UNVERIFIED and BLOCKED make the
 * whole thing inert -- nothing accrues and nothing can be redeemed.
 */
class LoyaltyRepository(
    private val catalogue: LoyaltyCatalogue,
    private val store: LoyaltyStore,
    private val missionRepository: MissionRepository,
    private val badgeRepository: BadgeRepository,
    private val protection: StateFlow<ProtectionState>,
    private val now: () -> Instant = { Clock.System.now() },
) {
    val perks: List<Perk> get() = catalogue.perks

    val state: StateFlow<LoyaltyState>
        get() = _state

    private lateinit var _state: StateFlow<LoyaltyState>

    fun start(scope: CoroutineScope) {
        _state = combine(
            store.missions,
            store.badges,
            store.redemptions,
            protection,
        ) { _, _, _, guard -> snapshot(guard) }
            .stateIn(scope, SharingStarted.Eagerly, snapshot(protection.value))
    }

    fun snapshot(guard: ProtectionState = protection.value) = LoyaltyState(
        missions = missionRepository.snapshot(),
        badges = badgeRepository.snapshot(),
        redemptions = store.redemptions.value.map {
            Redemption(it.id, it.perkId, it.code, Instant.fromEpochMilliseconds(it.redeemedAt), it.badgesSpent)
        },
        paused = guard != ProtectionState.NORMAL,
    )

    /** Whether the mechanic accrues at all right now. The tracker asks this, not the UI. */
    fun accrues(guard: ProtectionState): Boolean = guard == ProtectionState.NORMAL

    // --- redemption -------------------------------------------------------------------

    sealed interface RedeemResult {
        data class Done(val redemption: Redemption) : RedeemResult
        data class NotEnoughBadges(val short: Int) : RedeemResult
        data object TierTooLow : RedeemResult
        data object OutOfStock : RedeemResult
        data object AlreadyRedeemed : RedeemResult
        data object Unavailable : RedeemResult
    }

    /**
     * Redeems a perk. Deterministic from end to end.
     *
     * No draw, no reveal and no chance of a different outcome: the cost is stated, the perk is
     * the perk, and even the code is derived rather than rolled -- see [codeFor]. A redemption
     * a customer could not predict the result of is a loot box wearing a receipt.
     */
    fun redeem(perkId: String): RedeemResult {
        val guard = protection.value
        // Nothing accrues or converts under protection. A perk is not gambling credit, but a
        // blocked account should not be transacting with us at all.
        if (guard == ProtectionState.BLOCKED || guard == ProtectionState.UNVERIFIED) {
            return RedeemResult.Unavailable
        }
        val perk = catalogue.perk(perkId) ?: return RedeemResult.Unavailable
        if (store.redemptionFor(perkId) != null) return RedeemResult.AlreadyRedeemed
        if (!perk.inStock) return RedeemResult.OutOfStock

        val spendable = badgeRepository.weight() - store.spentBadges()
        if (badgeRepository.tier().ordinal < perk.tierRequired.ordinal) return RedeemResult.TierTooLow
        if (spendable < perk.badgeCost) return RedeemResult.NotEnoughBadges(perk.badgeCost - spendable)

        val at = now()
        val row = RedemptionRow(
            id = "red-" + perkId + "-" + at.toEpochMilliseconds(),
            perkId = perkId,
            code = codeFor(perkId, store.redemptions.value.size),
            redeemedAt = at.toEpochMilliseconds(),
            badgesSpent = perk.badgeCost,
        )
        store.addRedemption(row)
        return RedeemResult.Done(
            Redemption(row.id, row.perkId, row.code, at, row.badgesSpent),
        )
    }

    fun redemptionFor(perkId: String): Redemption? = store.redemptionFor(perkId)?.let {
        Redemption(it.id, it.perkId, it.code, Instant.fromEpochMilliseconds(it.redeemedAt), it.badgesSpent)
    }

    companion object {
        /**
         * A code derived from the perk and the sequence, not drawn.
         *
         * Deliberately boring: it has to be readable down a phone line and it must not look
         * like a prize. Nothing about it is secret, because it is a reference for our own
         * fulfilment rather than a bearer token.
         */
        fun codeFor(perkId: String, sequence: Int): String {
            val letters = perkId.filter { it.isLetterOrDigit() }.uppercase().take(4).padEnd(4, 'X')
            return "PSK-" + letters + "-" + (1000 + sequence)
        }
    }
}
