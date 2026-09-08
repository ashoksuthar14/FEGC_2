package eu.feg.ambient.ambient.surfaces

import android.content.Context
import android.content.SharedPreferences
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours

/**
 * At most one interrupting alert per rolling 24 hours.
 *
 * The restraint is the product, so the budget is enforced here rather than left to whoever
 * calls postAlert. When it is spent the caller is told so and can say something useful; it
 * is never silently swallowed.
 *
 * SharedPreferences rather than DataStore: the app already persists UserState this way, and
 * one integer does not justify another dependency.
 */
class AlertBudget(
    private val prefs: SharedPreferences?,
    private val clock: () -> Instant = { Clock.System.now() },
    private val perDay: Int = 1,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences("ambient_alerts", Context.MODE_PRIVATE),
    )

    val budget: Int get() = perDay

    /** How many alerts were sent inside the last rolling 24 hours. */
    fun sentInLastDay(): Int {
        val stamps = timestamps()
        val cutoff = clock() - 24.hours
        return stamps.count { it > cutoff }
    }

    fun canSend(): Boolean = sentInLastDay() < perDay

    /** Records a send. Returns false when the budget was already spent. */
    fun tryConsume(): Boolean {
        if (!canSend()) return false
        val cutoff = clock() - 24.hours
        val kept = timestamps().filter { it > cutoff } + clock()
        prefs?.edit()?.putString(KEY, kept.joinToString(",") { it.toEpochMilliseconds().toString() })?.apply()
        return true
    }

    /** Demo affordance only, and labelled as such wherever it is exposed. */
    fun reset() {
        prefs?.edit()?.remove(KEY)?.apply()
    }

    private fun timestamps(): List<Instant> =
        prefs?.getString(KEY, null)
            ?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.map { Instant.fromEpochMilliseconds(it) }
            ?: emptyList()

    private companion object {
        const val KEY = "alert_timestamps"
    }
}
