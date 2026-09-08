package eu.feg.ambient

import eu.feg.ambient.ambient.engine.MatchEvent
import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.loyalty.BadgeAwarder
import eu.feg.ambient.ambient.loyalty.BadgeRepository
import eu.feg.ambient.ambient.loyalty.LoyaltyCatalogue
import eu.feg.ambient.ambient.loyalty.LoyaltyTier
import eu.feg.ambient.ambient.loyalty.MissionEvaluator
import eu.feg.ambient.ambient.loyalty.MissionRepository
import eu.feg.ambient.ambient.loyalty.MissionSignals
import eu.feg.ambient.ambient.loyalty.MissionTracker
import eu.feg.ambient.ambient.loyalty.MissionType
import eu.feg.ambient.ambient.loyalty.store.LoyaltyStore
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.model.Limits
import eu.feg.ambient.data.repo.BetRepository
import eu.feg.ambient.data.repo.UserStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The claim the loyalty mechanic is allowed to make out loud: a badge is announced once.
 *
 * The tracker recomputes from the sources on every change, so the same completion is seen
 * again and again for the life of the install. If any of that ever produced a second
 * MISSION_COMPLETE, the customer would get the same badge pushed at them repeatedly -- which
 * is the mission-nagging every competitor ships and the reason missions go through the
 * engine here. Real store, real repository, real file: the idempotency key lives in
 * LoyaltyStore.awardBadge and a fake would only prove the fake.
 */
class MissionIdempotencyTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val catalogue = LoyaltyCatalogue { path ->
        File("src/main/assets/" + path).readText()
    }

    private val at: Instant = Instant.fromEpochMilliseconds(1_757_000_000_000L)

    private fun store() = LoyaltyStore(File(folder.root, "loyalty.json"))

    /**
     * Records every event the awarder raised, standing in for AmbientEngine.onEvent.
     *
     * MatchEvent rather than Moment: the awarder posts through the engine's own door, so the
     * moment is built by DefaultMomentBuilder from event.loyalty and the whole pipeline --
     * protection, score, budget, router, ledger row -- applies to a badge exactly as it does
     * to a goal. A test that accepted a pre-built Moment would be testing a path the product
     * does not have.
     */
    private class RecordingSink {
        val raised = mutableListOf<MatchEvent>()
        val raise: suspend (MatchEvent) -> Unit = { raised += it }
    }

    @Test
    fun `a badge is awarded exactly once however often completion is observed`() = runTest {
        val store = store()
        val badges = BadgeRepository(catalogue, store)
        val missions = MissionRepository(catalogue, store)
        val sink = RecordingSink()
        val awarder = BadgeAwarder(badges, sink.raise) { at }

        val mission = missions.of(MissionType.SET_A_LIMIT).single()
        missions.record(mission.id, progress = 1, at = at)
        val completed = missions.mission(mission.id)!!
        assertTrue(completed.isComplete)

        // First observation: the badge exists, and the completion is announced once.
        //
        // TWO events, not one, and that is correct rather than a leak: "Set a deposit limit"
        // is weight 3 and Silver needs 3, so the first badge earned this way also takes the
        // customer up a tier. Asserting on the MISSION_COMPLETE count rather than on the list
        // size keeps this test about idempotency instead of about the tier table.
        assertNotNull(awarder.onCompleted(completed))
        assertEquals(
            listOf(MomentType.MISSION_COMPLETE, MomentType.TIER_REACHED),
            sink.raised.map { it.type },
        )
        assertEquals(mission.title, sink.raised.first().loyalty?.missionTitle)

        // Every later observation of the same completion is a no-op on every count.
        val afterFirst = sink.raised.size
        repeat(25) { assertNull(awarder.onCompleted(completed)) }
        assertEquals(afterFirst, sink.raised.size)
        assertEquals(1, sink.raised.count { it.type == MomentType.MISSION_COMPLETE })
        assertEquals(1, store.badges.value.size)
        assertEquals(1, badges.snapshot().size)
    }

    @Test
    fun `the award survives the process and is still not repeated after a reload`() = runTest {
        val file = File(folder.root, "loyalty.json")
        val first = BadgeRepository(catalogue, LoyaltyStore(file))
        val mission = catalogue.missions.first { it.type == MissionType.SET_A_LIMIT }
        assertNotNull(first.award(mission.id, at))

        // A fresh store over the same file is what a restarted process sees.
        val reloaded = BadgeRepository(catalogue, LoyaltyStore(file))
        assertNull(reloaded.award(mission.id, at))
        assertEquals(1, reloaded.snapshot().size)
    }

    @Test
    fun `tier is raised once, on the completion that crosses it`() = runTest {
        val store = store()
        val badges = BadgeRepository(catalogue, store)
        val missions = MissionRepository(catalogue, store)
        val sink = RecordingSink()
        val awarder = BadgeAwarder(badges, sink.raise) { at }

        // SET_A_LIMIT weighs 3, which is SILVER on its own. Completing it must announce the
        // badge and the tier; completing it again must announce neither.
        val mission = missions.of(MissionType.SET_A_LIMIT).single()
        missions.record(mission.id, 1, at)
        awarder.onCompleted(missions.mission(mission.id)!!)
        awarder.onCompleted(missions.mission(mission.id)!!)

        assertEquals(listOf(MomentType.MISSION_COMPLETE, MomentType.TIER_REACHED), sink.raised.map { it.type })
        assertEquals(LoyaltyTier.SILVER, badges.tier())
        assertEquals("Silver", sink.raised.last().loyalty?.tierName)
        assertEquals(LoyaltyTier.GOLD.badgesRequired - 3, sink.raised.last().loyalty?.badgesToNextTier)
    }

    // --- the tracker end to end, on the JVM ---------------------------------------------

    private object StillClock : MatchClock {
        override val ticks: Flow<Unit> = emptyFlow()
        override fun now(): Instant = Clock.System.now()
    }

    @Test
    fun `setting a limit completes the mission once, and calm pauses it`() = runTest {
        val store = store()
        val badges = BadgeRepository(catalogue, store)
        val missions = MissionRepository(catalogue, store)
        val sink = RecordingSink()
        val user = UserStateRepository(null)
        val protection = MutableStateFlow(ProtectionState.CALM)
        val ledgerFile = File(folder.root, "ledger.json")

        val tracker = MissionTracker(
            missionRepository = missions,
            badgeAwarder = BadgeAwarder(badges, sink.raise) { at },
            userStateRepository = user,
            betRepository = BetRepository(StillClock),
            ledger = Ledger(ledgerFile),
            protection = protection,
            now = { at },
        )

        // The demo beat: the customer sets a deposit limit below the default.
        user.setLimits(Limits(depositLimit = 100.0))

        // In CALM nothing accrues, however many times the tracker looks.
        repeat(3) { tracker.recompute() }
        assertEquals(0, missions.of(MissionType.SET_A_LIMIT).single().progress)
        assertTrue(sink.raised.isEmpty())

        // Back to NORMAL: recorded, awarded, announced -- once, across repeated recomputes.
        protection.value = ProtectionState.NORMAL
        repeat(5) { tracker.recompute() }
        assertTrue(missions.of(MissionType.SET_A_LIMIT).single().isComplete)
        assertEquals(1, sink.raised.count { it.type == MomentType.MISSION_COMPLETE })
        assertEquals(1, store.badges.value.size)

        // And going CALM again takes nothing away.
        protection.value = ProtectionState.CALM
        tracker.recompute()
        assertTrue(missions.of(MissionType.SET_A_LIMIT).single().isComplete)
        assertEquals(1, store.badges.value.size)
    }

    // --- the judgement, without any I/O -------------------------------------------------

    @Test
    fun `a moved usage counter is not a limit the customer set`() {
        val evaluator = MissionEvaluator()
        fun progressFor(limits: Limits): Int = evaluator.progressOf(
            MissionType.SET_A_LIMIT,
            MissionSignals(limitSet = evaluator.limitSet(limits)),
        )

        assertEquals(0, progressFor(Limits()))
        // Depositing more moves depositUsed and nothing else. That is not setting a limit.
        assertEquals(0, progressFor(Limits(depositUsed = 400.0)))
        assertEquals(1, progressFor(Limits(depositLimit = 100.0)))
        // Raising a cap is still the customer deciding a ceiling; the mission is about the act.
        assertEquals(1, progressFor(Limits(timeLimit = 240)))
    }
}
