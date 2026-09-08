package eu.feg.ambient.ambient.surfaces.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * Three channels rather than one, so a user can mute the offers without losing the match
 * updates they asked for. Muting a whole app is the outcome we are trying to avoid.
 */
object Channels {

    /**
     * The Live Update itself. IMPORTANCE_DEFAULT is a hard requirement — IMPORTANCE_MIN
     * silently disqualifies a notification from being promoted to the lock screen.
     */
    const val LIVE_SLIP = "live_slip"

    /** The rare settlement alert; the only one allowed to interrupt. */
    const val SETTLEMENT = "settlement"

    /** The unlock digest: present, never interrupting. */
    const val DIGEST = "digest"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                LIVE_SLIP,
                "Live bet slips",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Score and progress for a slip you have running."
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                SETTLEMENT,
                "Settlement",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "One alert when a slip you are following is decided."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                DIGEST,
                "Catch-up",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "What happened while you were away. Never makes a sound."
            },
        )
    }
}
