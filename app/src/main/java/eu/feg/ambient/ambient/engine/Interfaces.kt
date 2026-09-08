package eu.feg.ambient.ambient.engine

import eu.feg.ambient.ambient.surfaces.ProtectionState

/**
 * The three seams, one per workstream. Each is small on purpose: the engine chains them, and
 * anything that needs more than this signature is doing work that belongs somewhere else.
 */

/**
 * Answers "may we say anything to this person at all". Runs before relevance, always, because
 * an excluded user's moment must never be scored, let alone rendered.
 */
interface ProtectionEvaluator {
    suspend fun evaluate(): ProtectionState
}

/**
 * Deterministic and auditable. No model and no randomness — "Why this?" has to be able to
 * explain the number, and a learned score cannot be explained in a sentence.
 */
interface RelevanceScorer {
    fun score(moment: Moment): Double
}

/**
 * Chooses how to say it, and learns from what happens next.
 *
 * [choose] may only return an arm whose surface is in [allowed]: pruning happens before
 * sampling, never after, so an arm the budget forbade can never win and then be discarded.
 */
interface Router {
    suspend fun choose(moment: Moment, score: Double, allowed: Set<Surface>): Decision

    suspend fun reward(armId: String, context: String, r: Double)
}

/**
 * Decides which surfaces are open right now, independent of what the user might like.
 * Quiet hours, do-not-disturb and the alert budget all land here.
 */
interface AttentionBudget {
    suspend fun allowedSurfaces(moment: Moment, protection: ProtectionState): Set<Surface>
}

/** Builds a [Moment] from an event, or returns null when the user owns none of it. */
interface MomentBuilder {
    suspend fun build(event: MatchEvent, protection: ProtectionState): Moment?
}
