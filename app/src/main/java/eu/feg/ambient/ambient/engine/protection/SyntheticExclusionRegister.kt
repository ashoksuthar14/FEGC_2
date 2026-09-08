package eu.feg.ambient.ambient.engine.protection

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration.Companion.hours

/**
 * A stand-in for the national register, backed by a JSON file.
 *
 * WHY A FILE COPY AND NOT THE ASSET: assets are read-only, and the demo has to be able to put
 * a player on the register while the audience watches and then see the app go quiet. The asset
 * is the seed; the working copy lives in filesDir and is the thing [add] and [remove] edit.
 *
 * WHY IT CAN FAIL: a register that always answers teaches the wrong lesson. Any unreadable or
 * unparseable file returns [RegisterResult.Failed] rather than an empty register, because an
 * empty register would read as "nobody is excluded" — the single most dangerous wrong answer
 * this class could give.
 */
class SyntheticExclusionRegister(
    private val file: File,
    private val seed: () -> String,
    private val clock: () -> Instant = { Clock.System.now() },
) : ExclusionRegister {

    constructor(context: Context) : this(
        File(context.filesDir, WORKING_COPY),
        { context.assets.open(ASSET_PATH).bufferedReader().use { reader -> reader.readText() } },
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    override suspend fun check(playerRef: String): RegisterResult = withContext(Dispatchers.IO) {
        val now = clock()
        val entries = readEntries() ?: return@withContext RegisterResult.Failed(
            reason = "Register unavailable",
            checkedAt = now,
        )

        val entry = entries.firstOrNull { it.playerRef.equals(playerRef, ignoreCase = true) }
            ?: return@withContext RegisterResult.Checked(
                excluded = false,
                validUntil = now + ANSWER_VALIDITY,
                reason = "No entry on the register",
                checkedAt = now,
            )

        // A lapsed exclusion is not an exclusion. Reporting it as one would keep a player out
        // past the term they agreed to, which is its own compliance failure.
        val lapsed = entry.excluded && entry.validUntil <= now
        if (lapsed) {
            return@withContext RegisterResult.Checked(
                excluded = false,
                validUntil = now + ANSWER_VALIDITY,
                reason = "Exclusion lapsed " + entry.validUntil,
                checkedAt = now,
            )
        }

        RegisterResult.Checked(
            excluded = entry.excluded,
            validUntil = if (entry.excluded) entry.validUntil else now + ANSWER_VALIDITY,
            reason = entry.reason,
            checkedAt = now,
        )
    }

    override suspend fun submit(request: RegisterEntryRequest): Boolean =
        add(request.playerRef, request.until, request.reason)

    // --- demo affordances --------------------------------------------------------------

    /** Puts a player on the register, or extends one already there. Returns false on I/O failure. */
    suspend fun add(
        playerRef: String,
        until: Instant,
        reason: String = "Voluntary self-exclusion",
    ): Boolean = withContext(Dispatchers.IO) {
        val entries = readEntries() ?: return@withContext false
        val next = entries.filterNot { it.playerRef.equals(playerRef, ignoreCase = true) } +
            RegisterEntry(playerRef, excluded = true, validUntil = until, reason = reason)
        write(next)
    }

    /**
     * Takes a player off the register entirely.
     *
     * Demo-only, and deliberately not reachable from the app: no real register lets the
     * operator being policed remove an entry.
     */
    suspend fun remove(playerRef: String): Boolean = withContext(Dispatchers.IO) {
        val entries = readEntries() ?: return@withContext false
        write(entries.filterNot { it.playerRef.equals(playerRef, ignoreCase = true) })
    }

    suspend fun entries(): List<RegisterEntry> = withContext(Dispatchers.IO) {
        readEntries().orEmpty()
    }

    /** Throws the working copy away so the next check re-seeds from the asset. */
    suspend fun reset(): Boolean = withContext(Dispatchers.IO) {
        runCatching { !file.exists() || file.delete() }.getOrDefault(false)
    }

    // --- storage -----------------------------------------------------------------------

    private fun readEntries(): List<RegisterEntry>? {
        val raw = runCatching {
            if (!file.exists()) seed().also { file.writeText(it) } else file.readText()
        }.getOrElse {
            Log.w(TAG, "could not read register", it)
            return null
        }
        return runCatching { json.decodeFromString<RegisterFile>(raw).entries }.getOrElse {
            Log.w(TAG, "could not parse register", it)
            null
        }
    }

    private fun write(entries: List<RegisterEntry>): Boolean = runCatching {
        file.writeText(json.encodeToString(RegisterFile(entries)))
        true
    }.getOrElse {
        Log.w(TAG, "could not write register", it)
        false
    }

    private companion object {
        const val TAG = "SyntheticRegister"
        const val ASSET_PATH = "mock/register.json"
        const val WORKING_COPY = "ambient_register.json"

        /**
         * How long a "not excluded" answer stands. Shorter than the evaluator's cache window
         * would make the cache pointless; much longer would let a stale clean answer outlive
         * an exclusion filed elsewhere.
         */
        val ANSWER_VALIDITY = 24.hours
    }
}

@Serializable
data class RegisterEntry(
    val playerRef: String,
    val excluded: Boolean,
    val validUntil: Instant,
    val reason: String,
)

@Serializable
private data class RegisterFile(
    val entries: List<RegisterEntry> = emptyList(),
)
