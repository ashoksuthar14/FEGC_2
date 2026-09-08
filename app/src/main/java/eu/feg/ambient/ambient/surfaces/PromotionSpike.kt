package eu.feg.ambient.ambient.surfaces

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import eu.feg.ambient.R

/**
 * Step 14.0c: does this device actually promote an ongoing notification to the lock screen?
 *
 * Everything else in step 14 is shaped by the answer, so this posts one notification that
 * meets every documented promotion requirement and then reads it back to see what the system
 * really did — rather than trusting that asking for promotion granted it.
 *
 * Temporary. Delete once the answer is recorded.
 */
object PromotionSpike {

    const val TAG = "PromotionSpike"
    private const val CHANNEL_ID = "spike"
    private const val NOTIFICATION_ID = 4201

    fun run(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        // IMPORTANCE_MIN would disqualify the notification from promotion.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Live Update spike", NotificationManager.IMPORTANCE_DEFAULT),
        )

        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        Log.i(TAG, "android=" + Build.VERSION.SDK_INT + " postNotificationsGranted=" + granted)
        if (!granted) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted — cannot post, nothing to measure")
            return
        }

        val notification = build(context)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)

        // Read back what the system actually holds, not what we asked for.
        val posted = manager.activeNotifications.firstOrNull { it.id == NOTIFICATION_ID }?.notification
        val promotedFlag = posted?.let { (it.flags and FLAG_PROMOTED_ONGOING) != 0 }

        Log.i(TAG, "canPostPromotedNotifications=" + canPostPromoted(manager))
        Log.i(TAG, "hasPromotableCharacteristics=" + hasPromotable(notification))
        Log.i(TAG, "FLAG_PROMOTED_ONGOING set on posted notification=" + promotedFlag)
        Log.i(TAG, "posted=" + (posted != null) + " flags=" + posted?.flags)
    }

    private fun build(context: Context): Notification {
        // One segment per leg of a three-leg slip: two won, one still running.
        val style = NotificationCompat.ProgressStyle()
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(1).setColor(GREEN),
                    NotificationCompat.ProgressStyle.Segment(1).setColor(GREEN),
                    NotificationCompat.ProgressStyle.Segment(1).setColor(GREY),
                ),
            )
            .setProgressPoints(listOf(NotificationCompat.ProgressStyle.Point(1).setColor(BLUE)))
            .setProgress(2)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Liverpool 1–0 · 61'")
            .setContentText("Your Liverpool win pick is still alive. 29 minutes left.")
            .setStyle(style)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText("2/3 · 61'")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            // Deliberately absent: custom RemoteViews, setGroupSummary, setColorized —
            // each of them silently disqualifies the notification from promotion.
            .build()
    }

    /** Reflective so the spike still compiles and runs if the SDK predates the API. */
    private fun canPostPromoted(manager: NotificationManager): String = runCatching {
        NotificationManager::class.java
            .getMethod("canPostPromotedNotifications")
            .invoke(manager)
            .toString()
    }.getOrElse { "unavailable (" + it.javaClass.simpleName + ")" }

    private fun hasPromotable(notification: Notification): String = runCatching {
        Notification::class.java
            .getMethod("hasPromotableCharacteristics")
            .invoke(notification)
            .toString()
    }.getOrElse { "unavailable (" + it.javaClass.simpleName + ")" }

    /** Notification.FLAG_PROMOTED_ONGOING, inlined so this builds against older SDKs too. */
    private const val FLAG_PROMOTED_ONGOING = 1 shl 21

    private const val GREEN = 0xFF3BC66B.toInt()
    private const val GREY = 0xFF3B3B43.toInt()
    private const val BLUE = 0xFF1852BE.toInt()
}
