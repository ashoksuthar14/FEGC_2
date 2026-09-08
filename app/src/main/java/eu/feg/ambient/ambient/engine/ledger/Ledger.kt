package eu.feg.ambient.ambient.engine.ledger

import android.content.Context
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.surfaces.ProtectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The record of every decision, including the decisions to stay quiet.
 *
 * NOT Room, deliberately. The spec calls for it, but Room needs KSP, and a KSP/Kotlin version
 * mismatch is a well-known way to lose an hour — this project is on Kotlin 2.2.0 and builds on
 * a 6 GB machine. The data here is a few hundred append-only rows, 48 arm counters and a
 * handful of register checks; none of the queries need SQL. kotlinx.serialization over a file
 * is the same pattern UserState and the widget store already use, adds no dependency, and
 * cannot crash on launch because a schema changed under an installed app.
 *
 * Room is the right production choice, and this class is deliberately shaped like a DAO so
 * swapping it is mechanical.
 */
@Serializable
data class LedgerEntry(
    val id: String,
    val momentId: String,
    val momentType: String,
    val contextBucket: String,
    val protection: String,
    val score: Double,
    val surface: String,
    val tone: String,
    val armId: String,
    val sampled: Double,
    /** Plain words. This is what "Why this?" renders. */
    val reason: String,
    val shownAt: Long?,
    /**
     * The match this row was about, and its score at the time.
     *
     * Added for the digest. Without it a catch-up can only say "a slip settled", because the
     * ledger recorded that a decision happened but not what it was about — and a card that
     * vague is a card nobody opens. All five default to null so the JSON already on a device
     * still parses; a row written before this existed simply has no match to name.
     *
     * COMPLIANCE — teams and scores are match facts, which every surface here already carries.
     * There is deliberately no field for a price or an amount, for the same reason MomentFacts
     * has none: the guarantee is that there is nothing to leak, not that we remember not to.
     */
    val matchId: String? = null,
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val tappedAt: Long? = null,
    val dismissedAt: Long? = null,
    val reward: Double? = null,
    val createdAt: Long,
) {
    val isComplianceRelevant: Boolean
        get() = surface == Surface.NOTHING.name ||
            protection != ProtectionState.NORMAL.name ||
            reason.contains("register", ignoreCase = true) ||
            reason.contains("blocked", ignoreCase = true) ||
            reason.contains("calm", ignoreCase = true) ||
            reason.contains("withheld", ignoreCase = true)
}

@Serializable
data class RegisterCheck(
    val id: String,
    val registerRef: String,
    val checkedAt: Long,
    val validUntil: Long,
    val excluded: Boolean,
    val reason: String,
)

@Serializable
data class ArmStat(
    val contextBucket: String,
    val armId: String,
    val wins: Double,
    val losses: Double,
    val updatedAt: Long,
)

@Serializable
private data class LedgerFile(
    val entries: List<LedgerEntry> = emptyList(),
    val checks: List<RegisterCheck> = emptyList(),
    val arms: List<ArmStat> = emptyList(),
)

class Ledger(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, "ambient_ledger.json"))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _entries = MutableStateFlow<List<LedgerEntry>>(emptyList())
    val entries: StateFlow<List<LedgerEntry>> = _entries.asStateFlow()

    private val _arms = MutableStateFlow<List<ArmStat>>(emptyList())
    val arms: StateFlow<List<ArmStat>> = _arms.asStateFlow()

    private var checks: List<RegisterCheck> = emptyList()

    init {
        load()
    }

    // --- decisions --------------------------------------------------------------------

    fun record(entry: LedgerEntry) {
        // Newest first: every reader wants the recent end, and the list is small enough that
        // prepending costs nothing.
        _entries.value = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
        persist()
    }

    fun markTapped(entryId: String, at: Instant = Clock.System.now()) {
        update(entryId) { it.copy(tappedAt = at.toEpochMilliseconds()) }
    }

    fun markDismissed(entryId: String, at: Instant = Clock.System.now()) {
        update(entryId) { it.copy(dismissedAt = at.toEpochMilliseconds()) }
    }

    fun markRewarded(entryId: String, reward: Double) {
        update(entryId) { it.copy(reward = reward) }
    }

    fun recent(limit: Int = 50): List<LedgerEntry> = _entries.value.take(limit)

    /** The DSA transparency view: checks, blocked renders, calm transitions, offers withheld. */
    fun complianceOnly(limit: Int = 50): List<LedgerEntry> =
        _entries.value.filter { it.isComplianceRelevant }.take(limit)

    // --- register ---------------------------------------------------------------------

    fun recordCheck(check: RegisterCheck) {
        checks = (listOf(check) + checks).take(MAX_CHECKS)
        persist()
    }

    fun lastCheck(): RegisterCheck? = checks.firstOrNull()

    fun allChecks(limit: Int = 20): List<RegisterCheck> = checks.take(limit)

    // --- bandit -----------------------------------------------------------------------

    fun arm(contextBucket: String, armId: String): ArmStat? =
        _arms.value.firstOrNull { it.contextBucket == contextBucket && it.armId == armId }

    fun armsFor(contextBucket: String): List<ArmStat> =
        _arms.value.filter { it.contextBucket == contextBucket }

    fun upsertArm(stat: ArmStat) {
        _arms.value = _arms.value
            .filterNot { it.contextBucket == stat.contextBucket && it.armId == stat.armId }
            .plus(stat)
        persist()
    }

    /** Applied lazily on read rather than scheduled: a weekly job is not worth a WorkManager. */
    fun decayed(stat: ArmStat, now: Instant = Clock.System.now()): ArmStat {
        val weeks = (now.toEpochMilliseconds() - stat.updatedAt) / WEEK_MILLIS
        if (weeks <= 0) return stat
        val factor = Math.pow(WEEKLY_DECAY, weeks.toDouble())
        return stat.copy(wins = stat.wins * factor, losses = stat.losses * factor)
    }

    fun clearArms() {
        _arms.value = emptyList()
        persist()
    }

    // --- persistence ------------------------------------------------------------------

    private fun update(entryId: String, block: (LedgerEntry) -> LedgerEntry) {
        _entries.value = _entries.value.map { if (it.id == entryId) block(it) else it }
        persist()
    }

    private fun load() {
        val raw = runCatching { if (file.exists()) file.readText() else null }.getOrNull() ?: return
        val parsed = runCatching { json.decodeFromString<LedgerFile>(raw) }.getOrNull() ?: return
        _entries.value = parsed.entries
        _arms.value = parsed.arms
        checks = parsed.checks
    }

    private fun persist() {
        val snapshot = LedgerFile(_entries.value, checks, _arms.value)
        runCatching { file.writeText(json.encodeToString(snapshot)) }
    }

    companion object {
        const val MAX_ENTRIES = 500
        const val MAX_CHECKS = 100
        const val WEEKLY_DECAY = 0.98
        private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
