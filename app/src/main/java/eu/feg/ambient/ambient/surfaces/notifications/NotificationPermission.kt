package eu.feg.ambient.ambient.surfaces.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * POST_NOTIFICATIONS, asked at the right moment.
 *
 * Never at launch. On Android a denied notification permission is close to permanent, and
 * opt-in retention is one of the things being judged — so the ask happens after a live bet
 * is placed, phrased as "track this slip on your lock screen?", where the user has just
 * demonstrated they want exactly that.
 */
object NotificationPermission {

    const val RATIONALE_TITLE = "Track this slip on your lock screen?"

    const val RATIONALE_BODY =
        "We will show the score and how your picks are doing, and nothing else. " +
            "No offers, no amounts. One notification, only while the match is on."

    fun isGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Whether the system will promote an ongoing notification for us. Separate from the
     * runtime permission: a user can allow notifications and still disable promotion.
     */
    fun canPostPromoted(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching {
            NotificationManager::class.java
                .getMethod("canPostPromotedNotifications")
                .invoke(manager) as? Boolean
        }.getOrNull() ?: false
    }

    /** Where to send the user when promotion is off but notifications are allowed. */
    const val PROMOTED_SETTINGS_ACTION = "android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS"
}
