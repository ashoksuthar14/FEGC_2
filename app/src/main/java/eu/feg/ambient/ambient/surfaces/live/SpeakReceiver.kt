package eu.feg.ambient.ambient.surfaces.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import eu.feg.ambient.AmbientApp
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.surfaces.SpeakResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The "Listen" action on the lock-screen card.
 *
 * A broadcast receiver rather than an activity, deliberately. The whole claim of this surface
 * is that the useful thing happens without opening the app; making a blind customer unlock a
 * phone and wait out a cold start to hear one sentence would be a worse experience than the
 * one we are replacing, not a better one. This also means Listen works from a locked screen.
 *
 * The sentence travels in the intent rather than being looked up. It is the text the card is
 * already displaying, refreshed on every post through FLAG_UPDATE_CURRENT, so what is read
 * out and what is on the screen cannot drift apart.
 */
class SpeakReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SPEAK) return
        val app = context.applicationContext as? AmbientApp ?: return
        val text = intent.getStringExtra(EXTRA_TEXT)
        val surface = runCatching {
            Surface.valueOf(intent.getStringExtra(EXTRA_SURFACE) ?: Surface.LIVE_UPDATE.name)
        }.getOrDefault(Surface.LIVE_UPDATE)

        // goAsync would hold the broadcast open for the length of the utterance; a scope is
        // cheaper and the result is reported by voice or toast, not by the broadcast.
        scope.launch {
            val result = app.container.spokenMoments.speak(text, surface)
            Log.i(TAG, "listen tapped: " + result)
            announce(context, result)
        }
    }

    /**
     * The one case that needs a visible answer: nothing was said, and the customer needs to
     * know it was their phone's silent switch and not our button that failed.
     */
    private fun announce(context: Context, result: SpeakResult) {
        val message = when (result) {
            is SpeakResult.Silenced -> "Phone is on silent"
            is SpeakResult.Unavailable -> "Cannot read this out right now"
            else -> return
        }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_SPEAK = "eu.feg.ambient.SPEAK_MOMENT"
        const val EXTRA_TEXT = "ambient.speakText"
        const val EXTRA_SURFACE = "ambient.speakSurface"

        private const val TAG = "SpeakReceiver"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
