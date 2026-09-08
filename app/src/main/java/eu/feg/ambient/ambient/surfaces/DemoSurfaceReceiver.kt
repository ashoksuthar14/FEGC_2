package eu.feg.ambient.ambient.surfaces

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import eu.feg.ambient.AmbientApp
import eu.feg.ambient.ambient.engine.protection.DefaultProtectionEvaluator
import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.TemplateNarrator
import eu.feg.ambient.ambient.recap.RecapPeriod
import eu.feg.ambient.data.model.RiskState
import eu.feg.ambient.ambient.ticket.ScannedTicket
import eu.feg.ambient.ambient.ticket.TicketLookupResult
import eu.feg.ambient.ambient.narrator.Tone
import kotlin.time.Duration.Companion.days
import eu.feg.ambient.ambient.surfaces.widget.WidgetRefresher
import eu.feg.ambient.ambient.surfaces.widget.applyMute
import eu.feg.ambient.ambient.surfaces.widget.applyThumbs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Drives the surfaces from adb, without bringing an Activity to the front.
 *
 * The Surface Lab is the screen a human uses, but every one of its buttons needs the app in
 * the foreground — which is useless when the thing being tested is what happens on a locked
 * phone, and awkward when someone else is using the device. This receiver exposes the same
 * SurfaceController calls to:
 *
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action reality
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action stage
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action start
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action goal
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action minute
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action calm
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action settle
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action end
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action widget --es state Live
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action thumbs --es value up
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action speak
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action club --es id hajduk_split
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action exclude --es on true
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action away --ei minutes 90
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action seed --ei days 30
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action recap --es period month
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action ticket --es code PSK-DEMO-0001
 *   adb shell am broadcast -a eu.feg.ambient.DEMO_SURFACE --es action risk --es state calm
 *
 * Debug builds only — it is registered behind a manifest flag and does nothing in release.
 */
class DemoSurfaceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? AmbientApp ?: return
        val controller = app.container.surfaceController
        val action = intent.getStringExtra("action") ?: "start"

        Log.i(TAG, "demo action: " + action)

        scope.launch {
            when (action) {
                // Re-arms the whole stage: a slip, its Live Update, and real ledger rows
                // for the catch-up. This is what AmbientApp runs on a cold start, exposed
                // here so a demo can be reset without force-stopping the app.
                // The casino reality check, without sitting through an hour of session. It
                // goes through the tracker and the engine, so what is demonstrated is the
                // real pipeline and not a notification posted for the occasion.
                "reality" -> {
                    app.container.gameSessionTracker.checkNow()
                    Log.i(TAG, "reality check raised")
                }

                "stage" -> {
                    val armed = DemoStage.arm(app.container)
                    DemoStage.armAwayPeriod(app.container)
                    WidgetRefresher.refreshAll(context)
                    // Asked here rather than trusted: the catch-up is built from ledger rows
                    // the engine wrote a moment ago, and "it should work" is not a check.
                    val digest = app.container.peekDigest()
                    Log.i(TAG, "stage armed=" + armed +
                        ", digest=" + (digest?.headline ?: "none") +
                        " (" + (digest?.items?.size ?: 0) + " items)")
                }

                "start" -> {
                    // Narrated, like the real path. Without this the demo card carries no
                    // spoken variant and Listen falls back to the plain sentence, which is
                    // not what a customer would actually hear.
                    slip = narrated(app, DemoSurfaceData.threeLegLive(), MomentType.GOAL_ON_SLIP)
                    controller.startLiveUpdate(slip)
                    controller.refreshWidget(WidgetState.Live(slip))
                }

                "minute" -> {
                    slip = slip.copy(
                        minute = (slip.minute ?: 61) + 1,
                        minutesRemaining = ((slip.minutesRemaining ?: 29) - 1).coerceAtLeast(0),
                    )
                    controller.updateLiveUpdate(slip)
                    controller.refreshWidget(WidgetState.Live(slip))
                }

                "goal" -> {
                    slip = narrated(
                        app,
                        slip.copy(homeScore = (slip.homeScore ?: 0) + 1),
                        MomentType.GOAL_ON_SLIP,
                    )
                    controller.updateLiveUpdate(slip)
                    controller.refreshWidget(WidgetState.Live(slip))
                }

                "winleg" -> {
                    val legs = slip.legs.toMutableList()
                    val index = legs.indexOfFirst { it.status == LegStatus.PENDING }
                    if (index >= 0) legs[index] = legs[index].copy(status = LegStatus.WON)
                    slip = slip.copy(legs = legs)
                    controller.updateLiveUpdate(slip)
                    controller.refreshWidget(WidgetState.Live(slip))
                }

                "calm" -> {
                    slip = slip.copy(protection = ProtectionState.CALM)
                    controller.updateLiveUpdate(slip)
                    controller.refreshWidget(WidgetState.Live(slip))
                }

                "normal" -> {
                    slip = slip.copy(protection = ProtectionState.NORMAL)
                    controller.updateLiveUpdate(slip)
                    controller.refreshWidget(WidgetState.Live(slip))
                }

                "blocked" -> {
                    // Must remove the surface entirely, not merely restyle it.
                    slip = slip.copy(protection = ProtectionState.BLOCKED)
                    controller.updateLiveUpdate(slip)
                    controller.refreshWidget(
                        WidgetState.Protected(ProtectionState.BLOCKED, lastRegisterCheck = null),
                    )
                }

                "settle" -> {
                    slip = slip.copy(settled = true)
                    controller.endLiveUpdate(slip.slipId, settled = true)
                    controller.refreshWidget(WidgetState.Settled(slip))
                }

                "end" -> controller.endLiveUpdate(slip.slipId, settled = false)

                "alert" -> {
                    val sent = controller.postAlert(
                        "Your slip is settled",
                        "Two of three came in. Nothing left running.",
                        "mybets",
                    )
                    Log.i(TAG, "alert sent=" + sent)
                }

                "widget" -> {
                    val state = when (intent.getStringExtra("state")) {
                        "PreMatch" -> DemoSurfaceData.preMatchWidget
                        "Settled" -> WidgetState.Settled(DemoSurfaceData.twoLegSettled())
                        "Idle" -> WidgetState.Idle("Varaždin – Istria 1961", null)
                        "Protected" -> WidgetState.Protected(ProtectionState.CALM, null)
                        "Digest" -> WidgetState.Digest(
                            "While you were away",
                            "2 legs won, Betis drew, Sparta kick off in 40 min.",
                            app.container.clock.now(),
                        )
                        else -> WidgetState.Live(slip)
                    }
                    controller.refreshWidget(state)
                }

                // Real CALM, through UserState, not a styling flag on the demo slip: the
                // evaluator derives it and every surface follows. "normal" puts it back.
                "risk" -> {
                    val calm = intent.getStringExtra("state") != "normal"
                    app.container.userStateRepository.setRiskState(
                        if (calm) RiskState.AT_RISK else RiskState.NORMAL,
                    )
                    Log.i(TAG, "risk -> " + app.container.protectionEvaluator.evaluate())
                }

                // Step 18: the retail slip, without the camera. This is the same seam the
                // scanner calls -- lookup, then track -- so it exercises the feature rather
                // than a shortcut around it, and it is the demo's insurance if the camera
                // fails on stage. The camera itself is the only thing this cannot test.
                "ticket" -> {
                    val code = intent.getStringExtra("code") ?: "PSK-DEMO-0001"
                    val scanned = ScannedTicket(code, "MANUAL", app.container.clock.now())
                    when (val result = app.container.ticketLookup.lookup(scanned)) {
                        is TicketLookupResult.Found -> {
                            val tracked = app.container.ticketTracker.track(result.bet)
                            Log.i(TAG, "ticket " + code + " -> Found, tracked=" + tracked +
                                ", legs=" + result.bet.legs.size + ", source=" + result.bet.source)
                        }
                        is TicketLookupResult.AlreadyTracked ->
                            Log.i(TAG, "ticket " + code + " -> AlreadyTracked " + result.betId)
                        is TicketLookupResult.Settled ->
                            Log.i(TAG, "ticket " + code + " -> Settled, status=" + result.bet.status)
                        is TicketLookupResult.Invalid ->
                            Log.i(TAG, "ticket " + code + " -> Invalid: " + result.reason)
                        TicketLookupResult.NotFound ->
                            Log.i(TAG, "ticket " + code + " -> NotFound")
                    }
                }

                // 17B: a month of history for the recap. Rows are labelled "Demo seed" and
                // the digest ranker refuses them, so seeding the recap can never put a
                // fabricated moment into a customer's catch-up. Do this before the demo.
                "seed" -> {
                    val days = intent.getIntExtra("days", 30)
                    val n = DemoLedgerSeed.seed(app.container.ledger, app.container.clock.now(), days)
                    Log.i(TAG, "seeded " + n + " ledger rows over " + days + " days")
                }

                "recap" -> {
                    val period = if (intent.getStringExtra("period") == "season") RecapPeriod.SEASON else RecapPeriod.MONTH
                    val recap = app.container.recapBuilder.build(period, app.container.clock.now())
                    Log.i(TAG, "recap " + period + " -> " + (recap?.headline ?: "null") + " / " + (recap?.detail ?: "-"))
                    if (recap != null) controller.refreshWidget(WidgetState.Recap(recap))
                }

                // Step 16: pretend the customer has been away, then poke the widget. The
                // widget is the trigger, so its redraw is what turns the away period into a
                // digest -- this only supplies the away period.
                "away" -> {
                    val minutes = intent.getIntExtra("minutes", 90)
                    app.container.awayTracker.simulateAway(minutes)
                    controller.refreshWidget(
                        controller.currentWidgetState() ?: WidgetState.Idle(null, null),
                    )
                    Log.i(TAG, "away for " + minutes + " min; widget poked")
                }

                // The real protection flip: the demo player goes on or off the exclusion
                // register, and the evaluator's own cache is dropped so the change lands now
                // rather than at the end of its fifteen-minute window. This is the same
                // register the panel writes to -- not a shortcut past it.
                "exclude" -> {
                    val on = intent.getStringExtra("on")?.toBooleanStrictOrNull() ?: true
                    val register = app.container.exclusionRegister
                    val ref = DefaultProtectionEvaluator.DEMO_PLAYER
                    if (on) {
                        register.add(ref, app.container.clock.now().plus(1.days))
                    } else {
                        register.remove(ref)
                    }
                    app.container.protectionEvaluator.checkNow()
                    Log.i(TAG, "exclude " + on + " -> " + app.container.protectionEvaluator.evaluate())
                }

                // N6 from the wire. It writes through the same repository the Home strip
                // and the Surface Lab picker write to, so the surfaces repaint by the one
                // path -- this is a remote control for the control, not a second control.
                "club" -> {
                    app.container.userStateRepository.setMyClub(
                        intent.getStringExtra("id")?.takeIf { it.isNotBlank() },
                    )
                    Log.i(TAG, "club set to " + intent.getStringExtra("id"))
                }

                // N1 read aloud, driven the same way. Still never automatic: this is a
                // broadcast someone typed, which is a tap by another name.
                "speak" -> {
                    val result = app.container.spokenMoments.speak(
                        SpokenSurface.forSlip(slip),
                        eu.feg.ambient.ambient.engine.Surface.LIVE_UPDATE,
                    )
                    Log.i(TAG, "speak result: " + result)
                }

                // The widget's own feedback buttons, reachable without a finger on the glass.
                // Learning is the claim hardest to demonstrate, and it should not depend on
                // someone tapping a 2x2 card accurately while the phone is being filmed.
                "thumbs" -> when (intent.getStringExtra("value")) {
                    "down" -> applyThumbs(context, positive = false)
                    "mute" -> applyMute(context)
                    else -> applyThumbs(context, positive = true)
                }

                else -> Log.w(TAG, "unknown demo action: " + action)
            }
            Log.i(TAG, "after " + action + ": chip=" + slip.chipText + " protection=" + slip.protection)
        }
    }

    /** Runs the real narrator over a demo slip, so the demo surfaces carry real text. */
    private suspend fun narrated(
        app: AmbientApp,
        slip: SlipSurfaceState,
        type: MomentType,
    ): SlipSurfaceState = runCatching {
        // The template rung directly, not the ladder. The demo is driven from adb between
        // other steps, and waiting twelve seconds for the on-device model to time out and
        // fall through to this same template -- which is what it did -- makes every step
        // look broken on stage. The model's own latency is demonstrated in the Narrator Lab.
        slip.copy(
            narrated = demoNarrator.narrate(
                MomentFacts(
                    type = type,
                    homeTeam = slip.homeTeam,
                    awayTeam = slip.awayTeam,
                    homeScore = slip.homeScore,
                    awayScore = slip.awayScore,
                    minute = slip.minute,
                    period = slip.period,
                    legsTotal = slip.legsTotal,
                    legsWon = slip.legsWon,
                    legsLost = slip.legsLost,
                    myLegDescription = slip.legs.firstOrNull { it.status == LegStatus.PENDING }
                        ?.description,
                    minutesRemaining = slip.minutesRemaining,
                ),
                Tone.PLAIN,
                NarratorLanguage.EN,
            ),
        )
    }.getOrDefault(slip)

    private companion object {
        const val TAG = "DemoSurface"
        val demoNarrator = TemplateNarrator()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Mutable across broadcasts so a sequence of actions builds on the last one. */
        @Volatile
        var slip: SlipSurfaceState = DemoSurfaceData.threeLegLive()
    }
}
