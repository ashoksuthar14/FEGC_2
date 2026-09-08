package eu.feg.ambient.ambient.recap

import android.content.Context
import android.content.SharedPreferences
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Whether the customer has already been shown the recap for a period.
 *
 * SharedPreferences, for the reasons AwayTracker gives: it is read from `provideGlance` in
 * whatever process the launcher wakes, a blocking read of a small file is right there, and
 * every other preference in this app already lives here.
 *
 * KEYED ON THE PERIOD, NOT A BOOLEAN. The seen-mark stores the calendar day of the period
 * end it was shown for, and "seen" means that day is inside the current window. A plain flag
 * would either never clear — one recap for the life of the install — or clear on the wrong
 * event and put the recap back on every redraw, which is the reactivation push we claim to
 * have replaced, wearing a different hat. Storing the day means a month recap seen today is
 * not offered again for thirty days and then is, with nothing having to remember to reset it.
 */
class RecapSeen(private val prefs: SharedPreferences?) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
    )

    fun hasSeen(
        period: RecapPeriod,
        to: Instant,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): Boolean {
        val seenDay = prefs?.getLong(key(period), NEVER) ?: NEVER
        if (seenDay == NEVER) return false
        val elapsed = dayOf(to, zone) - seenDay
        // A negative gap means the clock went backwards, which on a demo phone it does.
        // Treat that as seen rather than re-offer a recap the customer has already had.
        return elapsed < period.windowDays
    }

    /** Records that the recap ending at [to] has been offered — drawn, not necessarily read. */
    fun markSeen(
        period: RecapPeriod,
        to: Instant,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ) {
        prefs?.edit()?.putLong(key(period), dayOf(to, zone))?.apply()
    }

    /** Demo affordance: forget that a period's recap was shown, so the widget offers it again. */
    fun reset(period: RecapPeriod) {
        prefs?.edit()?.remove(key(period))?.apply()
    }

    private fun dayOf(at: Instant, zone: TimeZone): Long =
        at.toLocalDateTime(zone).date.toEpochDays().toLong()

    private fun key(period: RecapPeriod): String = KEY_SEEN_PREFIX + period.name

    companion object {
        private const val PREFS = "ambient_recap"
        private const val KEY_SEEN_PREFIX = "seen_"
        private const val NEVER = Long.MIN_VALUE
    }
}
