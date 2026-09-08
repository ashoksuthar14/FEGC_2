package eu.feg.ambient.core

import android.content.Context
import android.util.Log
import eu.feg.ambient.ambient.narrator.EngineState
import eu.feg.ambient.ambient.narrator.LadderNarrator
import eu.feg.ambient.ambient.narrator.LiteRtEngineHolder
import eu.feg.ambient.ambient.narrator.LiteRtNarrator
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.Tone
import eu.feg.ambient.ambient.narrator.NanoAvailabilityChecker
import eu.feg.ambient.ambient.narrator.NanoNarrator
import eu.feg.ambient.ambient.narrator.NanoState
import eu.feg.ambient.ambient.narrator.Narrator
import eu.feg.ambient.ambient.narrator.TemplateNarrator
import eu.feg.ambient.ambient.identity.ClubTheme
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.digest.AwayTracker
import eu.feg.ambient.ambient.surfaces.ProtectionState
import eu.feg.ambient.ambient.digest.DefaultDigestBuilder
import eu.feg.ambient.ambient.digest.Digest
import eu.feg.ambient.ambient.digest.DigestBuilder
import eu.feg.ambient.ambient.digest.UnlockWatcher
import eu.feg.ambient.ambient.recap.DefaultRecapBuilder
import eu.feg.ambient.ambient.recap.Recap
import eu.feg.ambient.ambient.recap.RecapBuilder
import eu.feg.ambient.ambient.recap.RecapPeriod
import eu.feg.ambient.ambient.recap.RecapSeen
import eu.feg.ambient.ambient.surfaces.AndroidSurfaceController
import eu.feg.ambient.ambient.surfaces.WidgetState
import eu.feg.ambient.ambient.surfaces.shortcuts.AmbientShortcuts
import eu.feg.ambient.ambient.surfaces.DemoSurfaceData
import eu.feg.ambient.ambient.surfaces.SurfaceController
import eu.feg.ambient.ambient.engine.AmbientEngine
import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.engine.moment.DefaultAttentionBudget
import eu.feg.ambient.ambient.engine.moment.DefaultMomentBuilder
import eu.feg.ambient.ambient.engine.moment.KickoffMomentSource
import eu.feg.ambient.ambient.engine.moment.DefaultRelevanceScorer
import eu.feg.ambient.ambient.engine.protection.DefaultProtectionEvaluator
import eu.feg.ambient.ambient.engine.protection.PrefsAgeAssurance
import eu.feg.ambient.ambient.engine.protection.SyntheticExclusionRegister
import eu.feg.ambient.ambient.engine.router.BanditRouter
import eu.feg.ambient.ambient.surfaces.MomentSpeaker
import eu.feg.ambient.ambient.ticket.MockTicketLookup
import eu.feg.ambient.ambient.ticket.TicketLookup
import eu.feg.ambient.ambient.ticket.TicketTracker
import eu.feg.ambient.ambient.surfaces.SpokenMoments
import eu.feg.ambient.ambient.surfaces.AlertBudget
import eu.feg.ambient.ambient.surfaces.SurfaceCoordinator
import eu.feg.ambient.ambient.surfaces.live.LiveUpdateRenderer
import eu.feg.ambient.ambient.surfaces.widget.WidgetRenderer
import eu.feg.ambient.data.clock.MatchClock
import eu.feg.ambient.data.clock.SystemMatchClock
import eu.feg.ambient.ambient.loyalty.BadgeAwarder
import eu.feg.ambient.ambient.loyalty.LoyaltyShortcut
import eu.feg.ambient.ambient.loyalty.BadgeRepository
import eu.feg.ambient.ambient.loyalty.MissionTracker
import eu.feg.ambient.ambient.surfaces.widget.WidgetRefresher
import eu.feg.ambient.ambient.loyalty.LoyaltyCatalogue
import eu.feg.ambient.ambient.loyalty.LoyaltyRepository
import eu.feg.ambient.ambient.loyalty.MissionRepository
import eu.feg.ambient.ambient.loyalty.store.LoyaltyStore
import eu.feg.ambient.data.mock.MockDataSource
import eu.feg.ambient.data.repo.BetRepository
import eu.feg.ambient.data.repo.MatchRepository
import eu.feg.ambient.data.repo.UserStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Plain constructors instead of a DI framework (PRD section 2.3). Held by the Application,
 * so the tick and the repositories outlive any single screen.
 */
class AppContainer(context: Context) {

    /**
     * The container's own scope.
     *
     * Public because AmbientApp starts the mission tracker with it: a subscriber that must
     * live exactly as long as the process belongs on the process's scope, not on a second one
     * created at the call site that nobody would remember to cancel.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // --- club identity (N6) --------------------------------------------------------------

    /**
     * The customer's club, or the operator's own colours.
     *
     * Declared here, at the top, rather than derived where its inputs live: the Live Update
     * renderer is built long before the protection evaluator is, and a surface asking "whose
     * colours am I?" must never depend on the order this file happens to be written in. The
     * flow is filled in by [observeClubTheme] once everything exists.
     */
    private val _myClubTheme = MutableStateFlow(ClubThemes.Default)
    val myClubTheme: StateFlow<ClubTheme> = _myClubTheme.asStateFlow()

    /** Public so the UI can read the static content files directly. */
    val source = MockDataSource.fromContext(context)

    /** Declared as the interface, never the impl — Phase 2 swaps this line alone. */
    val clock: MatchClock = SystemMatchClock()

    val matchRepository = MatchRepository(source, clock, appScope)

    val betRepository = BetRepository(clock)

    val userStateRepository = UserStateRepository(
        context.getSharedPreferences("ambient", Context.MODE_PRIVATE),
    )

    // --- narrator (Phase 2, step 12) --------------------------------------------------

    val nanoAvailability = NanoAvailabilityChecker(appScope)

    private val templateNarrator = TemplateNarrator()

    private val nanoNarrator = NanoNarrator(fallback = templateNarrator)

    /** Path B: a Gemma 3 model we ship ourselves, independent of AICore entirely. */
    val liteRtEngine = LiteRtEngineHolder(context, appScope)

    private val liteRtNarrator = LiteRtNarrator(liteRtEngine, fallback = templateNarrator)

    /**
     * Whether the local Gemma rung may run.
     *
     * This was off while the first generation terminated the process. The cause turned out
     * to be ours: an uncapped output token budget and native calls spread across threads.
     * With both fixed it is on, and the diagnostics screen can still disable it if a device
     * misbehaves — process death is the one failure the ladder cannot catch.
     */
    @Volatile
    var localGenerationEnabled: Boolean = true

    /**
     * Built on every access from the current probe results: Nano first when the OS has it,
     * then our own Gemma, then templates.
     *
     * Computed rather than cached deliberately. A cached field rebuilt by a collector left a
     * window between "engine became Ready" and "ladder rebuilt", during which the very first
     * narration silently fell back to templates. Wrapping a list is free; a race is not.
     */
    val narrator: Narrator
        get() = LadderNarrator(
            buildList {
                if (nanoAvailability.state.value is NanoState.Available) add(nanoNarrator)
                if (localGenerationEnabled && liteRtEngine.state.value is EngineState.Ready) {
                    add(liteRtNarrator)
                }
                add(templateNarrator)
            },
        )

    /** The template rung alone, for the Narrator Lab's side-by-side comparison. */
    val templateOnly: Narrator = templateNarrator

    /** Null until the device reports Nano available; the Lab shows a notice in that case. */
    val nanoOrNull: Narrator?
        get() = if (nanoAvailability.state.value is NanoState.Available) nanoNarrator else null

    /** Null unless the local model is loaded and generation has been explicitly enabled. */
    val localGemmaOrNull: Narrator?
        get() = if (localGenerationEnabled && liteRtEngine.state.value is EngineState.Ready) {
            liteRtNarrator
        } else {
            null
        }

    init {
        nanoAvailability.check()
        liteRtEngine.initialize()
    }

    /**
     * Pre-warms the model off the main thread. First inference loads it and can take many
     * seconds; the user must not pay that on a lock screen, and onCreate must never block.
     */
    fun warmUpNarrator() {
        appScope.launch {
            if (nanoAvailability.state.value is NanoState.Available) {
                nanoNarrator.warmUp()
            }
        }
    }

    // --- surfaces (Phase 2, step 14) ---------------------------------------------------

    /**
     * The single door to every OS surface. The Surface Lab drives it today; step 13B's
     * Moment Engine calls the same methods without any renderer changing.
     *
     * The renderers are attached here rather than constructed inside the controller, so the
     * controller stays testable and knows nothing about notifications or Glance.
     */
    val surfaceController: SurfaceController = AndroidSurfaceController(context, AlertBudget(context)).apply {
        liveUpdateRenderer = LiveUpdateRenderer(context) { _myClubTheme.value }
        widgetRenderer = WidgetRenderer(context, clubTheme = { _myClubTheme.value })
        shortcutRenderer = AmbientShortcuts(
            context = context,
            matchRepository = matchRepository,
            betRepository = betRepository,
            clubTheme = { _myClubTheme.value },
            // Read through the lambda, not captured: loyaltyRepository is declared further
            // down this file and the menu is not built during construction.
            showRewards = {
                LoyaltyShortcut.visible(loyaltyRepository.snapshot(), protectionEvaluator.state.value)
            },
        )
    }

    /** Ready-made slips so the surfaces have something real to show before the engine exists. */
    val demoData = DemoSurfaceData

    /**
     * N1: the moment read aloud. One TextToSpeech engine for the process, on-device only,
     * and never started by anything but a tap.
     */
    val momentSpeaker = MomentSpeaker(context)

    // --- engine (Phase 2, step 13) ------------------------------------------------------

    /**
     * Append-only record of every decision, silences included. File-backed rather than Room:
     * see the KDoc on Ledger for why KSP was not worth the risk here.
     */
    val ledger = Ledger(context)

    val exclusionRegister = SyntheticExclusionRegister(context)

    val ageAssurance = PrefsAgeAssurance(context)

    val protectionEvaluator = DefaultProtectionEvaluator(
        register = exclusionRegister,
        ageAssurance = ageAssurance,
        userStateRepository = userStateRepository,
        ledger = ledger,
        scope = appScope,
    )

    /** One counter, shared with the surface controller, so the two cannot disagree. */
    private val sharedAlertBudget = AlertBudget(context)

    val relevanceScorer = DefaultRelevanceScorer()

    val router = BanditRouter(ledger)

    /**
     * Ownership needs the club's NAME, because that is what a fixture carries; the user state
     * stores its id. ClubThemes is the one place that maps between them, and byId falls back
     * to the operator's own theme, so an unknown or absent id has to become an empty set here
     * rather than the literal team "PSK".
     */
    private val momentBuilder = DefaultMomentBuilder(
        betRepository = betRepository,
        followedTeams = {
            val theme = ClubThemes.byId(userStateRepository.state.value.myClubId)
            if (theme.clubId.isEmpty()) emptySet() else setOf(theme.name)
        },
    )

    private val attentionBudget = DefaultAttentionBudget(
        context = context,
        userStateRepository = userStateRepository,
        alertBudget = sharedAlertBudget,
    )

    /**
     * Replaces a hand on the Surface Lab buttons. The Lab stays as a manual override, but the
     * engine is what decides whether an event surfaces at all.
     */
    val engine = AmbientEngine(
        protectionEvaluator = protectionEvaluator,
        momentBuilder = momentBuilder,
        scorer = relevanceScorer,
        attentionBudget = attentionBudget,
        router = router,
        narrator = narrator,
        surfaceController = surfaceController,
        ledger = ledger,
    )

    /**
     * Speaking is an event like any other: it leaves a ledger row and rewards the arm that
     * produced the moment. Declared after the ledger and the router because it needs both.
     */
    val spokenMoments = SpokenMoments(
        speaker = momentSpeaker,
        ledger = ledger,
        router = router,
        protection = { protectionEvaluator.evaluate() },
    )

    // --- digest (step 16) ---------------------------------------------------------------

    // --- N7 loyalty ---------------------------------------------------------------------

    /**
     * Missions, badges and perks.
     *
     * The catalogue reads the same assets folder as everything else; the store is one JSON
     * file behind a StateFlow, the Ledger's pattern rather than Room -- see LoyaltyStore for
     * why. start() is called once below, because the flows are shared and a second instance
     * of the tracker would double-count every mission.
     */
    val loyaltyCatalogue = LoyaltyCatalogue { path -> source.read(path) }

    val loyaltyStore = LoyaltyStore(context)

    val missionRepository = MissionRepository(loyaltyCatalogue, loyaltyStore)

    val badgeRepository = BadgeRepository(loyaltyCatalogue, loyaltyStore)

    val loyaltyRepository = LoyaltyRepository(
        catalogue = loyaltyCatalogue,
        store = loyaltyStore,
        missionRepository = missionRepository,
        badgeRepository = badgeRepository,
        protection = protectionEvaluator.state,
        now = { clock.now() },
    ).also {
        missionRepository.start(appScope)
        badgeRepository.start(appScope)
        it.start(appScope)
    }

    /**
     * The one place mission progress is computed.
     *
     * ONE INSTANCE, started once from AmbientApp. Two trackers would award nothing twice --
     * the store forbids that -- but would recompute the whole set on every change for no
     * reason, and "mission progress jumps" is the symptom of a second subscriber.
     *
     * Declared after the engine because the awarder posts into it: a completed mission is an
     * event through AmbientEngine.onEvent like any other, never a notification of its own.
     */
    val badgeAwarder = BadgeAwarder(
        badgeRepository = badgeRepository,
        raise = { event -> engine.onEvent(event) },
        now = { clock.now() },
    )

    val missionTracker = MissionTracker(
        missionRepository = missionRepository,
        badgeAwarder = badgeAwarder,
        userStateRepository = userStateRepository,
        betRepository = betRepository,
        ledger = ledger,
        protection = protectionEvaluator.state,
        // Wired to the repository rather than repeated, so the tracker and the screens'
        // "paused" flag cannot disagree about what CALM means.
        accrues = { loyaltyRepository.accrues(it) },
        // AppWidgetManager is the only party that knows whether a widget is on a home
        // screen; the launcher tells nobody. Asked on each recompute rather than observed.
        widgetPlaced = { WidgetRefresher.anyPlaced(context) },
        now = { clock.now() },
    )

    /** How long the customer has been away, and whether that is worth a catch-up. */
    val awayTracker = AwayTracker(context)

    val digestBuilder: DigestBuilder = DefaultDigestBuilder(
        ledger = ledger,
        narrator = narrator,
        protection = { protectionEvaluator.evaluate() },
    )

    /**
     * The digest for the widget to draw, or null.
     *
     * Called from provideGlance, which is the primary trigger: the launcher redraws a widget
     * whether or not our process is alive, and "alive" is exactly what a customer who has been
     * away for two hours is not. Marking it shown here rather than at some later confirmation
     * is deliberate — if we drew it, it has been offered, and offering it twice is the push
     * notification we claim to have replaced.
     */
    /**
     * The digest WITHOUT consuming it.
     *
     * The dedicated "While you were away" widget is about the away period itself, so it shows
     * the catch-up for as long as that period lasts; only the Live Slip card, which borrows
     * its one surface, takes it once. Without this split the two widgets raced -- whichever
     * composed first marked it shown and the other quietly fell to its empty state.
     */
    suspend fun peekDigest(): Digest? {
        val protection = protectionEvaluator.evaluate()
        if (protection == ProtectionState.BLOCKED || protection == ProtectionState.UNVERIFIED) return null
        val since = awayTracker.lastInteractionAt() ?: return null
        if (awayTracker.awayDuration() < AwayTracker.AWAY_THRESHOLD) return null
        return digestBuilder.build(since)
    }

    suspend fun digestForWidget(): Digest? {
        val protection = protectionEvaluator.evaluate()
        if (!awayTracker.shouldShowDigest(protection)) return null
        val since = awayTracker.lastInteractionAt() ?: return null
        val digest = digestBuilder.build(since)
        Log.i("Digest", if (digest == null) "away since " + since + " but nothing worth a card" else
            "digest drawn: " + digest.headline + " / " + digest.detail + " [" + digest.items.size + " items]")
        if (digest == null) return null
        awayTracker.markDigestShown()
        return digest
    }

    // --- retail slip (step 18) ------------------------------------------------------------

    /** INTEGRATION SEAM: FEG's retail ticket API in production; tickets.json for the demo. */
    val ticketLookup: TicketLookup = MockTicketLookup(context, matchRepository, betRepository) { clock.now() }

    val ticketTracker = TicketTracker(betRepository, ledger, clock)

    // --- recap (N5, step 17B) ------------------------------------------------------------

    val recapSeen = RecapSeen(context)

    val recapBuilder: RecapBuilder = DefaultRecapBuilder(
        ledger = ledger,
        narrator = narrator,
        protection = { protectionEvaluator.evaluate() },
        myClubId = { userStateRepository.state.value.myClubId },
    )

    /**
     * The recap for the widget to draw when nothing is live, or null.
     *
     * Same shape as the digest and for the same reason: the widget is redrawn by the
     * launcher whether or not we are alive, so this is where "is there a recap to show"
     * gets decided. Marked seen on draw -- a recap is a gift, and a gift handed over twice
     * is a nag. The month is tried before the season because it is the fresher story.
     */
    suspend fun recapForWidget(): Recap? {
        val now = clock.now()
        for (period in listOf(RecapPeriod.MONTH, RecapPeriod.SEASON)) {
            val recap = recapBuilder.build(period, now) ?: continue
            if (recapSeen.hasSeen(period, recap.to)) continue
            recapSeen.markSeen(period, recap.to)
            Log.i("Recap", "recap drawn: " + recap.headline + " / " + recap.detail)
            return recap
        }
        return null
    }

    /**
     * The unlock, when the process happens to be alive to hear it. An enhancement on top of
     * the widget path, never the trigger — see UnlockWatcher for why that distinction is the
     * whole design of this feature.
     */
    val unlockWatcher = UnlockWatcher(context, appScope) {
        val protection = protectionEvaluator.evaluate()
        if (awayTracker.shouldShowDigest(protection)) {
            surfaceController.refreshWidget(
                digestForWidget()?.let { WidgetState.Digest(it.headline, it.detail, it.generatedAt) }
                    ?: return@UnlockWatcher,
            )
        }
    }

    /**
     * The forty-minute warning for a followed club.
     *
     * It raises a MatchEvent and stops; the engine decides whether that becomes a widget, an
     * alert or nothing at all, exactly as it does for a goal. Riding the match clock rather
     * than WorkManager keeps this dependency-free -- minute precision is all a forty-minute
     * lead needs.
     */
    val kickoffMomentSource = KickoffMomentSource(
        scope = appScope,
        clock = clock,
        matchRepository = matchRepository,
        myClubId = { userStateRepository.state.value.myClubId },
        protection = { protectionEvaluator.evaluate() },
        onEvent = { engine.onEvent(it) },
    )

    /**
     * Step 14E: turns ordinary app use into surfaces. Started from the Application so a bet
     * placed anywhere reaches the lock screen without a screen having to remember to ask.
     */
    val surfaceCoordinator = SurfaceCoordinator(
        scope = appScope,
        controller = surfaceController,
        betRepository = betRepository,
        matchRepository = matchRepository,
        userStateRepository = userStateRepository,
        narrator = narrator,
        clock = clock,
        protection = { protectionEvaluator.evaluate() },
        onMatchEvent = { engine.onEvent(it) },
        clubTheme = myClubTheme,
        protectionState = protectionEvaluator.state,
    )

    /**
     * Debug-only: once the local model is ready, narrate a few moments and log the result.
     * The Narrator Lab does this interactively, but a lock screen should not stand between
     * us and a latency number.
     */
    fun logNarratorSelfTest() {
        appScope.launch {
            if (!localGenerationEnabled) return@launch
            liteRtEngine.state.first { it is EngineState.Ready }

            val cases = listOf(
                Triple(MomentType.GOAL_ON_SLIP, Tone.PLAIN, NarratorLanguage.EN),
                Triple(MomentType.GOAL_ON_SLIP, Tone.WITTY, NarratorLanguage.EN),
                Triple(MomentType.AWAY_DIGEST, Tone.PLAIN, NarratorLanguage.EN),
            )
            cases.forEach { (type, tone, language) ->
                // Spaced out: LiteRT holds native state per conversation, and running them
                // back to back on a memory-pressured phone gets the process killed.
                delay(1_500)
                val started = System.currentTimeMillis()
                val result = narrator.narrate(eu.feg.ambient.ui.lab.sampleFacts(type), tone, language)
                val wall = System.currentTimeMillis() - started
                Log.i(
                    SELF_TEST_TAG,
                    type.name + " / " + tone.name + " / " + language.name +
                        " | engine=" + result.engine.name + " | " + wall + "ms" +
                        " | H: " + result.headline + " | D: " + result.detail,
                )
            }

        }
    }

    /**
     * Club plus protection, combined into the one value every surface reads.
     *
     * Eager, and started here rather than by a screen, because the surfaces that use it run
     * with no UI on screen at all — a widget redraw after a reboot must find the right club
     * already resolved.
     */
    private fun observeClubTheme() {
        appScope.launch {
            combine(
                userStateRepository.state,
                protectionEvaluator.state,
            ) { user, protection -> ClubThemes.forState(user.myClubId, protection) }
                .distinctUntilChanged()
                .collect { _myClubTheme.value = it }
        }
    }

    init {
        observeClubTheme()
    }

    companion object {
        const val ML_KIT_GENAI_VERSION = "com.google.mlkit:genai-prompt:1.0.0-beta2"
        const val SELF_TEST_TAG = "NarratorSelfTest"
    }
}
