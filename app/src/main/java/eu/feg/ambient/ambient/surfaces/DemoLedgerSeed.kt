package eu.feg.ambient.ambient.surfaces

import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.engine.ledger.LedgerEntry
import eu.feg.ambient.ambient.narrator.MomentType
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * A month of plausible history, for the recap demo.
 *
 * The recap returns null under three followed matches, and a fresh install has none. The
 * brief is explicit that seeding happens before the demo, not during it. Every row carries
 * reason "Demo seed", which the digest ranker refuses on sight: this exists to give the
 * recap something to count and must never become a catch-up about matches the customer did
 * not watch. Deterministic (fixed seed) so the demo says the same numbers every time.
 *
 * COMPLIANCE -- rows carry match facts only. There is no field for money to go in.
 */
object DemoLedgerSeed {

    private val fixtures = listOf(
        Triple("hajduk_split", "Hajduk Split", "Rijeka"),
        Triple("dinamo_zagreb", "Dinamo Zagreb", "Osijek"),
        Triple("liverpool", "Liverpool", "Everton"),
        Triple("hajduk_split", "Hajduk Split", "Dinamo Zagreb"),
        Triple("real_madrid", "Real Madrid", "Sevilla"),
        Triple("sparta", "Sparta", "Slavia"),
        Triple("hajduk_split", "Varaždin", "Hajduk Split"),
        Triple("liverpool", "Liverpool", "Arsenal"),
    )

    /** Returns how many rows were written. */
    fun seed(ledger: Ledger, now: Instant, days: Int): Int {
        val random = Random(17)
        var written = 0
        for (day in days downTo 1) {
            // Roughly every other day has a followed match; weekends more so.
            if (day % 2 == 1 && random.nextInt(4) != 0) continue
            val f = fixtures[day % fixtures.size]
            val at = now - day.days + random.nextInt(0, 6).hours
            val home = random.nextInt(0, 4)
            val away = random.nextInt(0, 3)
            val won = random.nextInt(3) != 0
            val type = if (won) MomentType.LEG_DECIDED else MomentType.LEG_LOST
            ledger.record(
                LedgerEntry(
                    id = "seed-" + at.toEpochMilliseconds() + "-" + f.first,
                    momentId = f.first + ":" + type.name + "@" + at.toEpochMilliseconds(),
                    momentType = type.name,
                    contextBucket = type.name + "|EVENING",
                    protection = "NORMAL",
                    score = 0.8,
                    surface = Surface.WIDGET.name,
                    tone = "PLAIN",
                    armId = "WIDGET|PLAIN|IMMEDIATE",
                    sampled = 0.8,
                    reason = "Demo seed: " + f.second + " " + home + "\u2013" + away + " " + f.third,
                    shownAt = at.toEpochMilliseconds(),
                    // Most followed matches were checked in on; that is what a streak counts.
                    tappedAt = if (random.nextInt(5) != 0) at.toEpochMilliseconds() + 60_000 else null,
                    matchId = f.first + "-" + day,
                    homeTeam = f.second,
                    awayTeam = f.third,
                    homeScore = home,
                    awayScore = away,
                    createdAt = at.toEpochMilliseconds(),
                ),
            )
            written++
        }
        return written
    }
}
