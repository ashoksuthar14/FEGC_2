package eu.feg.ambient.ambient.narrator

import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
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
    /** Generous on purpose: this runs on the CPU, and the call itself blocks. */
    private val timeoutMillis: Long = 20_000,
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

        val raw: String? = try {
            withTimeout(timeoutMillis) {
                // One thread for every native call. Creating a conversation on one thread and
                // generating on another is not safe with this runtime.
                withContext(holder.nativeDispatcher) {
                    // A fresh conversation per moment, capped: a goal must not carry context
                    // into a settlement, and an uncapped small model will generate until it
                    // exhausts the context window.
                    val conversation = engine.createConversation(
                        ConversationConfig(maxOutputToken = LiteRtEngineHolder.MAX_OUTPUT_TOKENS),
                    )
                    try {
                        // Blocking sendMessage, deliberately, not sendMessageAsync.
                        //
                        // sendMessageAsync in litertlm 0.16.1 was built against an older
                        // kotlinx-coroutines: its completion callback calls
                        // SendChannel.close$default, which no longer exists in 1.10.x. That
                        // throws NoSuchMethodError on the runtime's own raw callback thread,
                        // where no catch of ours can reach it, and the process dies. The
                        // synchronous call touches no channels and has no such dependency.
                        conversation.sendMessage(prompt).textOrNull()
                    } finally {
                        // Same thread as creation and generation, after the call returned.
                        runCatching { conversation.close() }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Gemma generation failed or timed out", t)
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
            // A model that skipped the third line still gets to keep the first two; the
            // spoken variant is composed from the same facts instead.
            spokenText = NanoPrompt.parseSpoken(raw)
                ?: SpokenLines.compose(facts, tone, language),
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

    /** A reply can carry several parts; only the text ones matter to us. */
    private fun Message.textOrNull(): String? =
        contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
            .takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "LiteRtNarrator"
    }
}
