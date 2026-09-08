package eu.feg.ambient.ambient.surfaces

import android.util.Log
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.Tone
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.MatchState
import java.util.concurrent.atomic.AtomicInteger

/**
 * The two notifications the demo bubble can send on demand.
 *
 * WHY IT EXISTS. On stage you cannot wait for a goal, and the engine is designed to stay quiet
 * most of the time -- which is the product's whole argument and also the reason a judge might
 * never see a notification at all. This is the button that shows one, on request.
 *
 * WHAT IT IS NOT. It is not a second notification channel. Nothing here decides that a moment
 * is worth interrupting for: a human pressed a button, which is the same footing the Surface
 * Lab has always worked on. The real path stays the engine's -- score, budget, router -- and
 * the ledger still records that path's decisions rather than these.
 *
 * ON THE COPY, and this is the part worth defending out loud: the brief for this button asked
 * for something that "attracts the user to bet". It does not say that, and it cannot. Every
 * line here is written by the same narrator ladder the rest of the app uses and is checked by
 * NarratorGuard, which rejects "bet now", "don't miss", "last chance" and every other
 * inducement -- there is a test asserting it. A push that tried would fail the app's own
 * compliance suite, and a judge who saw one would have watched the pitch contradict itself.
 * What arrives instead is a fact about a match the customer follows, which is the more
 * interesting demo anyway: it is what the competitors cannot send.
 *
 * VARIATION is deliberate. Pressing twice must not repeat: [turn] advances a counter that
 * moves both the subject and the tone, so consecutive presses take a different fixture or
 * mission and a different voice. With a model on the ladder the words differ again on top.
 */
object DemoNotifier {

    private val turn = AtomicInteger(0)

    /** The four tones in rotation, so the same subject twice still sounds different. */
    private val tones = Tone.entries

    /**
     * A fact about a match the customer follows: the score if it is being played, the
     * kick-off if it is not.
     */
    suspend fun general(container: AppContainer): String? {
        val n = turn.getAndIncrement()
        val matches = container.matchRepository.matches.value
        val followed = followedNames(container)

        // A followed club first, and among those the ones in play, because a score is a better
        // demo than a fixture list. Falls through to any live match on a device with no club
        // picked, so the button never does nothing.
        val mine = matches.filter { it.home.name in followed || it.away.name in followed }
        val live = (mine.filter { it.state == MatchState.LIVE }
            .ifEmpty { mine.filter { it.state == MatchState.PREMATCH } })
            .ifEmpty { matches.filter { it.state == MatchState.LIVE } }
        if (live.isEmpty()) return null

        val match = live[n % live.size]
        val club = listOf(match.home.name, match.away.name).firstOrNull { it in followed }
        val facts = if (match.state == MatchState.LIVE) factsForLive(match, club) else factsForKickoff(match, club)
        return post(container, facts, tones[n % tones.size], "live")
    }

    /**
     * A mission the customer has not finished, with what it earns.
     *
     * MISSION_AVAILABLE and not MISSION_COMPLETE: the second would congratulate someone for
     * something they have not done. If every mission is finished there is nothing honest to
     * offer, so it returns null and the caller says so.
     */
    suspend fun reward(container: AppContainer): String? {
        val n = turn.getAndIncrement()
        val state = container.loyaltyRepository.state.value
        val open = state.active
        if (open.isEmpty()) return null

        val mission = open[n % open.size]
        val badge = container.loyaltyCatalogue.mission(mission.id)
            ?.let { container.loyaltyCatalogue.badgeFor(it) }
        val facts = MomentFacts(
            type = MomentType.MISSION_AVAILABLE,
            missionTitle = mission.title,
            badgeName = badge?.name,
            badgeCount = state.badgeWeight,
            tierName = state.tier.name.lowercase().replaceFirstChar { it.uppercase() },
            badgesToNextTier = state.toNextTier,
            missionProgress = mission.progress,
            missionTarget = mission.target,
        )
        return post(container, facts, tones[n % tones.size], "missions")
    }

    /**
     * Narrate, then post.
     *
     * The budget is cleared first, and that is a demo affordance rather than a loophole: the
     * one-alert-a-day cap exists to protect a customer from us, and a person pressing a button
     * to see their own app work is not that. It is the same reset the Surface Lab has offered
     * since step 14, called from one more place.
     */
    private suspend fun post(
        container: AppContainer,
        facts: MomentFacts,
        tone: Tone,
        deepLink: String,
    ): String? {
        val text = runCatching {
            container.narrator.narrate(facts, tone, NarratorLanguage.EN)
        }.getOrNull() ?: return null

        container.surfaceController.resetAlertBudget()
        val sent = container.surfaceController.postAlert(text.headline, text.detail, deepLink)
        Log.i(TAG, "demo alert (" + facts.type + "/" + tone + ") sent=" + sent + ": " + text.headline)
        return if (sent) text.headline else null
    }

    private fun factsForLive(match: Match, club: String?) = MomentFacts(
        type = MomentType.GOAL_ON_SLIP,
        homeTeam = match.home.name,
        awayTeam = match.away.name,
        homeScore = match.homeScore ?: 0,
        awayScore = match.awayScore ?: 0,
        minute = match.minute ?: 0,
        period = match.period,
        minutesRemaining = ((FULL_TIME - (match.minute ?: 0)).coerceAtLeast(0)),
        followedTeam = club,
    )

    private fun factsForKickoff(match: Match, club: String?) = MomentFacts(
        type = MomentType.KICKOFF_FOLLOWED,
        homeTeam = match.home.name,
        awayTeam = match.away.name,
        followedTeam = club ?: match.home.name,
        kickoffInMinutes = KICKOFF_MINUTES,
    )

    /**
     * The clubs to treat as the customer's.
     *
     * Reads the follow set and the themed club, exactly as the mission evaluator does, so the
     * notification is about a club the rest of the app already agrees they follow.
     */
    private fun followedNames(container: AppContainer): Set<String> {
        val user = container.userStateRepository.state.value
        val ids = user.followedClubIds + listOfNotNull(user.myClubId)
        return ids.mapNotNull { id -> ClubThemes.byId(id).takeIf { it.clubId.isNotEmpty() }?.name }.toSet()
    }

    private const val FULL_TIME = 90
    private const val KICKOFF_MINUTES = 40
    private const val TAG = "DemoNotifier"
}
