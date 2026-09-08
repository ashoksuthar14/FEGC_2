package eu.feg.ambient.ambient.narrator

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Gemma 3, on device, through LiteRT-LM — a model we ship rather than one the OS lends us.
 *
 * Every failure path ends at [fallback] with the same inputs, and the engine badge says
 * LOCAL_GEMMA only when this model's own words survived the guard.
 */
class LiteRtNarrator(
    private val holder: LiteRtEngineHolder,
    private val fallback: Narrator,
    /**
     * Generous on purpose. This runs on CPU, and a timeout here means abandoning a native
     * conversation rather than cancelling it, so it should fire only on a genuine hang.
     */
    private val timeoutMillis: Long = 15_000,
) : Narrator {

    override suspend fun narrate(
        facts: MomentFacts,
        tone: Tone,
        language: NarratorLanguage,
    ): NarratedText {
        val engine = holder.engine
        if (holder.state.value !is EngineState.Ready || engine == null) {
            return fallback.narrate(facts, tone, language)
        }

        val started = System.nanoTime()
        // The same prompt Nano would have been given — one builder, not two.
        val prompt = NanoPrompt.build(facts, tone, language)

        // A fresh conversation per moment: a goal must not carry context into a settlement.
        val conversation = runCatching { engine.createConversation() }.getOrNull()
            ?: return fallback.narrate(facts, tone, language)

        val raw: String? = try {
            val text = withTimeout(timeoutMillis) {
                withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    conversation.sendMessageAsync(prompt)
                        .catch { Log.w(TAG, "generation stream failed", it) }
                        .collect { chunk -> sb.append(chunk) }
                    sb.toString()
                }
            }
            // Only safe to close once the stream has actually finished. NonCancellable so a
            // cancelled caller cannot skip it and leak the conversation.
            withContext(NonCancellable) { runCatching { conversation.close() } }
            text
        } catch (t: Throwable) {
            // Deliberately NOT closing here. LiteRT's native callback thread may still be
            // mid-generation; closing underneath it dereferences freed state and takes the
            // whole process down with SIGSEGV, which no Kotlin catch can recover from.
            // Abandoning one conversation leaks a little native memory; it does not crash.
            Log.w(TAG, "Gemma generation failed or timed out, abandoning conversation", t)
            null
        }

        if (raw.isNullOrBlank()) return fallback.narrate(facts, tone, language)

        val parsed = NanoPrompt.parse(raw)
        if (parsed == null) {
            Log.d(TAG, "Gemma output was unparseable, falling back")
            return fallback.narrate(facts, tone, language)
        }

        val candidate = NarratedText(
            headline = parsed.first,
            detail = parsed.second,
            engine = NarratorEngine.LOCAL_GEMMA,
            latencyMs = (System.nanoTime() - started) / 1_000_000,
        )

        val violation = NarratorGuard.violation(candidate)
        if (violation != null) {
            Log.d(TAG, "Gemma output rejected by guard (" + violation + "), falling back")
            return fallback.narrate(facts, tone, language)
        }
        return candidate
    }

    private companion object {
        const val TAG = "LiteRtNarrator"
    }
}
