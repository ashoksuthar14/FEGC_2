package eu.feg.ambient

import org.junit.Test
import kotlin.random.Random

/**
 * Step 13.0c spike — THROWAWAY. Delete once the numbers are recorded.
 *
 * The demo claim is "it learned that in thirty seconds, from two gestures". If the bandit needs
 * two hundred interactions to visibly flip, that beat dies on stage. This measures the shape
 * before anything is built around it.
 */
class BanditSpikeTest {

    // --- a minimal Thompson sampler ---------------------------------------------------

    private class Arm(var wins: Double = 0.0, var losses: Double = 0.0)

    /** Beta(a,b) via two Gamma draws — enough for a spike, no library needed. */
    private fun sampleBeta(a: Double, b: Double, rng: Random): Double {
        val x = sampleGamma(a, rng)
        val y = sampleGamma(b, rng)
        return if (x + y == 0.0) 0.5 else x / (x + y)
    }

    /** Marsaglia-Tsang. Handles a < 1 by the standard boost. */
    private fun sampleGamma(shape: Double, rng: Random): Double {
        if (shape < 1.0) {
            val u = rng.nextDouble().coerceAtLeast(1e-12)
            return sampleGamma(shape + 1.0, rng) * Math.pow(u, 1.0 / shape)
        }
        val d = shape - 1.0 / 3.0
        val c = 1.0 / Math.sqrt(9.0 * d)
        while (true) {
            var x: Double
            var v: Double
            do {
                x = gaussian(rng)
                v = 1.0 + c * x
            } while (v <= 0)
            v = v * v * v
            val u = rng.nextDouble()
            if (u < 1.0 - 0.0331 * x * x * x * x) return d * v
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) return d * v
        }
    }

    private fun gaussian(rng: Random): Double {
        val u1 = rng.nextDouble().coerceAtLeast(1e-12)
        val u2 = rng.nextDouble()
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2)
    }

    private fun choose(arms: List<Arm>, rng: Random): Int =
        arms.indices.maxByOrNull { sampleBeta(arms[it].wins + 1.0, arms[it].losses + 1.0, rng) } ?: 0

    // --- the three questions ----------------------------------------------------------

    /** Arm 1 ("B") is genuinely best. */
    private val rewardProbability = listOf(0.3, 0.8, 0.3, 0.3)
    private val bestArm = 1

    @Test
    fun `spike answers the three demo questions`() {
        println("=== 13.0c bandit spike ===")
        println("Q1 interactions until arm B dominates in 9 of 10 runs: " + questionOne())
        println("Q2 new interactions to flip A to B after 8 prior observations: " + questionTwo())
        println("Q3 prior strength where two negatives visibly change the choice: " + questionThree())
        println("TUNING " + tuneForDemo())
    }

    /**
     * The number that actually matters. A flip on one interaction reads as randomness on stage;
     * the demo wants two or three gestures to visibly move it. This finds the incumbent prior
     * strength that puts the flip in that window, and the reward magnitude that goes with it.
     */
    private fun tuneForDemo(): String {
        for (seed in 1..40) {
            val flips = flipsNeeded(incumbentWins = seed * 3.0, incumbentLosses = seed * 1.0)
            if (flips in 2..4) {
                return "incumbent prior wins=" + (seed * 3) + " losses=" + seed +
                    " -> flip after " + flips + " gestures"
            }
        }
        return "no seed in range produced a 2-4 gesture flip"
    }

    /** How many consecutive rewards on B are needed before B visibly wins? */
    private fun flipsNeeded(incumbentWins: Double, incumbentLosses: Double): Int {
        for (fresh in 1..30) {
            var flipped = 0
            repeat(10) { run ->
                val rng = Random(run * 32452843 + fresh)
                val arms = List(4) { Arm() }
                arms[0].wins = incumbentWins
                arms[0].losses = incumbentLosses
                repeat(fresh) { arms[bestArm].wins += 1.0 }
                val counts = IntArray(4)
                repeat(200) { counts[choose(arms, rng)]++ }
                if (counts.indices.maxByOrNull { counts[it] } == bestArm) flipped++
            }
            if (flipped >= 9) return fresh
        }
        return -1
    }

    /** How many interactions before B is the most-sampled choice in 9 of 10 runs? */
    private fun questionOne(): Int {
        for (budget in 1..200) {
            var successes = 0
            repeat(10) { run ->
                val rng = Random(run * 7919 + budget)
                val arms = List(4) { Arm() }
                repeat(budget) {
                    val picked = choose(arms, rng)
                    if (rng.nextDouble() < rewardProbability[picked]) {
                        arms[picked].wins += 1.0
                    } else {
                        arms[picked].losses += 1.0
                    }
                }
                // Most-sampled over a fresh set of draws, i.e. what the user would now see.
                val counts = IntArray(4)
                repeat(100) { counts[choose(arms, rng)]++ }
                if (counts.indices.maxByOrNull { counts[it] } == bestArm) successes++
            }
            if (successes >= 9) return budget
        }
        return -1
    }

    /**
     * The number that decides the demo: with A pre-seeded as the incumbent, how many NEW
     * interactions flip the visible winner to B?
     */
    private fun questionTwo(): Int {
        for (fresh in 1..40) {
            var flipped = 0
            repeat(10) { run ->
                val rng = Random(run * 104729 + fresh)
                val arms = List(4) { Arm() }
                // A is the incumbent: 8 prior observations, mostly good.
                arms[0].wins = 6.0
                arms[0].losses = 2.0
                repeat(fresh) {
                    // The user is now rewarding B, as they would on stage.
                    arms[bestArm].wins += 1.0
                }
                val counts = IntArray(4)
                repeat(200) { counts[choose(arms, rng)]++ }
                if (counts.indices.maxByOrNull { counts[it] } == bestArm) flipped++
            }
            if (flipped >= 9) return fresh
        }
        return -1
    }

    /**
     * With how weak a prior do two consecutive negatives visibly move the choice away from an
     * incumbent? Reported as the prior strength, i.e. wins+losses on the incumbent arm.
     */
    private fun questionThree(): String {
        for (prior in 1..20) {
            var moved = 0
            repeat(10) { run ->
                val rng = Random(run * 15485863 + prior)
                val arms = List(4) { Arm() }
                arms[0].wins = prior.toDouble()
                // Two consecutive negatives, at the -1.0 dismissal magnitude.
                arms[0].losses = 2.0
                val counts = IntArray(4)
                repeat(200) { counts[choose(arms, rng)]++ }
                if (counts.indices.maxByOrNull { counts[it] } != 0) moved++
            }
            if (moved < 9) {
                return "prior strength " + (prior - 1) + " or weaker (at " + prior +
                    " the incumbent survives two negatives)"
            }
        }
        return "any prior up to 20 still moves"
    }
}
