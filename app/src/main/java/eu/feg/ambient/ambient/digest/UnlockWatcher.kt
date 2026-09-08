package eu.feg.ambient.ambient.digest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The unlock, when we are lucky enough to hear about it.
 *
 * ACTION_USER_PRESENT CANNOT BE DECLARED IN THE MANIFEST. Since Android 8 it is subject to the
 * implicit-broadcast restrictions and it is not on the exemption list, so a manifest entry is
 * silently dead — which is the worst possible failure mode, because it works in a debug build
 * with the app on screen and fails on stage. It reaches a receiver registered at runtime from a
 * living process, and only then.
 *
 * WHICH MEANS THIS IS AN ENHANCEMENT, NOT THE TRIGGER. The situation a catch-up exists for is
 * precisely the one where our process has been frozen or killed for hours, and a frozen process
 * receives nothing. So the digest is decided by the widget — which the launcher redraws whether
 * or not we are alive — and this receiver only *upgrades* the moment when the process happens
 * to still be up. Anything that depends on this firing is a feature that demos and does not
 * ship.
 *
 * Registering is cheap and idempotent here, so the Application registers on start and the
 * live-slip service registers while it runs; a slip in flight is the one time the process is
 * reliably alive, and the registration costs nothing on top of a service that already exists.
 */
class UnlockWatcher(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onUnlock: suspend () -> Unit,
) {

    private var receiver: BroadcastReceiver? = null

    fun register() {
        if (receiver != null) return
        val created = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != Intent.ACTION_USER_PRESENT) return
                // Logged unconditionally, because "did the unlock reach us" is a question we
                // will keep asking on new devices and battery settings, and the answer has to
                // be readable from logcat rather than guessed at.
                Log.i(TAG, "ACTION_USER_PRESENT reached a live process")
                scope.launch { onUnlock() }
            }
        }
        runCatching {
            context.registerReceiver(created, IntentFilter(Intent.ACTION_USER_PRESENT))
            receiver = created
            Log.i(TAG, "unlock watcher registered")
        }.onFailure { Log.w(TAG, "could not register unlock watcher", it) }
    }

    fun unregister() {
        val current = receiver ?: return
        runCatching { context.unregisterReceiver(current) }
        receiver = null
    }

    private companion object {
        const val TAG = "UnlockWatcher"
    }
}
