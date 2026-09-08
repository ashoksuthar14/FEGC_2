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
        // Bet placement, the match clock and protection changes now drive the surfaces.
        container.surfaceCoordinator.start()
        // Registered from the Application because ACTION_USER_PRESENT cannot be declared in
        // the manifest. It only reaches us while this process happens to be alive, which is
        // why it is an enhancement and the widget is the trigger.
        container.unlockWatcher.register()
        if (BuildConfig.DEBUG) container.logNarratorSelfTest()
    }

    /**
     * Releases the speech engine's service binding.
     *
     * Best effort by design: Android does not call this on a real device, it kills the
     * process instead — which releases the binding anyway. It is here so the emulator and
     * instrumentation runs, where onTerminate is called, do not leak an engine per run.
     */
    override fun onTerminate() {
        container.momentSpeaker.shutdown()
        super.onTerminate()
    }
}
