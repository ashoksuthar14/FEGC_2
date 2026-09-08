package eu.feg.ambient.data.clock

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * The seam Phase 2 replaces with the scripted match simulator (PRD section 11, seam 2).
 * Nothing outside `data/clock` may depend on a concrete implementation.
 */
interface MatchClock {
    /** Emits once per tick. Collectors advance minutes and re-price odds. */
    val ticks: Flow<Unit>
    fun now(): Instant
}

/** Phase 1 implementation: a plain one-second heartbeat off the system clock. */
class SystemMatchClock(private val periodMillis: Long = 1_000L) : MatchClock {

    override val ticks: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(periodMillis)
        }
    }

    override fun now(): Instant = Clock.System.now()
}
