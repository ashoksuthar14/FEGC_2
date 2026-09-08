package eu.feg.ambient.ambient.identity

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import eu.feg.ambient.MainActivity
import eu.feg.ambient.ambient.surfaces.AndroidSurfaceController
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.data.model.MatchState
import eu.feg.ambient.data.repo.MatchRepository

/**
 * The long-press menu on the launcher icon.
 *
 * N6 puts the customer's club at the top of it: their next fixture, with their crest, is the
 * one shortcut they will actually use, and a launcher menu is a surface we get for free.
 *
 * PROTECTION IS NOT COSMETIC HERE, so this is the one place club identity gives way. Under
 * UNVERIFIED the only routes offered are verification and help; under BLOCKED, help alone.
 * A shortcut is a door, and a protected account must not be shown a door into a match.
 */
class ClubShortcuts(
    private val context: Context,
    private val matchRepository: MatchRepository,
    private val clubTheme: () -> ClubTheme = { ClubThemes.Default },
) : AndroidSurfaceController.ShortcutRendering {

    override fun refresh(protection: ProtectionState) {
        val shortcuts = when (protection) {
            ProtectionState.BLOCKED -> listOf(help())
            ProtectionState.UNVERIFIED -> listOf(verify(), help())
            ProtectionState.NORMAL, ProtectionState.CALM -> normalShortcuts(protection)
        }
        runCatching {
            ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            ShortcutManagerCompat.addDynamicShortcuts(context, shortcuts)
        }.onFailure { Log.w(TAG, "could not publish shortcuts", it) }
    }

    /**
     * The club's next match first, then the standing routes.
     *
     * Calm Mode keeps the club shortcut — it is a fixture, not an offer — but loses the bet
     * slip, because a one-tap route back to a slip is exactly the affordance someone cooling
     * down asked us to stop putting in front of them.
     */
    private fun normalShortcuts(protection: ProtectionState): List<ShortcutInfoCompat> {
        val shortcuts = mutableListOf<ShortcutInfoCompat>()
        clubFixture()?.let { shortcuts += it }
        shortcuts += route("live", "Live", "Live matches", ROUTE_LIVE)
        if (protection == ProtectionState.NORMAL) {
            shortcuts += route("mybets", "My bets", "My bets", ROUTE_MY_BETS)
        } else {
            shortcuts += help()
        }
        return shortcuts.take(MAX_SHORTCUTS)
    }

    /** Null when no club is followed, or when that club has no fixture in the data. */
    private fun clubFixture(): ShortcutInfoCompat? {
        val theme = clubTheme()
        if (theme.clubId.isEmpty()) return null

        val match = matchRepository.matches.value
            .filter { it.home.name.equals(theme.name, true) || it.away.name.equals(theme.name, true) }
            // A match in play beats one that has not started; a finished one is no use at all.
            .minByOrNull {
                when (it.state) {
                    MatchState.LIVE -> 0
                    MatchState.PREMATCH -> 1
                    else -> 2
                }
            } ?: return null

        val opponent = if (match.home.name.equals(theme.name, true)) match.away.name else match.home.name
        return ShortcutInfoCompat.Builder(context, "club-" + theme.clubId)
            .setShortLabel(theme.short + " v " + opponent.take(SHORT_LABEL_TAIL))
            .setLongLabel(theme.name + " against " + opponent)
            .setIcon(IconCompat.createWithBitmap(CrestBitmap.of(theme, ICON_PX)))
            .setIntent(intentFor(ROUTE_LIVE))
            .build()
    }

    private fun route(id: String, short: String, long: String, route: String) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(short)
            .setLongLabel(long)
            .setIcon(IconCompat.createWithBitmap(CrestBitmap.of(clubTheme(), ICON_PX)))
            .setIntent(intentFor(route))
            .build()

    private fun verify() = route("verify", "Verify", "Verify your account", ROUTE_HOME)

    /** Always reachable, in every state, and never themed away into the background. */
    private fun help() = route("help", "Help", "Responsible gaming and help", ROUTE_RG)

    private fun intentFor(route: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("route", route)

    private companion object {
        const val TAG = "ClubShortcuts"
        const val MAX_SHORTCUTS = 4
        const val SHORT_LABEL_TAIL = 6
        const val ICON_PX = 128
        const val ROUTE_LIVE = "live"
        const val ROUTE_MY_BETS = "mybets"
        const val ROUTE_RG = "rg"
        const val ROUTE_HOME = "sport"
    }
}
