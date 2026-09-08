package eu.feg.ambient.ambient.engine.moment

import android.app.NotificationManager
import android.content.Context
import eu.feg.ambient.ambient.engine.AttentionBudget
import eu.feg.ambient.ambient.engine.Moment
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.surfaces.AlertBudget
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.data.model.QuietHours
import eu.feg.ambient.data.repo.UserStateRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.seconds

/**
 * Which surfaces are open right now, before anyone asks what the user might enjoy.
 *
 * This is the restraint half of the engine. The router optimises; this class only ever takes
 * options away, and it is deliberately the one place that can. An empty result is a real
 * answer — the caller records [Surface.NOTHING] against it, which is how the ledger ends up
 * holding the decisions to stay quiet as well as the decisions to speak.
 *
 * The 24h alert budget is [AlertBudget], the same instance the surfaces already enforce. A
 * second counter here would drift from it within an hour and we would have two truths about
 * how many times we interrupted someone. Note this class only *reads* the budget: consuming
 * it is the renderer's job, at the moment an alert actually posts, because a surface we chose
 * and then failed to show must not spend the day's one interruption.
 */
class DefaultAttentionBudget(
    private val context: Context,
    private val userStateRepository: UserStateRepository,
    private val alertBudget: AlertBudget = AlertBudget(context),
    private val clock: () -> Instant = { Clock.System.now() },
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : AttentionBudget {

    /** Last time each match-and-type pair was allowed through. In memory: see the scorer. */
    private val lastAllowedAt = LinkedHashMap<String, Instant>()

    override suspend fun allowedSurfaces(moment: Moment, protection: ProtectionState): Set<Surface> {
        // Nothing at all for a user we may not talk to. Checked first so no later branch can
        // accidentally hand one of these states a surface.
        if (protection == ProtectionState.BLOCKED || protection == ProtectionState.UNVERIFIED) {
            return emptySet()
        }

        val now = clock()
        if (isDuplicate(moment, now)) return emptySet()

        val user = userStateRepository.state.value
        val quiet = isQuietHour(user.quietHours, now)

        val allowed = mutableSetOf(
            Surface.LIVE_UPDATE,
            Surface.WIDGET,
            Surface.IN_APP,
            Surface.ALERT,
        )

        // Quiet hours are the user's own instruction, so they outrank everything else here.
        // IN_APP survives because it is not a push: it is what they find when they open the
        // app themselves, and withholding that would be hiding information, not being quiet.
        if (quiet) {
            allowed.retainAll(setOf(Surface.IN_APP))
        }

        if (protection == ProtectionState.CALM) allowed.remove(Surface.ALERT)
        if (!alertBudget.canSend()) allowed.remove(Surface.ALERT)
        if (isDoNotDisturb()) allowed.remove(Surface.ALERT)

        if (allowed.isNotEmpty()) {
            lastAllowedAt[key(moment)] = now
            prune()
        }
        return allowed
    }

    /**
     * The same thing said twice in a minute is the same thing said once. The simulator can
     * emit two goals a few ticks apart and the lock screen should not blink between them.
     * Keyed on match plus type, so a goal in another match is never suppressed by this.
     */
    private fun isDuplicate(moment: Moment, now: Instant): Boolean {
        val since = lastAllowedAt[key(moment)] ?: return false
        return (now - since) < DEDUPE_WINDOW
    }

    /**
     * Handles a window that crosses midnight, which is the only kind anyone sets: 23:00–08:00
     * is two ranges, not one, and comparing it as one range would leave the night wide open.
     * End-exclusive, so a window ending at 08:00 is over at 08:00.
     */
    private fun isQuietHour(quietHours: QuietHours?, now: Instant): Boolean {
        if (quietHours == null) return false
        val local: LocalTime = now.toLocalDateTime(timeZone).time
        val start = quietHours.start
        val end = quietHours.end
        if (start == end) return false
        return if (start < end) {
            local >= start && local < end
        } else {
            local >= start || local < end
        }
    }

    /**
     * Read from the system rather than mirrored in our own settings: the user may have turned
     * DND on thirty seconds ago and a stale copy would interrupt them anyway. Reading the
     * filter needs no policy-access grant; changing it would, and we never do.
     */
    private fun isDoNotDisturb(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val filter = runCatching { manager.currentInterruptionFilter }.getOrNull() ?: return false
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun key(moment: Moment): String = moment.matchId + "/" + moment.type.name

    private fun prune() {
        while (lastAllowedAt.size > MAX_DEDUPE_ENTRIES) {
            lastAllowedAt.remove(lastAllowedAt.keys.first())
        }
    }

    private companion object {
        val DEDUPE_WINDOW = 60.seconds
        const val MAX_DEDUPE_ENTRIES = 64
    }
}
