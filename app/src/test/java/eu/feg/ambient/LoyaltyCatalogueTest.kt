package eu.feg.ambient

import eu.feg.ambient.ambient.loyalty.LoyaltyCatalogue
import eu.feg.ambient.ambient.loyalty.MissionType
import eu.feg.ambient.ambient.loyalty.PerkCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The two tests this feature is allowed to have, and the two it needs.
 *
 * They are not testing that the catalogue parses. They are testing the claim the feature is
 * pitched on: that a wagering mission and a gambling reward are not merely absent from the
 * fixtures but cannot be added without someone editing an enum -- which is a decision that
 * shows up in a diff and gets argued about, rather than a line of JSON nobody reviews.
 *
 * If either of these ever fails, the failure is not a broken test. It is somebody having
 * quietly turned this into the thing it was built not to be.
 */
class LoyaltyCatalogueTest {

    private val catalogue = LoyaltyCatalogue { path ->
        File("src/main/assets/" + path).readText()
    }

    /** Words that would each mean a mission progresses by staking money. */
    private val wagering = listOf("bet", "wager", "stake", "deposit_amount", "spend", "turnover")

    /** Words that would each mean a reward is gambling credit. */
    private val gambling = listOf("bet", "bonus", "cash", "spin", "boost", "freebet", "credit")

    @Test
    fun `no mission requires a wager`() {
        // Every shipped mission is a type the enum knows. Decoding would have thrown on an
        // unknown one, so this asserts the catalogue is non-empty and complete rather than
        // re-asserting the parser.
        assertEquals(8, catalogue.missions.size)
        assertTrue(catalogue.missions.all { it.type in MissionType.entries })
        assertTrue(catalogue.missions.all { it.target > 0 })

        // And the enum itself has no wagering case, which is the claim that matters: a
        // mission requiring a bet cannot be expressed, not merely does not appear.
        MissionType.entries.forEach { type ->
            val name = type.name.lowercase()
            wagering.forEach { banned ->
                assertFalse(
                    "MissionType." + type.name + " looks like a wagering mission. " +
                        "A mission that progresses when the customer stakes money is the harm " +
                        "pattern this design exists to prevent -- see LoyaltyModel.kt.",
                    name.contains(banned),
                )
            }
        }

        // The mission worth leading with has to actually be in there.
        assertTrue(catalogue.missions.any { it.type == MissionType.SET_A_LIMIT })
    }

    @Test
    fun `no perk is a gambling reward`() {
        assertEquals(6, catalogue.perks.size)
        assertTrue(catalogue.perks.all { it.category in PerkCategory.entries })

        PerkCategory.entries.forEach { category ->
            val name = category.name.lowercase()
            gambling.forEach { banned ->
                assertFalse(
                    "PerkCategory." + category.name + " looks like gambling credit. " +
                        "A reward that can only be spent by gambling turns loyalty into " +
                        "stakes and locks out anyone taking a break -- see LoyaltyModel.kt.",
                    name.contains(banned),
                )
            }
        }

        // Deterministic: a stated cost, a stated tier, real stock, and terms in plain words
        // rather than a link the customer has to chase.
        catalogue.perks.forEach { perk ->
            assertTrue(perk.name, perk.badgeCost > 0)
            assertTrue(perk.name, perk.stock > 0)
            assertTrue(perk.name, perk.terms.length > 40)
        }
    }
}
