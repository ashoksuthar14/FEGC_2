package eu.feg.ambient

import eu.feg.ambient.ambient.recap.Recap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hard rule on [Recap], enforced on the class rather than on any one render of it.
 *
 * The recap is shareable and survives Calm Mode because it structurally cannot carry money
 * or wagering volume. That claim is only as good as the field list, and a field list is the
 * easiest thing in a codebase to grow by one. So the field names are checked here: a
 * `stakeTotal` or a `betsPlaced` added to the data class fails this test before it reaches
 * a card, and whoever adds it has to argue with this file rather than with a reviewer.
 *
 * Java reflection over declared fields, not Kotlin reflection over constructor parameters:
 * kotlin-reflect is not a dependency, and a data class's backing fields carry exactly the
 * constructor's names, which is all this needs.
 */
class RecapNoMoneyTest {

    /**
     * Substrings, matched case-insensitively. "win" also catches "winnings" and "wins";
     * "bet" catches "betsPlaced"; "odds" catches a price under any prefix. None of the
     * fields the recap is allowed to have — matchesFollowed, predictionsRight, checkIns,
     * longestStreak — contains any of them, which is checked below so a false positive in
     * this list cannot hide behind a broken assertion.
     */
    private val moneyWords = listOf(
        "stake", "win", "loss", "balance", "amount", "bet", "payout", "odds",
    )

    @Test
    fun `no field on Recap is shaped like money or wagering volume`() {
        val names = Recap::class.java.declaredFields.map { it.name }
        assertTrue("reflection found no fields; the check would pass vacuously", names.isNotEmpty())

        names.forEach { name ->
            val lower = name.lowercase()
            moneyWords.forEach { word ->
                assertFalse(
                    "Recap." + name + " contains \"" + word + "\" — a recap counts, it never values",
                    lower.contains(word),
                )
            }
        }
    }

    /** The fields the spec names must be present, or the class has been hollowed out. */
    @Test
    fun `the fields the spec names are all present`() {
        val names = Recap::class.java.declaredFields.map { it.name }.toSet()
        listOf(
            "matchesFollowed", "teamsFollowed", "topTeam", "predictionsRight",
            "predictionsTotal", "checkIns", "longestStreak", "headline", "detail", "spokenText",
        ).forEach { expected ->
            assertTrue("Recap is missing " + expected, expected in names)
        }
    }
}
