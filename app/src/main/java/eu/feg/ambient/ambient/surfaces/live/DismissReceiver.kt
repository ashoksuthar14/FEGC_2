package eu.feg.ambient.ambient.surfaces.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Records that the user swiped a Live Update away.
 *
 * This exists because of one rule rather than any feature: Google's Live Update guidance is
 * explicit that a dismissed Live Update must not be reposted, and the moment engine keeps
 * ticking after a dismissal — it has no idea the card is gone. Without this receiver the very
 * next tick would put the card straight back on the lock screen, which reads as an app that
 * will not take no for an answer and is exactly the behaviour that loses promotion privilege.
 *
 * A swipe is a decision about this slip, not about notifications in general, so it is recorded
 * per slip: another slip placed later still gets its card.
 */
class DismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val slipId = intent.getStringExtra(EXTRA_SLIP_ID)
        if (slipId.isNullOrBlank()) {
            Log.w(TAG, "dismiss broadcast with no slipId — ignoring")
            return
        }
        Log.i(TAG, "user dismissed the Live Update for slip " + slipId + "; will not repost")

        // Shared through the renderer's companion object. Step 13 moves this into the ledger,
        // so a dismissal survives process death rather than being forgotten on a cold start.
        LiveUpdateRenderer.markDismissed(slipId)
    }

    companion object {
        const val ACTION_DISMISSED = "eu.feg.ambient.LIVE_UPDATE_DISMISSED"
        const val EXTRA_SLIP_ID = "slipId"
        private const val TAG = "DismissReceiver"
    }
}
