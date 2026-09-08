package eu.feg.ambient.ambient.digest

import android.content.Context
import android.content.SharedPreferences
import eu.feg.ambient.ambient.surfaces.ProtectionState
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * How long the customer has been away, and whether that is worth a catch-up.
 *
 * SharedPreferences rather than DataStore, which the brief suggested. DataStore would be a new
 * direct dependency for two longs, and every other preference in this app — user state, the
 * alert budget, the widget snapshot, the club — already lives here. More to the point, this is
 * read from `provideGlance`, which the launcher calls in whatever process it wakes: a blocking
 * read of a small file is exactly right there, and a suspending DataStore read is not.
 *
 * The shown-flag is the part that is easy to get wrong. It is keyed to the away-period it was
 * shown for, not to a boolean, because a plain flag cleared at the wrong moment gives you a
 * digest on every single unlock — which is the overnight push we are claiming to have replaced,
 * wearing a different hat.
 */
class AwayTracker(private val prefs: SharedPreferences?) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
    )

    /** Called on every app foreground and every surface tap. */
    fun markInteraction(at: Instant = Clock.System.now()) {
        prefs?.edit()
            ?.putLong(KEY_LAST_INTERACTION, at.toEpochMilliseconds())
            // A new interaction ends the away-period, so the next one is allowed its own digest.
            ?.remove(KEY_SHOWN_FOR)
            ?.apply()
    }

    fun lastInteractionAt(): Instant? =
        prefs?.getLong(KEY_LAST_INTERACTION, 0L)
            ?.takeIf { it > 0L }
            ?.let { Instant.fromEpochMilliseconds(it) }

    fun awayDuration(now: Instant = Clock.System.now()): Duration {
        val last = lastInteractionAt() ?: return Duration.ZERO
        val gap = now - last
        return if (gap.isNegative()) Duration.ZERO else gap
    }

    /**
     * Whether to show a digest right now.
     *
     * Three conditions, and all three matter:
     *  - away for at least [AWAY_THRESHOLD]. Below an hour there is nothing to catch up on.
     *  - not already shown for this away-period. The launcher redraws a widget whenever it
     *    likes, so without this the digest would reappear on every redraw and read as a loop.
     *  - protection allows it. An excluded or unverified account gets no catch-up, because a
     *    catch-up is a summary of betting activity and there is none to summarise.
     */
    fun shouldShowDigest(
        protection: ProtectionState,
        now: Instant = Clock.System.now(),
    ): Boolean {
        if (protection == ProtectionState.BLOCKED || protection == ProtectionState.UNVERIFIED) {
            return false
        }
        val last = lastInteractionAt() ?: return false
        if (now - last < AWAY_THRESHOLD) return false
        return prefs?.getLong(KEY_SHOWN_FOR, 0L) != last.toEpochMilliseconds()
    }

    /**
     * Records that the away-period beginning at the last interaction has had its digest.
     *
     * Stamped with the away-period's own start rather than with "true", so a later interaction
     * — which moves that start — automatically re-arms the digest without anything having to
     * remember to clear a flag.
     */
    fun markDigestShown() {
        val last = lastInteractionAt() ?: return
        prefs?.edit()?.putLong(KEY_SHOWN_FOR, last.toEpochMilliseconds())?.apply()
    }

    /** Demo affordance: pretend the customer has been away for [minutes]. */
    fun simulateAway(minutes: Int) {
        val at = Clock.System.now() - minutes.minutes
        prefs?.edit()
            ?.putLong(KEY_LAST_INTERACTION, at.toEpochMilliseconds())
            ?.remove(KEY_SHOWN_FOR)
            ?.apply()
    }

    companion object {
        /** The spec's threshold: an hour away is when a catch-up starts being worth anything. */
        val AWAY_THRESHOLD = 60.minutes

        private const val PREFS = "ambient_away"
        private const val KEY_LAST_INTERACTION = "last_interaction"
        private const val KEY_SHOWN_FOR = "digest_shown_for"
    }
}
