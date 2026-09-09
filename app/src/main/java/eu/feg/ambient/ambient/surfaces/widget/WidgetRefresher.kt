package eu.feg.ambient.ambient.surfaces.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll

/**
 * The five home-screen widgets, one per capability.
 *
 * Each carries its receiver, because two questions need it: which widget to redraw, and
 * whether anybody has actually placed one. The second is how the USE_WIDGET mission knows it
 * is complete -- the launcher tells nobody when a widget is added, so AppWidgetManager has to
 * be asked, and it is asked by component.
 */
enum class AmbientWidgetKind(val receiver: Class<out android.content.BroadcastReceiver>) {
    LIVE_SLIP(AmbientWidgetReceiver::class.java),
    MY_CLUB(ClubWidgetReceiver::class.java),
    DIGEST(DigestWidgetReceiver::class.java),
    SEASON(SeasonWidgetReceiver::class.java),
    PROTECTION(ProtectionWidgetReceiver::class.java),
    CLUB_MATCH(ClubMatchWidgetReceiver::class.java),
}

/**
 * One place that redraws widgets, so no caller has to know which of the five care about a
 * given change.
 *
 * WHY A SINGLE ENTRY POINT: the family shares state — the club themes all five, protection
 * silences all five, a slip changes two of them. Left to each caller, "which widgets does
 * this affect" would be answered five times and eventually answered wrong, and the failure
 * is invisible: a widget that quietly stops updating looks like a widget that has nothing to
 * say.
 *
 * updateAll on a widget nobody has added is not an error — it simply has no ids — so the
 * refreshers are safe to call unconditionally.
 */
object WidgetRefresher {

    /**
     * Whether any of the five is on a home screen right now.
     *
     * AppWidgetManager is the only party that knows: a widget being placed fires no broadcast
     * we can subscribe to, so this is polled at the moment somebody asks rather than
     * observed. Failures answer false -- on a device with no app widget host at all, getting
     * this wrong should cost a mission, not a crash.
     */
    fun anyPlaced(context: Context): Boolean = runCatching {
        val manager = android.appwidget.AppWidgetManager.getInstance(context) ?: return false
        AmbientWidgetKind.entries.any { kind ->
            manager.getAppWidgetIds(android.content.ComponentName(context, kind.receiver)).isNotEmpty()
        }
    }.getOrDefault(false)

    suspend fun refreshAll(context: Context) {
        AmbientWidgetKind.entries.forEach { refresh(context, it) }
    }

    suspend fun refresh(context: Context, which: AmbientWidgetKind) {
        val widget: GlanceAppWidget = when (which) {
            AmbientWidgetKind.LIVE_SLIP -> AmbientWidget()
            AmbientWidgetKind.MY_CLUB -> ClubWidget()
            AmbientWidgetKind.DIGEST -> DigestWidget()
            AmbientWidgetKind.SEASON -> SeasonWidget()
            AmbientWidgetKind.PROTECTION -> ProtectionWidget()
            AmbientWidgetKind.CLUB_MATCH -> ClubMatchWidget()
        }
        runCatching { widget.updateAll(context) }
            .onFailure { Log.w(TAG, "could not refresh " + which, it) }
    }

    private const val TAG = "WidgetRefresher"
}
