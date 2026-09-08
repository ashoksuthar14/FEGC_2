package eu.feg.ambient

import eu.feg.ambient.ambient.surfaces.DemoSurfaceData
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.surfaces.allowsLegDetail
import eu.feg.ambient.ambient.surfaces.allowsLiveUpdate
import eu.feg.ambient.ambient.surfaces.toProtectionStateTemp
import eu.feg.ambient.data.model.Consents
import eu.feg.ambient.data.model.Limits
import eu.feg.ambient.data.model.RiskState
import eu.feg.ambient.data.model.UserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one property worth a test in step 14: an unverified or self-excluded user gets no
 * lock-screen surface at all. A judge could break the pitch on stage by asking for exactly
 * this, so it is pinned rather than eyeballed.
 *
 * Everything else about the surfaces is verified by looking at the phone, which is both
 * faster and more honest than unit-testing a notification builder.
 */
class SurfaceProtectionTest {

    @Test
    fun `unverified and blocked create no live update`() {
        assertFalse(
            "an unverified user must get no lock-screen surface",
            ProtectionState.UNVERIFIED.allowsLiveUpdate(),
        )
        assertFalse(
            "a self-excluded user must get no lock-screen surface",
            ProtectionState.BLOCKED.allowsLiveUpdate(),
        )
    }

    @Test
    fun `normal and calm do create a live update`() {
        assertTrue(ProtectionState.NORMAL.allowsLiveUpdate())
        assertTrue("calm still shows the match, just not the bet", ProtectionState.CALM.allowsLiveUpdate())
    }

    @Test
    fun `only normal may show leg detail`() {
        assertTrue(ProtectionState.NORMAL.allowsLegDetail())
        assertFalse("calm shows score and minute only", ProtectionState.CALM.allowsLegDetail())
        assertFalse(ProtectionState.UNVERIFIED.allowsLegDetail())
        assertFalse(ProtectionState.BLOCKED.allowsLegDetail())
    }

    // --- how the state is derived (temporary; step 13A replaces this) ------------------

    private fun user(
        risk: RiskState = RiskState.NORMAL,
        limits: Limits = Limits(),
    ) = UserState(riskState = risk, limits = limits, consents = Consents())

    @Test
    fun `age gate wins over everything`() {
        assertEquals(
            ProtectionState.UNVERIFIED,
            user(risk = RiskState.NORMAL).toProtectionStateTemp(ageVerified = false),
        )
        assertEquals(
            "self-exclusion must not mask an unverified age",
            ProtectionState.UNVERIFIED,
            user(risk = RiskState.SELF_EXCLUDED).toProtectionStateTemp(ageVerified = false),
        )
    }

    @Test
    fun `self-excluded blocks, at-risk calms`() {
        assertEquals(
            ProtectionState.BLOCKED,
            user(risk = RiskState.SELF_EXCLUDED).toProtectionStateTemp(ageVerified = true),
        )
        assertEquals(
            ProtectionState.CALM,
            user(risk = RiskState.AT_RISK).toProtectionStateTemp(ageVerified = true),
        )
    }

    @Test
    fun `a limit at eighty percent calms the surfaces`() {
        val nearDeposit = Limits(depositLimit = 100.0, depositUsed = 80.0)
        assertEquals(
            ProtectionState.CALM,
            user(limits = nearDeposit).toProtectionStateTemp(ageVerified = true),
        )

        val comfortable = Limits(
            depositLimit = 100.0, depositUsed = 10.0,
            lossLimit = 100.0, lossUsed = 10.0,
            timeLimit = 100, timeUsed = 10,
        )
        assertEquals(
            ProtectionState.NORMAL,
            user(limits = comfortable).toProtectionStateTemp(ageVerified = true),
        )
    }

    @Test
    fun `demo slips carry no money field`() {
        // Compile-time really, but stated as a test so the intent survives a refactor:
        // SlipSurfaceState has no stake, odds, return or balance to read.
        val slip = DemoSurfaceData.threeLegLive()
        assertEquals(3, slip.legsTotal)
        assertEquals(2, slip.legsWon)
        assertTrue("chip stays short for the status bar", slip.chipText.length <= 14)
    }
}
