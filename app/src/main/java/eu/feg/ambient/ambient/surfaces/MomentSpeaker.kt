package eu.feg.ambient.ambient.surfaces

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/** What actually happened when a moment was asked for out loud. */
sealed interface SpeakResult {
    /** It was spoken, in the language asked for. */
    data object Spoken : SpeakResult

    /** The phone is on silent or in Do Not Disturb, so nothing was said. */
    data object Silenced : SpeakResult

    /**
     * Spoken, but not in the language asked for. [usedEnglish] is true when English stood in;
     * false means even English was missing and the device default was used.
     */
    data class LanguageFallback(val usedEnglish: Boolean) : SpeakResult

    /** No speech engine, no voice data, or the engine refused. */
    data class Unavailable(val reason: String) : SpeakResult
}

/**
 * Reads a moment aloud, on demand and never otherwise.
 *
 * ON-DEVICE ONLY. The platform TextToSpeech engine is used with no network synthesis: the
 * "nothing about your bet leaves this phone" claim has to hold for audio too, and a cloud
 * voice would quietly break it while sounding better. [SPEAK_ON_DEVICE] is what enforces it.
 *
 * NEVER AUTO-PLAYS. There is no code path into this class that is not a tap. Audio about
 * someone's bet, starting on its own in a room with other people in it, is a harm — it
 * discloses to bystanders something the customer chose to keep on a screen they control.
 * That is the kind of thing that gets a feature switched off across a product, so the rule
 * is structural: this class exposes one suspending call and nothing schedules it.
 *
 * One engine per process. TextToSpeech is expensive to build and holds a service binding, so
 * it is created once, lazily, off the main thread, and kept. [shutdown] releases it.
 */
class MomentSpeaker(private val context: Context) {

    private val mutex = Mutex()
    private val utteranceCounter = AtomicLong(0)

    @Volatile
    private var engine: TextToSpeech? = null

    /**
     * Ringer state is checked here rather than trusted to the audio stream.
     *
     * TTS plays on the music stream, which a silent ringer does not mute — so a phone face
     * down on a meeting table would happily read someone's slip out loud. Reading the ringer
     * and the interruption filter ourselves is the only way "on silent means silent" is true.
     */
    fun isSilenced(): Boolean {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val ringerSilent = audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL
        if (ringerSilent) return true

        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return false
        // UNKNOWN means we were not granted policy access; that is not a reason to speak
        // over someone, but it is also not evidence of DND, so the ringer check stands alone.
        val filter = runCatching { notifications.currentInterruptionFilter }
            .getOrDefault(NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /**
     * Speaks [text], or explains why it did not. Suspends until the utterance finishes so the
     * caller can record what happened rather than guessing.
     */
    suspend fun speak(text: String, language: NarratorLanguage): SpeakResult {
        if (text.isBlank()) return SpeakResult.Unavailable("nothing to say")
        if (isSilenced()) return SpeakResult.Silenced

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val tts = engine ?: create() ?: return@withContext UNAVAILABLE_ENGINE
                engine = tts

                val fallback = applyLanguage(tts, language)
                val utteranceId = "moment-" + utteranceCounter.incrementAndGet()

                val finished = awaitUtterance(tts, text, utteranceId)
                if (!finished) SpeakResult.Unavailable("engine refused the utterance")
                else fallback ?: SpeakResult.Spoken
            }
        }
    }

    /** Best effort, for the process going away. Android rarely calls onTerminate on a device. */
    fun shutdown() {
        runCatching { engine?.shutdown() }
        engine = null
    }

    // ---- engine ------------------------------------------------------------------------

    /** TextToSpeech reports readiness on a callback, so construction is a suspend point. */
    private suspend fun create(): TextToSpeech? = suspendCancellableCoroutine { cont ->
        var created: TextToSpeech? = null
        created = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                cont.resume(created)
            } else {
                Log.w(TAG, "TextToSpeech init failed with status " + status)
                runCatching { created?.shutdown() }
                cont.resume(null)
            }
        }
        cont.invokeOnCancellation { runCatching { created?.shutdown() } }
    }

    /**
     * Sets the voice, falling back rather than failing.
     *
     * A missing Croatian voice is the common case on a device set up in English, and silence
     * would be the worst answer to it: the customer tapped a speaker button and would get
     * nothing, with no way to tell whether the feature or their phone was broken. English is
     * a worse reading of a Croatian sentence than a Croatian voice, and a far better one
     * than no reading at all — and the caller is told, so the UI can say so.
     */
    private fun applyLanguage(tts: TextToSpeech, language: NarratorLanguage): SpeakResult? {
        val wanted = when (language) {
            NarratorLanguage.EN -> Locale.ENGLISH
            NarratorLanguage.HR -> Locale("hr")
        }
        if (isUsable(tts.isLanguageAvailable(wanted))) {
            tts.language = wanted
            return null
        }
        if (language != NarratorLanguage.EN && isUsable(tts.isLanguageAvailable(Locale.ENGLISH))) {
            tts.language = Locale.ENGLISH
            return SpeakResult.LanguageFallback(usedEnglish = true)
        }
        return SpeakResult.LanguageFallback(usedEnglish = false)
    }

    private fun isUsable(status: Int): Boolean =
        status == TextToSpeech.LANG_AVAILABLE ||
            status == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            status == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE

    /** Resumes when the utterance is done, so one tap cannot overlap the next. */
    private suspend fun awaitUtterance(
        tts: TextToSpeech,
        text: String,
        utteranceId: String,
    ): Boolean = suspendCancellableCoroutine { cont ->
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) = cont.resumeOnce(true)

            @Deprecated("Kept because the base class requires it.")
            override fun onError(id: String?) = cont.resumeOnce(false)

            override fun onError(id: String?, errorCode: Int) = cont.resumeOnce(false)
        })

        val params = android.os.Bundle().apply {
            // On-device synthesis only. Without this an engine is free to reach the network
            // for a better voice, which would make "nothing leaves the phone" untrue for the
            // one surface a customer cannot inspect.
            putBoolean(SPEAK_ON_DEVICE, true)
        }

        // QUEUE_FLUSH: a second tap replaces the first rather than queueing behind it.
        val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (queued != TextToSpeech.SUCCESS) cont.resumeOnce(false)

        cont.invokeOnCancellation { runCatching { tts.stop() } }
    }

    private fun CancellableContinuation<Boolean>.resumeOnce(value: Boolean) {
        if (isActive) resume(value)
    }

    private companion object {
        const val TAG = "MomentSpeaker"

        /**
         * TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS's opposite number. Named here
         * rather than used inline so the on-device rule is greppable.
         */
        const val SPEAK_ON_DEVICE = TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS

        val UNAVAILABLE_ENGINE: SpeakResult =
            SpeakResult.Unavailable("no speech engine on this device")
    }
}
