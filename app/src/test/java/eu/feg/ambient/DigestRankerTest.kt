package eu.feg.ambient

import eu.feg.ambient.ambient.digest.DigestRanker
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.engine.ledger.LedgerEntry
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.surfaces.ProtectionState
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ranker is the only part of the digest with judgement in it, so it is the part worth a
 * test. No Ledger, no Context, no coroutine — that the ranker can be exercised this plainly
 * is the reason it was kept pure.
 */
class DigestRankerTest {

    private val base = 1_700_000_000_000L

    /**
     * Nine things happened. The card is allowed four, and it has to pick the four that a
     * customer would have wanted to be told — the settled slip first, the decided legs next,
     * a goal after that, and the kickoffs and half times left off entirely.
     */
    @Test
    fun `nine entries collapse to the four most significant, in order`() {
        val entries = listOf(
            entry(0, "m1", MomentType.KICKOFF_FOLLOWED),
            entry(1, "m2", MomentType.HALFTIME),
            entry(2, "m3", MomentType.GOAL_ON_SLIP),
            entry(3, "m4", MomentType.LEG_LOST),
            entry(4, "m5", MomentType.SLIP_SETTLED),
            entry(5, "m6", MomentType.KICKOFF_FOLLOWED),
            entry(6, "m7", MomentType.MINUTES_REMAINING),
            entry(7, "m8", MomentType.LEG_DECIDED),
            // Never shown. It still belongs in a catch-up: choosing not to interrupt is not
            // the same as deciding it did not happen.
            entry(8, "m9", MomentType.GOAL_ON_SLIP, surface = Surface.NOTHING),
        )

        val items = DigestRanker.rank(entries, since = Instant.fromEpochMilliseconds(base))

        assertEquals(DigestRanker.MAX_ITEMS, items.size)
        assertEquals(
            listOf("m5", "m8", "m4", "m9"),
            items.map { it.momentId.substringBefore(":") },
        )
        assertTrue("settled and decided legs are the important tier", items[0].important)
        assertTrue(items[1].important)
        assertTrue(items[2].important)
        assertFalse("a goal is worth listing but not flagging", items[3].important)
    }

    /** Newest first within a tier, so the index doubles as the timestamp. */
    private fun entry(
        index: Int,
        matchId: String,
        type: MomentType,
        surface: Surface = Surface.LIVE_UPDATE,
    ): LedgerEntry {
        val at = base + index * 1_000L
        val momentId = matchId + ":" + type.name + "@" + at
        return LedgerEntry(
            id = "led-" + at + "-" + momentId,
            momentId = momentId,
            momentType = type.name,
            contextBucket = type.name + "|EVENING",
            protection = ProtectionState.NORMAL.name,
            score = 0.5,
            surface = surface.name,
            tone = "PLAIN",
            armId = "arm",
            sampled = 0.5,
            reason = "test row",
            shownAt = if (surface == Surface.NOTHING) null else at,
            createdAt = at,
        )
    }
}
