package eu.feg.ambient

import android.app.Application
import eu.feg.ambient.core.AppContainer

/**
 * Holds the single [AppContainer]. No DI framework — plain constructors, per PRD section 2.3.
 */
class AmbientApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Off the main thread by construction — the first inference can take many seconds
        // and onCreate must never block on it.
        container.warmUpNarrator()
    }
}
