package eu.feg.ambient.ambient.narrator

import android.util.Log
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Gemini Nano, on device, through ML Kit's Prompt API.
 *
 * Every failure path — timeout, exception, unparseable output, guard rejection — falls back
 * to [fallback] with the same facts. [NarratedText.engine] is set to NANO only when the
 * model's own words survived, because the demo displays that badge and it must not lie.
 */
class NanoNarrator(
    private val fallback: Narrator,
    private val timeoutMillis: Long = 2_500,
) : Narrator {

    override suspend fun narrate(
        facts: MomentFacts,
        tone: Tone,
        language: NarratorLanguage,
    ): NarratedText {
        val started = System.nanoTime()
        val prompt = NanoPrompt.build(facts, tone, language)

        val raw: String? = try {
            withTimeout(timeoutMillis) {
                withContext(Dispatchers.IO) { generate(prompt) }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Nano generation failed, falling back", t)
            null
        }

        if (raw == null) return fallback.narrate(facts, tone, language)

        val parsed = NanoPrompt.parse(raw)
        if (parsed == null) {
            Log.d(TAG, "Nano output was unparseable, falling back")
            return fallback.narrate(facts, tone, language)
        }

        val candidate = NarratedText(
            headline = parsed.first,
            detail = parsed.second,
            engine = NarratorEngine.NANO,
            latencyMs = (System.nanoTime() - started) / 1_000_000,
        )

        val violation = NarratorGuard.violation(candidate)
        if (violation != null) {
            Log.d(TAG, "Nano output rejected by guard (" + violation + "), falling back")
            return fallback.narrate(facts, tone, language)
        }
        return candidate
    }

    /**
     * First inference loads the model and is slow, which is why [warmUp] exists. Never call
     * this from the main thread.
     */
    private suspend fun generate(prompt: String): String {
        val model = Generation.getClient()
        return model.generateContent(prompt).toString()
    }

    /**
     * One throwaway generation so the user never pays the cold-load cost on their lock
     * screen. Called from Application.onCreate on an IO dispatcher, never blocking onCreate.
     */
    suspend fun warmUp() {
        runCatching {
            withContext(Dispatchers.IO) {
                withTimeout(WARM_UP_TIMEOUT_MS) { generate(WARM_UP_PROMPT) }
            }
        }.onFailure { Log.d(TAG, "Warm-up skipped: " + it.message) }
    }

    private companion object {
        const val TAG = "NanoNarrator"
        const val WARM_UP_TIMEOUT_MS = 20_000L
        const val WARM_UP_PROMPT = "Reply with the single word: ready"
    }
}
