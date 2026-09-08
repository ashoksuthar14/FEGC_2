package eu.feg.ambient

import android.app.Application
import eu.feg.ambient.ambient.surfaces.DemoStage
import eu.feg.ambient.core.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        container.kickoffMomentSource.start()
        // Registered from the Application because ACTION_USER_PRESENT cannot be declared in
        // the manifest. It only reaches us while this process happens to be alive, which is
        // why it is an enhancement and the widget is the trigger.
        container.unlockWatcher.register()
        // A cold start leaves every ambient surface empty, because every one of them is
        // downstream of a placed slip. DemoStage places one and reports three fixtures to the
        // engine, so the lock screen, the widget family and the catch-up all have something
        // true on them before anyone taps anything. It stands down if a real slip exists.
        if (BuildConfig.DEBUG) {
            val demoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            demoScope.launch { DemoStage.arm(container) }
            // And again whenever its matches reach full time, so the card keeps a match on it
            // rather than a final score for the rest of the demo.
            DemoStage.keepArmed(container, demoScope)
        }
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
