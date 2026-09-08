package eu.feg.ambient.ambient.surfaces.live

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import eu.feg.ambient.AmbientApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * A swipe on an alert, turned into a reward the router can learn from.
 *
 * WHY THIS EXISTS. The bandit had exactly one input: a thumb on the widget. Everything else
 * the customer did with a notification was invisible to it -- an alert nobody wanted looked
 * identical to an alert nobody had seen, and the arm that sent it kept its score either way.
 * A learning loop with one input that requires a deliberate gesture is a learning loop that
 * mostly does not run.
 *
 * A swipe is the cheapest honest signal there is. It costs the customer nothing, they make it
 * without being asked, and it means precisely one thing: not this, not now. RewardTable
 * already grades it -- fast dismissals harder than slow ones, because a card swiped inside two
 * seconds was rejected on sight while one dismissed a minute later was at least read.
 *
 * The tap half lives in MainActivity rather than here, because a tap opens the app and the
 * activity is what learns it happened; see [EXTRA_ENTRY_ID].
 */
class AlertFeedbackReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
        if (entryId.isNullOrBlank()) {
            Log.w(TAG, "dismiss with no entry id - nothing to attribute")
            return
        }
        val app = context.applicationContext as? AmbientApp ?: return

        // goAsync would be the careful thing for a long job; this is one ledger write and a
        // Beta update, and the process is already alive because it just posted the alert.
        scope.launch {
            runCatching { app.container.engine.onDismissed(entryId) }
                .onSuccess { Log.i(TAG, "swiped away: " + entryId) }
                .onFailure { Log.w(TAG, "could not record the dismissal", it) }
        }
    }

    companion object {
        const val ACTION_ALERT_DISMISSED = "eu.feg.ambient.ALERT_DISMISSED"

        /** Carried by both the delete intent and the content intent; the ledger row's id. */
        const val EXTRA_ENTRY_ID = "feedbackEntryId"

        private const val TAG = "AlertFeedback"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * The PendingIntent the system fires when the alert is swiped away.
         *
         * The entry id is the request code as well as an extra: two alerts in one day would
         * otherwise share a PendingIntent and the second would silently reuse the first's
         * extras, attributing a swipe to the wrong decision.
         */
        fun dismissIntent(context: Context, entryId: String): PendingIntent {
            val intent = Intent(context, AlertFeedbackReceiver::class.java)
                .setAction(ACTION_ALERT_DISMISSED)
                .putExtra(EXTRA_ENTRY_ID, entryId)
            return PendingIntent.getBroadcast(
                context,
                entryId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
