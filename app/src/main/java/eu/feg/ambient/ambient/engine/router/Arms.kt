package eu.feg.ambient.ambient.engine.router

import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.engine.TimeBucket
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.Tone

/**
 * The arms the router chooses between, and the context buckets it keeps separate counters for.
 *
 * An arm is the whole delivery decision — where, in what voice, and when — because those three
 * are not independent. A witty line on the lock screen at the moment of a goal is a different
 * proposition from the same line in the widget an hour later, and a bandit that learned them
 * separately would learn three shallow things instead of one useful one.
 */
enum class Timing { IMMEDIATE, NEXT_BREAK, END_OF_MATCH }

/**
 * [id] is the ledger's primary key for this arm and is written into every row, so it must never
 * change shape once a demo device has state on it. Pipe-separated because a ledger row is read
 * by a human on the transparency sheet as often as it is parsed.
 */
data class Arm(
    val surface: Surface,
    val tone: Tone,
    val timing: Timing,
    val id: String,
)

object Arms {

    /** NOTHING is excluded here: staying quiet is one arm, not twelve variations of quiet. */
    val SPEAKING_SURFACES: List<Surface> = listOf(
        Surface.LIVE_UPDATE,
        Surface.WIDGET,
        Surface.IN_APP,
        Surface.ALERT,
    )

    const val NOTHING_ID: String = "NOTHING"

    /**
     * Choosing silence is a real arm with real counters, so "we were right to say nothing" is
     * something the router can actually learn rather than a fallback it falls through to.
     */
    val NOTHING: Arm = Arm(Surface.NOTHING, Tone.PLAIN, Timing.IMMEDIATE, NOTHING_ID)

    /** 4 surfaces x 4 tones x 3 timings. */
    private val speaking: List<Arm> = SPEAKING_SURFACES.flatMap { surface ->
        Tone.entries.flatMap { tone ->
            Timing.entries.map { timing ->
                Arm(surface, tone, timing, idOf(surface, tone, timing))
            }
        }
    }

    private val all: List<Arm> = speaking + NOTHING

    private val index: Map<String, Arm> = all.associateBy { it.id }

    /** All 49: the 48 ways to say something, plus the one way to say nothing. */
    fun allArms(): List<Arm> = all

    /** The 48 speaking arms, with no silence arm mixed in. */
    fun speakingArms(): List<Arm> = speaking

    /**
     * The prune step. The router calls this before it samples anything, so an arm the attention
     * budget closed can never win a draw and then be thrown away — a discarded winner is still a
     * biased choice, because the runner-up that gets used was never sampled against the field.
     */
    fun armsForSurfaces(allowed: Set<Surface>): List<Arm> = all.filter { it.surface in allowed }

    fun byId(id: String): Arm? = index[id]

    fun idOf(surface: Surface, tone: Tone, timing: Timing): String =
        surface.name + "|" + tone.name + "|" + timing.name

    /**
     * Counters are kept per context, not globally. Someone who taps a lock-screen goal alert at
     * 21:00 is not the same reader as the one being handed a digest at 07:00, and averaging the
     * two teaches the router the habits of a person who does not exist.
     */
    fun contextBucket(type: MomentType, time: TimeBucket): String = type.name + "|" + time.name
}
