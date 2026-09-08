package eu.feg.ambient.ambient.surfaces.shortcuts

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import eu.feg.ambient.MainActivity
import eu.feg.ambient.ambient.identity.ClubTheme
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.identity.CrestBitmap
import eu.feg.ambient.ambient.loyalty.LoyaltyShortcut
import eu.feg.ambient.ambient.surfaces.AndroidSurfaceController
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.data.model.BetStatus
import eu.feg.ambient.data.model.LegStatus
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.MatchState
import eu.feg.ambient.data.repo.BetRepository
import eu.feg.ambient.data.repo.MatchRepository

/**
 * The long-press menu on the launcher icon.
 *
 * The launcher is a surface we get for free, and the four doors on it say what the app thinks
 * the customer is in the middle of: the match running on their slip, the slip itself, a paper
 * ticket to scan, their club's next fixture. Nothing here is an offer — no market, no price,
 * no "bet now" — because a shortcut is a door, and the customer opened this menu themselves.
 *
 * PROTECTION OUTRANKS EVERY OTHER JUDGEMENT ON IT. Calm Mode swaps the whole set for the tools
 * someone cooling down actually came for and keeps no route back into a slip; UNVERIFIED
 * offers verification and help; BLOCKED offers help alone.
 *
 * COMPLIANCE — the labels carry no stake, odds, payout or balance, the same rule
 * SurfaceContracts states for every other OS surface. "2/3" counts picks, never money.
 */
class AmbientShortcuts(
    private val context: Context,
    private val matchRepository: MatchRepository,
    private val betRepository: BetRepository,
    private val clubTheme: () -> ClubTheme = { ClubThemes.Default },
    /**
     * N7. Whether the rewards door belongs in the menu right now.
     *
     * Passed in rather than read here, because the rule ("NORMAL, and at least one badge")
     * lives in LoyaltyShortcut.visible where the loyalty package can test it. Defaults to
     * false so a caller that has not wired loyalty gets the menu it had before.
     */
    private val showRewards: () -> Boolean = { false },
) : AndroidSurfaceController.ShortcutRendering {

    /** What a shortcut is before it becomes a launcher object, so [publish] can rank the set. */
    private data class Spec(
        val id: String,
        val short: String,
        val long: String,
        val route: String,
        val theme: ClubTheme = ClubThemes.Default,
    )

    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val icons = HashMap<String, IconCompat>()

    private var lastPublishAt = 0L
    private var lastPublished: ProtectionState? = null

    /** The state a debounced call asked for and did not get. Never dropped — see [refresh]. */
    private var pending: ProtectionState? = null

    /**
     * Rebuild the menu.
     *
     * DEBOUNCED, because the callers are cheap and this is not: a rebuild draws four icons and
     * makes an IPC round trip to the launcher, while the live match underneath it ticks once a
     * second. Five seconds is invisible to a human opening a menu and turns a per-tick drip
     * into a per-moment one.
     *
     * A DEBOUNCE THAT DROPS THE LAST CHANGE IS A BUG, not an optimisation, so this one has a
     * trailing edge as well as a leading one: the first call publishes immediately, calls
     * inside the window collapse into [pending] and publish once it closes, and the last state
     * asked for is always the state left on the launcher. A protection change skips the window
     * entirely — the five seconds absorb repeated refreshes of the *same* state, and making
     * someone wait to watch "Take a break" appear is not a trade worth making.
     */
    override fun refresh(protection: ProtectionState) {
        val now = SystemClock.elapsedRealtime()
        val immediate = synchronized(lock) {
            if (protection != lastPublished || now - lastPublishAt >= MIN_INTERVAL_MS) {
                pending = null
                true
            } else {
                val alreadyScheduled = pending != null
                pending = protection
                if (!alreadyScheduled) {
                    handler.postDelayed(trailing, MIN_INTERVAL_MS - (now - lastPublishAt))
                }
                false
            }
        }
        if (immediate) {
            handler.removeCallbacks(trailing)
            publish(protection)
        }
    }

    /** The collapsed rebuild. Null means an immediate publish already served the change. */
    private val trailing = Runnable {
        val state = synchronized(lock) { pending.also { pending = null } }
        if (state != null) publish(state)
    }

    private fun publish(protection: ProtectionState) {
        synchronized(lock) {
            lastPublishAt = SystemClock.elapsedRealtime()
            lastPublished = protection
        }

        // MAX_SHORTCUTS is not a style choice: getMaxShortcutCountPerActivity is commonly four
        // or five and is the OEM's to decide, and a set over the limit is rejected whole rather
        // than trimmed. Four is the number every launcher we have tried accepts.
        val specs = specsFor(protection).take(MAX_SHORTCUTS)
        val shortcuts = specs.mapIndexed { rank, spec -> spec.toShortcut(rank) }.toMutableList()

        // Rewards goes LAST and only in NORMAL, and it displaces the least likely tap rather
        // than being dropped: the set is capped, and a door the customer earned should not be
        // silently absent because a fixture happened to fill the fourth slot. specsFor already
        // returns the protected sets without it -- CALM, UNVERIFIED and BLOCKED never reach
        // this branch, which is the rule, not an optimisation.
        if (protection == ProtectionState.NORMAL && showRewards()) {
            if (shortcuts.size >= MAX_SHORTCUTS) shortcuts.removeAt(shortcuts.lastIndex)
            shortcuts += LoyaltyShortcut.toShortcut(context, shortcuts.size)
        }

        // setDynamicShortcuts replaces the set in one call. Remove-all-then-add, which this
        // class replaces, leaves the old set live between the two calls and leaves it there for
        // good if the add half throws — a stale door into a match on a blocked account.
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
            .onFailure { Log.w(TAG, "launcher refused the shortcut set", it) }
    }

    private fun specsFor(protection: ProtectionState): List<Spec> = when (protection) {
        ProtectionState.BLOCKED -> listOf(help())
        ProtectionState.UNVERIFIED -> listOf(verify(), help())
        ProtectionState.CALM -> listOf(takeABreak(), limits(), scores())
        ProtectionState.NORMAL -> normal()
    }

    /**
     * Ordered by how likely the tap is: a match running on the customer's own slip beats the
     * slip, which beats a paper ticket, which beats a fixture that has not kicked off. The
     * first two are absent often enough that the club fixture usually makes the four.
     */
    private fun normal(): List<Spec> = buildList {
        liveMatchOnSlip()?.let { add(it) }
        slip()?.let { add(it) }
        add(Spec("scan", "Scan a ticket", "Scan a paper ticket", ROUTE_SCAN))
        clubFixture()?.let { add(it) }
    }

    /** The match in play that the customer has a pick on — theirs, not merely live. */
    private fun liveMatchOnSlip(): Spec? {
        val mine = matchIdsOnSlip()
        if (mine.isEmpty()) return null
        val match = matchRepository.matches.value
            .firstOrNull { it.state == MatchState.LIVE && it.id in mine } ?: return null

        val score = (match.homeScore ?: 0).toString() + "-" + (match.awayScore ?: 0)
        return Spec(
            id = "match-" + match.id,
            short = match.home.name.take(NAME_TAIL) + " " + score,
            long = match.home.name + " " + score + " " + match.away.name,
            route = ROUTE_LIVE,
            // Club colours only when it is a club we know is playing; anyone else's match
            // wears the operator's badge rather than borrowing one.
            theme = ClubThemes.byName(match.home.name)
                ?: ClubThemes.byName(match.away.name)
                ?: ClubThemes.Default,
        )
    }

    /**
     * The slip, placed or still being built. A placed bet wins the slot because it is the one
     * with something riding on it, and its label is the progress count the lock screen shows —
     * one number in both places, so the launcher cannot contradict the Live Update.
     */
    private fun slip(): Spec? {
        val open = betRepository.placedBets.value.lastOrNull { it.status == BetStatus.OPEN }
        if (open != null) {
            val won = open.legs.count { it.status == LegStatus.WON }
            return Spec(
                id = "slip-" + open.id,
                short = "My slip " + won + "/" + open.legs.size,
                long = "My slip, " + won + " of " + open.legs.size + " picks home",
                route = ROUTE_MY_BETS,
            )
        }

        val picks = betRepository.slip.value.selections.size
        if (picks == 0) return null
        return Spec(
            id = "slip-open",
            short = "My slip " + picks,
            long = picks.toString() + " picks waiting on the slip",
            route = ROUTE_BET_SLIP,
        )
    }

    /**
     * The club's next fixture, with its crest. Null when no club is followed. A fixture that
     * has not started beats one already running: this is the "when do we play next" slot, and
     * a match in play is already covered above when it is on the slip.
     */
    private fun clubFixture(): Spec? {
        val theme = clubTheme()
        if (theme.clubId.isEmpty()) return null

        val fixtures = matchRepository.matches.value
            .filter { it.state != MatchState.FINISHED && it.involves(theme.name) }
        val next = fixtures.filter { it.state == MatchState.PREMATCH }.minByOrNull { it.kickoff }
            ?: fixtures.firstOrNull()
            ?: return null

        val opponent =
            if (next.home.name.equals(theme.name, true)) next.away.name else next.home.name
        return Spec(
            id = "club-" + theme.clubId,
            short = theme.short + " v " + opponent.take(NAME_TAIL),
            long = theme.name + " against " + opponent,
            route = if (next.state == MatchState.LIVE) ROUTE_LIVE else ROUTE_SPORT,
            theme = theme,
        )
    }

    private fun Match.involves(club: String) =
        home.name.equals(club, true) || away.name.equals(club, true)

    /** Matches the customer is exposed to: pending legs of open bets, plus the working slip. */
    private fun matchIdsOnSlip(): Set<String> =
        betRepository.placedBets.value
            .filter { it.status == BetStatus.OPEN }
            .flatMap { it.legs.filter { leg -> leg.status == LegStatus.PENDING } }
            .map { it.matchId }
            .toSet() + betRepository.slip.value.selections.map { it.matchId }

    /** Calm Mode's three. The panic route leads, because it is the one that was asked for. */
    private fun takeABreak() = Spec("break", "Take a break", "Pause my account", ROUTE_RG)

    private fun limits() = Spec("limits", "My limits", "Deposit, loss and time limits", ROUTE_RG)

    /** Scores, not markets: Calm Mode keeps the football and loses the offer. */
    private fun scores() = Spec("scores", "Scores", "Live scores", ROUTE_LIVE)

    /** The register panel, which is where an unverified account is actually resolved. */
    private fun verify() = Spec("verify", "Verify", "Verify your account", ROUTE_REGISTER)

    /** Always reachable, in every state, and never themed away into the background. */
    private fun help() = Spec("help", "Help", "Responsible gaming and help", ROUTE_RG)

    private fun Spec.toShortcut(rank: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, id)
            // Launchers truncate the short label hard and show the long one when the menu has
            // room, so both are written rather than one being reused for both.
            .setShortLabel(short.take(SHORT_LABEL_MAX))
            .setLongLabel(long)
            .setIcon(adaptiveIcon(theme))
            // Without an explicit rank the launcher orders the set as it pleases, and the
            // order is the whole editorial judgement of the menu.
            .setRank(rank)
            .setIntent(intentFor(route, id))
            .build()

    private fun intentFor(route: String, id: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_ROUTE, route)
            // Session attribution: the engine has to know a session began on the launcher menu
            // rather than on the icon, because a surface that is never acted on is one we
            // should stop posting to.
            .putExtra(EXTRA_ENTRY_POINT, ENTRY_POINT_SHORTCUT)
            .putExtra(EXTRA_TARGET_ID, id)

    /**
     * The club badge, padded for the adaptive mask. An adaptive icon is cropped to roughly the
     * inner two thirds of its bitmap, whatever shape the launcher masks with: handing
     * [CrestBitmap] straight over loses the shield's point and shoulders. Cached per club, or a
     * rebuild would allocate four bitmaps every time.
     */
    @Synchronized
    private fun adaptiveIcon(theme: ClubTheme): IconCompat = icons.getOrPut(theme.clubId) {
        val crest = CrestBitmap.of(theme, CREST_PX)
        val size = (CREST_PX / SAFE_ZONE).toInt()
        val padded = createBitmap(size, size)
        val inset = (size - CREST_PX) / 2f
        Canvas(padded).drawBitmap(crest, inset, inset, null)
        IconCompat.createWithAdaptiveBitmap(padded)
    }

    companion object {
        /** MainActivity navigates on this extra; the other two are read for attribution. */
        const val EXTRA_ROUTE = "route"
        const val EXTRA_ENTRY_POINT = "ambient.entryPoint"
        const val EXTRA_TARGET_ID = "ambient.targetId"
        const val ENTRY_POINT_SHORTCUT = "SHORTCUT"

        private const val TAG = "AmbientShortcuts"
        private const val MAX_SHORTCUTS = 4
        private const val MIN_INTERVAL_MS = 5_000L
        private const val SHORT_LABEL_MAX = 14
        private const val NAME_TAIL = 6
        private const val CREST_PX = 128

        /** The fraction of an adaptive icon guaranteed to survive the launcher's mask. */
        private const val SAFE_ZONE = 0.66f

        // Mirrors ui/nav/Routes.kt, the source of truth for the values. Copied rather than
        // imported so the ambient layer keeps no dependency on ui/, the same way
        // LiveUpdateRenderer and AmbientWidget carry their own route strings.
        private const val ROUTE_SPORT = "sport"
        private const val ROUTE_LIVE = "live"
        private const val ROUTE_MY_BETS = "mybets"
        private const val ROUTE_BET_SLIP = "betslip"
        private const val ROUTE_SCAN = "scan"
        private const val ROUTE_RG = "rg"
        private const val ROUTE_REGISTER = "register_panel"
    }
}
