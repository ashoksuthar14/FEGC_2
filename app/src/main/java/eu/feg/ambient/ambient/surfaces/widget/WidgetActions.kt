package eu.feg.ambient.ambient.surfaces.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * The five things a customer can do from the widget.
 *
 * Every one of them is a callback rather than an activity launch, because the point of the
 * widget is that the useful action does not require opening the app. They are separate
 * classes rather than one class with a parameter: the manifest-free ActionCallback dispatch
 * is by class name, and a parameter would mean an unlabelled string in every call site.
 *
 * The bodies are deliberately thin. Step 13C owns the learning router and step 13B owns the
 * moment engine; when those exist these forward to them. Until then each logs, so the Surface
 * Lab can prove the tap arrived rather than leaving us guessing at a silent widget.
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
        // TODO(step 13B): mute this slip for the rest of the session.
        Log.i(TAG, "mute this match tapped")
    }

    private companion object { const val TAG = "WidgetAction.Mute" }
}

/**
 * "That was worth telling me."
 *
 * TODO(step 13C): write to the ledger's feedback table. The learning router reads these to
 * decide which moment types are worth a surface for this customer, which is why the signal
 * has to be one tap on the surface itself rather than a survey inside the app.
 */
class ThumbsUpAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        recordFeedback(positive = true)
    }
}

/** "That was not worth telling me." Same TODO as [ThumbsUpAction]. */
class ThumbsDownAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        recordFeedback(positive = false)
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

private fun recordFeedback(positive: Boolean) {
    // TODO(step 13C): LearningRouter.record(momentId, positive).
    Log.i("WidgetAction.Feedback", "feedback positive=" + positive)
}
