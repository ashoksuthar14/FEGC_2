package eu.feg.ambient.ambient.loyalty

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import eu.feg.ambient.MainActivity
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.identity.CrestBitmap
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.surfaces.shortcuts.AmbientShortcuts

/**
 * N7 on the launcher: the "My rewards" door.
 *
 * A door, not an offer, like every other entry on the long-press menu. The label names no
 * perk, no count and no tier, because a launcher label is read by whoever is holding the
 * phone and "5 badges" on it would turn a private mechanic into a public one. It is only a
 * way back to a screen the customer has already been to.
 *
 * WHEN IT APPEARS. Only in the NORMAL set, and only once the customer holds a badge. Before
 * the first badge there is nothing behind the door, and a shortcut to an empty rewards screen
 * is an advert for the mechanic — the thing [LoyaltyRepository] is at pains not to be. Under
 * CALM the mechanic is paused and the menu is rebuilt around cooling down; under UNVERIFIED
 * and BLOCKED it is inert. [visible] says no in all three, and it says so from the protection
 * state rather than from LoyaltyState.paused so that the rule is legible in one place.
 *
 * REGISTRATION. This does not publish itself. [AmbientShortcuts.publish] replaces the whole
 * dynamic set in one setDynamicShortcuts call, which is the only way a blocked account is
 * guaranteed to lose every door at once — a second publisher pushing its own shortcut would
 * either be wiped by that call or survive it, and both are wrong. So this file is the
 * judgement (should the door exist?) and the pieces (labels, route, icon, intent), and
 * AmbientShortcuts.normal() is where it joins the set, ranked after the club fixture and
 * subject to the same four-shortcut cap.
 */
object LoyaltyShortcut {

    const val ID = "rewards"
    const val SHORT_LABEL = "My rewards"
    const val LONG_LABEL = "My badges and rewards"

    // Mirrors Routes.REWARDS in ui/nav/Routes.kt. Copied rather than imported for the same
    // reason AmbientShortcuts copies its routes: the ambient layer keeps no dependency on ui/.
    const val ROUTE = "rewards"

    /** The rule in one place. True only in NORMAL and only with at least one badge held. */
    fun visible(state: LoyaltyState, protection: ProtectionState): Boolean =
        protection == ProtectionState.NORMAL && state.badges.isNotEmpty()

    /**
     * The launcher object, built exactly as AmbientShortcuts builds its own: both labels
     * written rather than one reused, an explicit rank, the operator's crest as an adaptive
     * icon, and the same three extras so MainActivity navigates and the engine attributes the
     * session to the launcher menu rather than the app icon.
     */
    fun toShortcut(context: Context, rank: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel(SHORT_LABEL)
            .setLongLabel(LONG_LABEL)
            .setIcon(icon())
            .setRank(rank)
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(AmbientShortcuts.EXTRA_ROUTE, ROUTE)
                    .putExtra(AmbientShortcuts.EXTRA_ENTRY_POINT, AmbientShortcuts.ENTRY_POINT_SHORTCUT)
                    .putExtra(AmbientShortcuts.EXTRA_TARGET_ID, ID),
            )
            .build()

    private var cachedIcon: IconCompat? = null

    /**
     * The operator's badge, padded for the adaptive mask, the way AmbientShortcuts pads a
     * club crest. Always the operator's colours rather than the followed club's: rewards are
     * PSK's, not Hajduk's, and a club-coloured door here would say otherwise. Cached because
     * every rebuild of the menu asks for it.
     */
    @Synchronized
    private fun icon(): IconCompat = cachedIcon ?: run {
        val crest = CrestBitmap.of(ClubThemes.Default, CREST_PX)
        val size = (CREST_PX / SAFE_ZONE).toInt()
        val padded = createBitmap(size, size)
        val inset = (size - CREST_PX) / 2f
        Canvas(padded).drawBitmap(crest, inset, inset, null)
        IconCompat.createWithAdaptiveBitmap(padded).also { cachedIcon = it }
    }

    private const val CREST_PX = 128

    /** The fraction of an adaptive icon guaranteed to survive the launcher's mask. */
    private const val SAFE_ZONE = 0.66f
}
