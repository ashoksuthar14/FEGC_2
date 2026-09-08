package eu.feg.ambient.ambient.surfaces.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import eu.feg.ambient.ambient.identity.ClubTheme
import eu.feg.ambient.ambient.identity.ClubThemes
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
    /** N6: whose colours to draw in. Read per render, so a club change lands on the next one. */
    private val clubTheme: () -> ClubTheme = { ClubThemes.Default },
    private val store: WidgetStateStore = WidgetStateStore(context),
) : AndroidSurfaceController.WidgetRendering {

    /**
     * One render at a time. A club change and a live slip arriving within the same second
     * each did save-then-updateAll, and the launcher composed the two updates in the wrong
     * order: the store held Live while the home screen showed Idle. Serialising the pair
     * means the last state saved is always the last one the launcher is asked to draw.
     */
    private val renders = Mutex()

    override suspend fun render(state: WidgetState) = renders.withLock {
        // Stored next to the state, not read from user preferences at draw time. provideGlance
        // runs in whatever process the launcher wakes — often after a reboot, with nothing
        // else of ours alive — and it must not have to reach across to another store to find
        // out what colour the card is.
        store.saveClub(clubTheme().clubId)
        store.save(state)
        // A widget the customer has not added yet is not an error: updateAll simply has no
        // ids to update. Failing loudly here would put a crash in the demo's happy path.
        // The whole family, not just the Live Slip: the club, the digest and the protection
        // card all read from this same store, and a widget that quietly stops updating looks
        // exactly like a widget with nothing to say.
        WidgetRefresher.refreshAll(context)
        Unit
    }

    private companion object {
        const val TAG = "WidgetRenderer"
    }
}
