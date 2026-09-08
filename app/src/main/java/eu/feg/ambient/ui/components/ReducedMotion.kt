package eu.feg.ambient.ui.components

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * True when the user has asked the system to remove animations (Settings → Accessibility →
 * "Remove animations", which sets the animator duration scale to 0).
 *
 * Components use this to pick a *static* alternative — a solid live dot instead of a pulse,
 * no carousel auto-advance, colours that change instantly — rather than a slower animation.
 * A slower pulse is still a pulse for someone with a vestibular disorder; the setting means
 * "do not move", and Compose's own animation clock does not honour it on its own.
 *
 * The value is read once per composition rather than observed: Android restarts activities
 * when this setting changes, so a one-off read is enough and costs nothing on every frame.
 */
@Composable
fun reducedMotion(): Boolean {
    // Previews have no settings provider, so they render the animated variant.
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) == 0f
    }
}
