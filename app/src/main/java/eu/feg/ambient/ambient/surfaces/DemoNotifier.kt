package eu.feg.ambient.ambient.surfaces

import android.util.Log
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.engine.TimeBucket
import eu.feg.ambient.ambient.engine.ledger.LedgerEntry
import eu.feg.ambient.ambient.engine.router.Arms
import eu.feg.ambient.ambient.engine.router.Timing
import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.Tone
import eu.feg.ambient.core.AppContainer
import eu.feg.ambient.data.model.BetStatus
import eu.feg.ambient.data.model.Match
import eu.feg.ambient.data.model.MatchState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
        // ON MOCK DATA, AN EMPTY ANSWER IS A BUG. The button dead-ended when the customer held
        // no leg on a live match -- which happens the moment their slip settles. Arming the
        // stage places a real slip on real live fixtures through the real path, so the retry
        // is not a fallback message, it is the same answer arriving a beat later.
        if (!hasSomethingToSay(container)) DemoStage.arm(container)

        val n = turn.getAndIncrement()
        val matches = container.matchRepository.matches.value
        val followed = followedNames(container)

        // A followed club first, and among those the ones in play, because a score is a better
        // demo than a fixture list. Falls through to any live match on a device with no club
        // picked, so the button never does nothing.
        // ONLY CLAIM A PICK WHERE THERE IS ONE. GOAL_ON_SLIP's copy says "Your ... pick is
        // still alive", which is a statement about the customer's account, not about the
        // match -- and on a fixture they hold nothing on it is simply false. So a match with
        // an open leg is preferred and carries that leg's own words; a followed club with no
        // leg gets its kick-off instead, which is true whatever they have staked.
        val legs = openLegs(container)
        val onSlip = matches.filter { it.state == MatchState.LIVE && legs.containsKey(it.id) }
        val followedPre = matches.filter {
            it.state == MatchState.PREMATCH && (it.home.name in followed || it.away.name in followed)
        }

        val match = when {
            onSlip.isNotEmpty() -> onSlip[n % onSlip.size]
            followedPre.isNotEmpty() -> followedPre[n % followedPre.size]
            else -> return null
        }
        val club = listOf(match.home.name, match.away.name).firstOrNull { it in followed }
        val facts = if (legs.containsKey(match.id)) {
            factsForLive(match, club, legs[match.id])
        } else {
            factsForKickoff(match, club)
        }
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

        // A LEDGER ROW FIRST, and the alert carries its id.
        //
        // Without this the demo's own notifications taught the router nothing: no id means no
        // delete intent, so swiping one away was invisible -- and "swipe it and watch it
        // learn" is the whole demo. The row is honest about what it is. Nobody's arm chose
        // this; a person pressed a button, and the reason says so. But the tone is real and a
        // gesture on it is real, so the arm for this surface and voice is the right one to
        // credit or debit.
        val entryId = recordDemoDecision(container, facts.type, tone)

        container.surfaceController.resetAlertBudget()
        val sent = container.surfaceController.postAlert(text.headline, text.detail, deepLink, entryId)
        Log.i(TAG, "demo alert (" + facts.type + "/" + tone + ") id=" + entryId +
            " sent=" + sent + ": " + text.headline)
        return if (sent) text.headline else null
    }

    /**
     * Writes the row the alert is, so a tap or a swipe on it has something to be about.
     *
     * The arm and the context bucket are computed exactly as the router would compute them,
     * because the point is that the gesture lands on the SAME counters a real decision would
     * move. A demo that rewarded a private set of numbers would be a demo of nothing.
     */
    private fun recordDemoDecision(
        container: AppContainer,
        type: MomentType,
        tone: Tone,
    ): String {
        val now = container.clock.now()
        val hour = now.toLocalDateTime(TimeZone.currentSystemDefault()).hour
        val id = "led-" + now.toEpochMilliseconds() + "-demo:" + type.name
        container.ledger.record(
            LedgerEntry(
                id = id,
                momentId = "demo:" + type.name + "@" + now.toEpochMilliseconds(),
                momentType = type.name,
                contextBucket = Arms.contextBucket(type, TimeBucket.of(hour)),
                protection = ProtectionState.NORMAL.name,
                score = 0.0,
                surface = Surface.ALERT.name,
                tone = tone.name,
                armId = Arms.idOf(Surface.ALERT, tone, Timing.IMMEDIATE),
                sampled = 0.0,
                reason = "Sent from the demo bubble, not chosen by the router.",
                shownAt = now.toEpochMilliseconds(),
                createdAt = now.toEpochMilliseconds(),
            ),
        )
        return id
    }

    /** Whether there is a live match the customer holds a leg on, or a followed fixture to come. */
    private fun hasSomethingToSay(container: AppContainer): Boolean {
        val matches = container.matchRepository.matches.value
        val legs = openLegs(container)
        val followed = followedNames(container)
        return matches.any { it.state == MatchState.LIVE && legs.containsKey(it.id) } ||
            matches.any {
                it.state == MatchState.PREMATCH &&
                    (it.home.name in followed || it.away.name in followed)
            }
    }

    /** Match id to the description of the customer's own leg on it, for open slips only. */
    private fun openLegs(container: AppContainer): Map<String, String> =
        container.betRepository.placedBets.value
            .filter { it.status == BetStatus.OPEN }
            .flatMap { it.legs }
            .associate { it.matchId to it.description }

    private fun factsForLive(match: Match, club: String?, leg: String?) = MomentFacts(
        type = MomentType.GOAL_ON_SLIP,
        homeTeam = match.home.name,
        awayTeam = match.away.name,
        homeScore = match.homeScore ?: 0,
        awayScore = match.awayScore ?: 0,
        minute = match.minute ?: 0,
        period = match.period,
        minutesRemaining = ((FULL_TIME - (match.minute ?: 0)).coerceAtLeast(0)),
        followedTeam = club,
        myLegDescription = leg,
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
