package eu.feg.ambient.ambient.surfaces

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import eu.feg.ambient.R
import eu.feg.ambient.ambient.surfaces.notifications.Channels

/**
 * Keeps the Live Update alive while the app is in the background.
 *
 * A backgrounded Android app is frozen, and a lock-screen card that stops moving is worse
 * than no card at all — so the tick lives in a foreground service. Type is `dataSync`, which
 * covers a 90-minute match; Android 15 applies a daily budget to it. In production FEG would
 * agree the final type with Google, and that is worth saying out loud rather than hiding.
 *
 * `shortService` was rejected: it is capped at a few minutes, which is shorter than a half.
 */
class LiveSlipService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Channels.ensure(this)
        val slipId = intent?.getStringExtra(EXTRA_SLIP_ID)
        Log.i(TAG, "foreground service started for slip " + slipId)

        // A placeholder until the renderer posts the real one under the same id; the system
        // requires a notification the moment we go foreground.
        startForeground(NOTIFICATION_ID, placeholder())
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "foreground service stopped")
        super.onDestroy()
    }

    private fun placeholder(): Notification =
        NotificationCompat.Builder(this, Channels.LIVE_SLIP)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Following your slip")
            .setOngoing(true)
            .build()

    companion object {
        const val NOTIFICATION_ID = 4301
        const val EXTRA_SLIP_ID = "slipId"
        private const val TAG = "LiveSlipService"

        fun start(context: Context, slipId: String) {
            val intent = Intent(context, LiveSlipService::class.java)
                .putExtra(EXTRA_SLIP_ID, slipId)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveSlipService::class.java))
        }
    }
}
