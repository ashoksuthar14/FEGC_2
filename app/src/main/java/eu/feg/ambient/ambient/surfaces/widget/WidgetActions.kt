package eu.feg.ambient.ambient.surfaces.widget

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import eu.feg.ambient.AmbientApp
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.engine.ledger.LedgerEntry
import eu.feg.ambient.ambient.engine.router.RewardTable
import eu.feg.ambient.ambient.surfaces.SpeakResult
import eu.feg.ambient.ambient.surfaces.WidgetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The five things a customer can do from the widget.
 *
 * Every one of them is a callback rather than an activity launch, because the point of the
 * widget is that the useful action does not require opening the app. They are separate
 * classes rather than one class with a parameter: the manifest-free ActionCallback dispatch
 * is by class name, and a parameter would mean an unlabelled string in every call site.
 *
 * The bodies stay thin: each one grades a ledger row through the engine and then redraws the
 * card. The judgement lives in the router, not here.
 */

/** Ask to be told when this match kicks off. Offered on PreMatch. */
class RemindMeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // TODO(step 13B): schedule the kickoff moment through the Moment Engine.
        Log.i(TAG, "remind me tapped")
    }

    private companion object { const val TAG = "WidgetAction.Remind" }
}

/**
 * Stop talking about this match — the one-tap way out of a surface that has become noise.
 *
 * Offered on Live, where it is the honest alternative to swiping the widget away, and it is
 * the action the alert budget exists to make rare.
 */
class MuteMatchAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        applyMute(context)
    }

    private companion object { const val TAG = "WidgetAction.Mute" }
}

/**
 * "That was worth telling me."
 *
 * The learning router reads these to decide which moment types are worth a surface for this
 * customer, which is why the signal has to be one tap on the surface itself rather than a
 * survey buried inside the app.
 */
class ThumbsUpAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        applyThumbs(context, positive = true)
    }
}

/** "That was not worth telling me." The mirror of [ThumbsUpAction]. */
class ThumbsDownAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        applyThumbs(context, positive = false)
    }
}

/**
 * Read this card out loud.
 *
 * The slip comes from the stored snapshot, which is what the card was drawn from, so the
 * sentence and the pixels cannot disagree. Protection is re-checked inside [SpokenMoments]:
 * a widget snapshot outlives the decision that produced it, and audio is the surface where
 * a stale one would matter most.
 */
class SpeakWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val container = app(context)?.container ?: return
        val store = WidgetStateStore(context)
        val slip = when (val state = store.load()) {
            is WidgetState.Live -> state.slip
            is WidgetState.Settled -> state.slip
            else -> null
        }
        // A digest or a recap holds no slip; it speaks its own stored sentence instead.
        val result = if (slip != null) {
            container.spokenMoments.speakSlip(slip, Surface.WIDGET)
        } else {
            val text = store.storedSpoken()
            if (text == null) {
                Log.i(TAG_SPEAK, "speak tapped with nothing to say")
                return
            }
            container.spokenMoments.speak(text, Surface.WIDGET, "RECAP")
        }
        Log.i(TAG_SPEAK, "speak tapped: " + result)

        // Silence needs an answer, or the button looks broken rather than respectful.
        val message = when (result) {
            is SpeakResult.Silenced -> "Phone is on silent"
            is SpeakResult.Unavailable -> "Cannot read this out right now"
            is SpeakResult.LanguageFallback ->
                if (result.usedEnglish) "No Croatian voice — read in English" else null
            is SpeakResult.Spoken -> null
        }
        if (message != null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * The way out, always reachable while protection is active.
 *
 * TODO(step 13A): open the protection sheet — self-exclusion, limits, the helpline. It is a
 * callback and not a deep link on purpose: the moment a customer wants this, the last thing
 * that should stand between them and it is an app cold start.
 */
class PanicAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        Log.i(TAG, "panic tapped")
    }

    private companion object { const val TAG = "WidgetAction.Panic" }
}

/**
 * A thumb does two things: it teaches the router, and it visibly acknowledges itself.
 *
 * The second half is not decoration. A control that looks unchanged after it is tapped reads
 * as a broken widget, and a customer who believes the gesture did nothing will not use it
 * again — which costs us the cheapest signal we have about whether a moment was worth sending.
 *
 * The reward is applied to the ledger row that actually produced this card, not to whichever
 * arm currently looks best: the point of the gesture is to grade the decision that was made.
 */
internal suspend fun applyThumbs(context: Context, positive: Boolean) {
    val entry = lastShownEntry(context)
    if (entry != null) {
        app(context)?.container?.engine?.onThumbs(entry.id, up = positive)
    }
    acknowledge(context, if (positive) FeedbackMark.UP else FeedbackMark.DOWN)
    Log.i(TAG_THUMBS, "thumbs " + (if (positive) "up" else "down") + " on " + entry?.armId)
}

/**
 * A mute is the strongest negative we take: it punishes every speaking arm in this context at
 * once, because the customer is not objecting to a tone, they are objecting to being spoken to
 * here at all.
 */
internal suspend fun applyMute(context: Context) {
    val entry = lastShownEntry(context)
    if (entry != null) {
        app(context)?.container?.router?.rewardAllInContext(entry.contextBucket, RewardTable.MUTED)
        app(context)?.container?.ledger?.markRewarded(entry.id, RewardTable.MUTED)
    }
    acknowledge(context, FeedbackMark.MUTED)
    Log.i("WidgetAction.Mute", "muted context=" + entry?.contextBucket)
}

private fun app(context: Context): AmbientApp? = context.applicationContext as? AmbientApp

/**
 * The most recent row that reached a surface. Anything older is a decision the customer is
 * not looking at, and rewarding that would teach the router the wrong lesson.
 */
private fun lastShownEntry(context: Context): LedgerEntry? =
    app(context)?.container?.ledger?.entries?.value?.firstOrNull { it.shownAt != null }

/** Record the gesture against the card on screen and redraw so the buttons give way. */
private suspend fun acknowledge(context: Context, mark: FeedbackMark) {
    WidgetStateStore(context).setFeedback(mark)
    AmbientWidget().updateAll(context)
}

private const val TAG_THUMBS = "WidgetAction.Thumbs"
private const val TAG_SPEAK = "WidgetAction.Speak"
