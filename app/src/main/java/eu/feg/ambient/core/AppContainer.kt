package eu.feg.ambient.core

import android.content.Context
import eu.feg.ambient.ambient.narrator.LadderNarrator
import eu.feg.ambient.ambient.narrator.NanoAvailabilityChecker
import eu.feg.ambient.ambient.narrator.NanoNarrator
import eu.feg.ambient.ambient.narrator.NanoState
import eu.feg.ambient.ambient.narrator.Narrator
import eu.feg.ambient.ambient.narrator.TemplateNarrator
import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.clock.SystemMatchClock
import eu.feg.ambient.data.mock.MockDataSource
import eu.feg.ambient.data.repo.BetRepository
import eu.feg.ambient.data.repo.MatchRepository
import eu.feg.ambient.data.repo.UserStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Plain constructors instead of a DI framework (PRD section 2.3). Held by the Application,
 * so the tick and the repositories outlive any single screen.
 */
class AppContainer(context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Public so the UI can read the static content files directly. */
    val source = MockDataSource.fromContext(context)

    /** Declared as the interface, never the impl — Phase 2 swaps this line alone. */
    val clock: MatchClock = SystemMatchClock()

    val matchRepository = MatchRepository(source, clock, appScope)

    val betRepository = BetRepository(clock)

    val userStateRepository = UserStateRepository(
        context.getSharedPreferences("ambient", Context.MODE_PRIVATE),
    )

    // --- narrator (Phase 2, step 12) --------------------------------------------------

    val nanoAvailability = NanoAvailabilityChecker(appScope)

    private val templateNarrator = TemplateNarrator()

    private val nanoNarrator = NanoNarrator(fallback = templateNarrator)

    /**
     * Rebuilt whenever the probe result changes: Nano first once the device says it is
     * available, templates alone otherwise. `ui/` reaches this through the container and
     * never imports `ambient/` directly.
     */
    @Volatile
    var narrator: Narrator = LadderNarrator(listOf(templateNarrator))
        private set

    /** The template rung alone, for the Narrator Lab's side-by-side comparison. */
    val templateOnly: Narrator = templateNarrator

    /** Null until the device reports Nano available; the Lab shows a notice in that case. */
    val nanoOrNull: Narrator?
        get() = if (nanoAvailability.state.value is NanoState.Available) nanoNarrator else null

    init {
        nanoAvailability.check()
        appScope.launch {
            nanoAvailability.state.collect { state ->
                narrator = if (state is NanoState.Available) {
                    LadderNarrator(listOf(nanoNarrator, templateNarrator))
                } else {
                    LadderNarrator(listOf(templateNarrator))
                }
            }
        }
    }

    /**
     * Pre-warms the model off the main thread. First inference loads it and can take many
     * seconds; the user must not pay that on a lock screen, and onCreate must never block.
     */
    fun warmUpNarrator() {
        appScope.launch {
            if (nanoAvailability.state.value is NanoState.Available) {
                nanoNarrator.warmUp()
            }
        }
    }

    companion object {
        const val ML_KIT_GENAI_VERSION = "com.google.mlkit:genai-prompt:1.0.0-beta2"
    }
}
