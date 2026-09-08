package eu.feg.ambient.core

import android.content.Context
import android.util.Log
import eu.feg.ambient.ambient.narrator.EngineState
import eu.feg.ambient.ambient.narrator.LadderNarrator
import eu.feg.ambient.ambient.narrator.LiteRtEngineHolder
import eu.feg.ambient.ambient.narrator.LiteRtNarrator
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.Tone
import eu.feg.ambient.ambient.narrator.NanoAvailabilityChecker
import eu.feg.ambient.ambient.narrator.NanoNarrator
import eu.feg.ambient.ambient.narrator.NanoState
import eu.feg.ambient.ambient.narrator.Narrator
import eu.feg.ambient.ambient.narrator.TemplateNarrator
import eu.feg.ambient.ambient.surfaces.AndroidSurfaceController
import eu.feg.ambient.ambient.surfaces.DemoSurfaceData
import eu.feg.ambient.ambient.surfaces.SurfaceController
import eu.feg.ambient.ambient.surfaces.live.LiveUpdateRenderer
import eu.feg.ambient.ambient.surfaces.widget.WidgetRenderer
import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.clock.SystemMatchClock
import eu.feg.ambient.data.mock.MockDataSource
import eu.feg.ambient.data.repo.BetRepository
import eu.feg.ambient.data.repo.MatchRepository
import eu.feg.ambient.data.repo.UserStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

    /** Path B: a Gemma 3 model we ship ourselves, independent of AICore entirely. */
    val liteRtEngine = LiteRtEngineHolder(context, appScope)

    private val liteRtNarrator = LiteRtNarrator(liteRtEngine, fallback = templateNarrator)

    /**
     * Whether the local Gemma rung may run.
     *
     * This was off while the first generation terminated the process. The cause turned out
     * to be ours: an uncapped output token budget and native calls spread across threads.
     * With both fixed it is on, and the diagnostics screen can still disable it if a device
     * misbehaves — process death is the one failure the ladder cannot catch.
     */
    @Volatile
    var localGenerationEnabled: Boolean = true

    /**
     * Built on every access from the current probe results: Nano first when the OS has it,
     * then our own Gemma, then templates.
     *
     * Computed rather than cached deliberately. A cached field rebuilt by a collector left a
     * window between "engine became Ready" and "ladder rebuilt", during which the very first
     * narration silently fell back to templates. Wrapping a list is free; a race is not.
     */
    val narrator: Narrator
        get() = LadderNarrator(
            buildList {
                if (nanoAvailability.state.value is NanoState.Available) add(nanoNarrator)
                if (localGenerationEnabled && liteRtEngine.state.value is EngineState.Ready) {
                    add(liteRtNarrator)
                }
                add(templateNarrator)
            },
        )

    /** The template rung alone, for the Narrator Lab's side-by-side comparison. */
    val templateOnly: Narrator = templateNarrator

    /** Null until the device reports Nano available; the Lab shows a notice in that case. */
    val nanoOrNull: Narrator?
        get() = if (nanoAvailability.state.value is NanoState.Available) nanoNarrator else null

    /** Null unless the local model is loaded and generation has been explicitly enabled. */
    val localGemmaOrNull: Narrator?
        get() = if (localGenerationEnabled && liteRtEngine.state.value is EngineState.Ready) {
            liteRtNarrator
        } else {
            null
        }

    init {
        nanoAvailability.check()
        liteRtEngine.initialize()
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

    // --- surfaces (Phase 2, step 14) ---------------------------------------------------

    /**
     * The single door to every OS surface. The Surface Lab drives it today; step 13B's
     * Moment Engine calls the same methods without any renderer changing.
     *
     * The renderers are attached here rather than constructed inside the controller, so the
     * controller stays testable and knows nothing about notifications or Glance.
     */
    val surfaceController: SurfaceController = AndroidSurfaceController(context).apply {
        liveUpdateRenderer = LiveUpdateRenderer(context)
        widgetRenderer = WidgetRenderer(context)
    }

    /** Ready-made slips so the surfaces have something real to show before the engine exists. */
    val demoData = DemoSurfaceData

    /**
     * Debug-only: once the local model is ready, narrate a few moments and log the result.
     * The Narrator Lab does this interactively, but a lock screen should not stand between
     * us and a latency number.
     */
    fun logNarratorSelfTest() {
        appScope.launch {
            if (!localGenerationEnabled) return@launch
            liteRtEngine.state.first { it is EngineState.Ready }

            val cases = listOf(
                Triple(MomentType.GOAL_ON_SLIP, Tone.PLAIN, NarratorLanguage.EN),
                Triple(MomentType.GOAL_ON_SLIP, Tone.WITTY, NarratorLanguage.EN),
                Triple(MomentType.AWAY_DIGEST, Tone.PLAIN, NarratorLanguage.EN),
            )
            cases.forEach { (type, tone, language) ->
                // Spaced out: LiteRT holds native state per conversation, and running them
                // back to back on a memory-pressured phone gets the process killed.
                delay(1_500)
                val started = System.currentTimeMillis()
                val result = narrator.narrate(eu.feg.ambient.ui.lab.sampleFacts(type), tone, language)
                val wall = System.currentTimeMillis() - started
                Log.i(
                    SELF_TEST_TAG,
                    type.name + " / " + tone.name + " / " + language.name +
                        " | engine=" + result.engine.name + " | " + wall + "ms" +
                        " | H: " + result.headline + " | D: " + result.detail,
                )
            }

        }
    }

    companion object {
        const val ML_KIT_GENAI_VERSION = "com.google.mlkit:genai-prompt:1.0.0-beta2"
        const val SELF_TEST_TAG = "NarratorSelfTest"
    }
}
