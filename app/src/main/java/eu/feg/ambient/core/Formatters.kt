package eu.feg.ambient.core

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

private fun twoDecimals(value: Double): String {
    val cents = (value * 100).roundToInt()
    val whole = cents / 100
    val frac = (cents % 100).toString().padStart(2, '0')
    return whole.toString() + "." + frac
}

/** Two decimals, always — odds never render as "1.5". */
fun formatOdds(value: Double): String = twoDecimals(value)

fun formatMoney(value: Double): String = "€" + twoDecimals(value)

/** "today 20:45" / "tomorrow 00:30", matching the chips in the screenshots. */
fun formatKickoff(
    kickoff: Instant,
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val k = kickoff.toLocalDateTime(zone)
    val n = now.toLocalDateTime(zone)
    val time = k.hour.toString().padStart(2, '0') + ":" + k.minute.toString().padStart(2, '0')
    return when (k.date.toEpochDays() - n.date.toEpochDays()) {
        0 -> "today " + time
        1 -> "tomorrow " + time
        else -> k.date.dayOfMonth.toString() + "." + k.date.monthNumber + ". " + time
    }
}

/** "1. poluvrijeme - 44m", the live chip text seen in the screenshots. */
fun formatLiveMinute(period: String?, minute: Int?): String = when {
    period == "Pauza" -> "Pauza"
    period != null && minute != null -> period + " - " + minute + "m"
    minute != null -> minute.toString() + "m"
    else -> "LIVE"
}
