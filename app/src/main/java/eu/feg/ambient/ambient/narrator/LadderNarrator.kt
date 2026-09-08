package eu.feg.ambient.ambient.narrator

import android.util.Log

/**
 * Tries each narrator in turn and returns the first result that clears [NarratorGuard].
 *
 * AppContainer builds this as [NanoNarrator, TemplateNarrator] when Nano is available and
 * [TemplateNarrator] otherwise. Nothing else in the app knows or cares which rung ran —
 * the truth is carried in [NarratedText.engine].
 */
class LadderNarrator(private val rungs: List<Narrator>) : Narrator {

    init {
        require(rungs.isNotEmpty()) { "a ladder needs at least one rung" }
    }

    override suspend fun narrate(
        facts: MomentFacts,
        tone: Tone,
        language: NarratorLanguage,
    ): NarratedText {
        rungs.dropLast(1).forEach { rung ->
            val result = runCatching { rung.narrate(facts, tone, language) }.getOrNull()
            if (result != null) {
                val violation = NarratorGuard.violation(result)
                if (violation == null) return result
                Log.d(TAG, rung.javaClass.simpleName + " rejected by guard: " + violation)
            }
        }
        // The last rung is the template, which cannot fail; its output is already clamped.
        return rungs.last().narrate(facts, tone, language)
    }

    private companion object {
        const val TAG = "Narrator"
    }
}
