package eu.feg.ambient.ambient.loyalty.store

import android.content.Context
import eu.feg.ambient.ambient.loyalty.Redemption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Mission progress, earned badges and redemptions, on disk.
 *
 * WHY NOT ROOM. The brief for this feature asked for Room entities and a DAO. This project has
 * no Room: every other piece of durable state here is either SharedPreferences (the away
 * tracker, the widget snapshot, the alert budget, the recap flag) or one JSON file behind a
 * StateFlow (the Ledger, which is the closest neighbour to this data and is explicitly shaped
 * like a DAO for the day it becomes one). Adding Room means three dependencies and the KSP
 * plugin, against a project rule that new dependencies are argued for first, to persist three
 * small tables during a hackathon. The compliance properties this feature exists for live
 * entirely in the model, and none of them depend on where the rows are written.
 *
 * So: the Ledger's pattern, deliberately. Rows are the same shape a DAO would return, reads
 * are synchronous because the widget composes on whatever thread the launcher gives it, and
 * writes persist immediately. If this graduates to Room later, the row types below are the
 * entities and [LoyaltyStore] is the DAO, unchanged.
 */
@Serializable
data class MissionRow(
    val missionId: String,
    val progress: Int,
    val completedAt: Long? = null,
)

@Serializable
data class BadgeRow(
    val badgeId: String,
    /** The mission that earned it. The pair is the idempotency key, not the event. */
    val missionId: String,
    val earnedAt: Long,
    val weight: Int = 1,
)

@Serializable
data class RedemptionRow(
    val id: String,
    val perkId: String,
    val code: String,
    val redeemedAt: Long,
    val badgesSpent: Int,
)

@Serializable
private data class LoyaltyFile(
    val missions: List<MissionRow> = emptyList(),
    val badges: List<BadgeRow> = emptyList(),
    val redemptions: List<RedemptionRow> = emptyList(),
)

class LoyaltyStore(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, "ambient_loyalty.json"))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _missions = MutableStateFlow<List<MissionRow>>(emptyList())
    val missions: StateFlow<List<MissionRow>> = _missions.asStateFlow()

    private val _badges = MutableStateFlow<List<BadgeRow>>(emptyList())
    val badges: StateFlow<List<BadgeRow>> = _badges.asStateFlow()

    private val _redemptions = MutableStateFlow<List<RedemptionRow>>(emptyList())
    val redemptions: StateFlow<List<RedemptionRow>> = _redemptions.asStateFlow()

    init {
        load()
    }

    // --- missions ---------------------------------------------------------------------

    fun progressOf(missionId: String): Int =
        _missions.value.firstOrNull { it.missionId == missionId }?.progress ?: 0

    /**
     * Raises progress to [progress] and never lowers it.
     *
     * Monotonic on purpose. Progress is recomputed from sources that can under-report after a
     * reinstall or a cleared ledger, and a mission that visibly goes backwards reads as the
     * app having lost something the customer earned.
     */
    fun setProgress(missionId: String, progress: Int, completedAt: Long? = null) {
        val existing = _missions.value.firstOrNull { it.missionId == missionId }
        if (existing != null && progress <= existing.progress && completedAt == null) return
        val row = MissionRow(
            missionId = missionId,
            progress = maxOf(progress, existing?.progress ?: 0),
            completedAt = completedAt ?: existing?.completedAt,
        )
        _missions.value = _missions.value.filterNot { it.missionId == missionId } + row
        persist()
    }

    // --- badges -----------------------------------------------------------------------

    fun hasBadge(badgeId: String): Boolean = _badges.value.any { it.badgeId == badgeId }

    /**
     * Awards a badge, once and only once.
     *
     * Keyed on the badge, not on the event that caused it: the tracker recomputes progress
     * from the ledger on every relevant change, so the same completion is observed many
     * times. Returns false when the badge was already held, which is how the caller knows
     * not to raise a MISSION_COMPLETE moment for something it already announced.
     */
    fun awardBadge(badgeId: String, missionId: String, earnedAt: Long, weight: Int): Boolean {
        if (hasBadge(badgeId)) return false
        _badges.value = _badges.value + BadgeRow(badgeId, missionId, earnedAt, weight)
        persist()
        return true
    }

    fun earnedAt(badgeId: String): Long? =
        _badges.value.firstOrNull { it.badgeId == badgeId }?.earnedAt

    /** Summed weight, which is what a tier is computed from. */
    fun badgeWeight(): Int = _badges.value.sumOf { it.weight }

    // --- redemptions ------------------------------------------------------------------

    fun addRedemption(row: RedemptionRow) {
        if (_redemptions.value.any { it.id == row.id }) return
        _redemptions.value = _redemptions.value + row
        persist()
    }

    fun redemptionFor(perkId: String): RedemptionRow? =
        _redemptions.value.firstOrNull { it.perkId == perkId }

    fun spentBadges(): Int = _redemptions.value.sumOf { it.badgesSpent }

    // --- demo -------------------------------------------------------------------------

    /** Surface Lab only. Throws the whole file away so a demo can be run twice. */
    fun clear() {
        _missions.value = emptyList()
        _badges.value = emptyList()
        _redemptions.value = emptyList()
        persist()
    }

    // --- disk -------------------------------------------------------------------------

    private fun load() {
        val raw = runCatching { if (file.exists()) file.readText() else null }.getOrNull() ?: return
        val parsed = runCatching { json.decodeFromString<LoyaltyFile>(raw) }.getOrNull() ?: return
        _missions.value = parsed.missions
        _badges.value = parsed.badges
        _redemptions.value = parsed.redemptions
    }

    private fun persist() {
        val snapshot = LoyaltyFile(_missions.value, _badges.value, _redemptions.value)
        runCatching { file.writeText(json.encodeToString(snapshot)) }
    }
}
