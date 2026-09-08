package eu.feg.ambient.ambient.surfaces.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll

/** The five home-screen widgets, one per capability. */
enum class AmbientWidgetKind {
    LIVE_SLIP,
    MY_CLUB,
    DIGEST,
    SEASON,
    PROTECTION,
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
        }
        runCatching { widget.updateAll(context) }
            .onFailure { Log.w(TAG, "could not refresh " + which, it) }
    }

    private const val TAG = "WidgetRefresher"
}
