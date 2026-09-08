package eu.feg.ambient.ambient.surfaces.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import eu.feg.ambient.ambient.surfaces.AndroidSurfaceController
import eu.feg.ambient.ambient.surfaces.WidgetState

/**
 * The widget half of [AndroidSurfaceController.WidgetRendering].
 *
 * Save, then update, in that order and never the other way round: updateAll makes the
 * launcher call provideGlance, which reads the store. Updating first would redraw the widget
 * from the previous state and then leave it there until something else happened.
 *
 * The controller owns this through its interface, so nothing outside the surfaces package
 * ever holds a renderer — which is what lets step 13B's Moment Engine drop in unchanged.
 */
class WidgetRenderer(
    private val context: Context,
    private val store: WidgetStateStore = WidgetStateStore(context),
) : AndroidSurfaceController.WidgetRendering {

    override suspend fun render(state: WidgetState) {
        store.save(state)
        // A widget the customer has not added yet is not an error: updateAll simply has no
        // ids to update. Failing loudly here would put a crash in the demo's happy path.
        runCatching { AmbientWidget().updateAll(context) }
            .onFailure { Log.w(TAG, "widget update failed", it) }
    }

    private companion object {
        const val TAG = "WidgetRenderer"
    }
}
