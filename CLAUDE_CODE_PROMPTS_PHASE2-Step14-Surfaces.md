# Phase 2 · Step 14 — The surfaces: lock screen, status chip, widget, alerts

Prerequisite: shell (PRD-01 steps 1–11) and the narrator (step 12) are done and installed.
Outcome: the app can put a live bet slip on the lock screen, the always-on display and the
status bar, keep a home-screen widget in step with it, and send a rare alert — all driven
through **one controller** that the Moment Engine will later call in place of your hand.

Build order below is deliberate: the contract first, then the two surfaces, then the control
panel that drives them. Do not skip the control panel — it is how you test without waiting
for real match events, and it is a slide in the pitch.

---

## 14.0 Two decisions to settle before you start

**1. Where the ticking happens.** A Live Update has to keep changing while the app is in the
background, and a backgrounded Android app gets frozen. Options: a **foreground service** of
type `dataSync` (works for a 90-minute match; Android 15 applies a daily budget), or
`specialUse` with a declared subtype. `shortService` is capped at a few minutes and is wrong
here. Recommendation: `dataSync` for the demo, and one honest line in the deck that FEG would
agree the production type with Google.

**2. Permission timing.** Do not ask for notifications at launch. The widget needs no
permission at all, and `POST_NOTIFICATIONS` is asked at the moment the user places a live bet
— "track this slip on your lock screen?". On Android a denied notification permission is
close to permanent, and notification opt-in retention is one of the metrics being judged.

---

## 14A — The surface contract and notification plumbing

```
We are building the Ambient surfaces. Read docs/Ambient_Final_Spec.md sections 3 (feature set),
5 (architecture and the renderer matrix) and 6 (protection layer).

This step builds the contract and the plumbing only — no visible surface yet.

1. Create package ambient/surfaces/ with the state the renderers consume. Renderers must be
   pure functions of this; they may not reach into repositories.

   enum class LegStatus { PENDING, WON, LOST, VOID }

   data class LegState(
     val description: String,      // "Liverpool to win"
     val match: String,            // "Liverpool – Ipswich"
     val status: LegStatus
   )

   data class SlipSurfaceState(
     val slipId: String,
     val protection: ProtectionState,          // NORMAL | CALM | UNVERIFIED | BLOCKED
     val legs: List<LegState>,
     val activeMatch: String?,                 // the match currently live
     val homeTeam: String?, val awayTeam: String?,
     val homeScore: Int?, val awayScore: Int?,
     val minute: Int?, val period: String?,    // "1. poluvrijeme"
     val minutesRemaining: Int?,
     val narrated: NarratedText?,              // from step 12's Narrator
     val settled: Boolean
   ) {
     val legsWon get() = legs.count { it.status == LegStatus.WON }
     val legsTotal get() = legs.size
     // "2/3 ✓ · 61'" — keep it under ~14 chars, the status chip is small
     val chipText: String get() = TODO()
   }

   sealed interface WidgetState {
     data class PreMatch(val match: String, val kickoffIn: Duration, val legs: List<LegState>) : WidgetState
     data class Live(val slip: SlipSurfaceState) : WidgetState
     data class Settled(val slip: SlipSurfaceState) : WidgetState
     data class Digest(val headline: String, val detail: String, val since: Instant) : WidgetState
     data class Idle(val nextFixture: String?, val kickoff: Instant?) : WidgetState
     data class Protected(val protection: ProtectionState, val lastRegisterCheck: Instant?) : WidgetState
   }

   IMPORTANT: SlipSurfaceState carries no stake, no odds, no potential return and no balance.
   Money never reaches an OS surface. Add a comment saying so — this is the compliance
   property the whole pitch rests on.

2. The controller. Everything that changes a surface goes through this one interface, so the
   Moment Engine can replace the Surface Lab later without touching a renderer.

   interface SurfaceController {
     suspend fun startLiveUpdate(state: SlipSurfaceState)
     suspend fun updateLiveUpdate(state: SlipSurfaceState)
     suspend fun endLiveUpdate(slipId: String, settled: Boolean)
     suspend fun refreshWidget(state: WidgetState)
     suspend fun postAlert(headline: String, detail: String, deepLink: String): Boolean  // false if budget spent
     suspend fun refreshShortcuts(protection: ProtectionState)
     val diagnostics: StateFlow<SurfaceDiagnostics>
   }

   SurfaceDiagnostics reports: notification permission granted, canPostPromotedNotifications,
   whether the last posted notification actually carried FLAG_PROMOTED_ONGOING, alerts sent
   today, and the active Live Update id if any.

3. Notification plumbing in ambient/surfaces/notifications/:
   - Manifest: <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/> and
     <uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS"/>
     (the second is a normal, non-runtime permission).
   - Three channels, so a user can mute one thing rather than the app:
       "live_slip"   IMPORTANCE_DEFAULT — the Live Update. Never IMPORTANCE_MIN, that
                     disqualifies it from promotion.
       "settlement"  IMPORTANCE_HIGH    — the rare alert.
       "digest"      IMPORTANCE_LOW     — the unlock digest.
   - A permission helper that requests POST_NOTIFICATIONS only when called, plus a rationale
     sheet. Wire the call site to bet placement, NOT to app launch: after a live bet is placed,
     show "Track this slip on your lock screen?" and ask only if they accept.
   - An alert budget: at most 1 interrupting alert per rolling 24h, stored in DataStore.
     postAlert returns false when spent — never silently swallow it.

4. Foreground service ambient/surfaces/LiveSlipService.kt, type dataSync, started when a live
   slip begins and stopped when it settles or is dismissed. It owns the tick that drives
   updateLiveUpdate. Declare the type in the manifest.

Build with ./gradlew assembleDebug. Nothing is visible yet — that is expected.
```

---

## 14B — The Live Update: lock screen, always-on display, status chip

```
Now the lock-screen surface. This is the centrepiece of the demo, so get the requirements
exactly right rather than approximating them.

FIRST: fetch https://developer.android.com/develop/ui/compose/notifications/live-update and
read Google's sample at
https://github.com/android/platform-samples/tree/main/samples/user-interface/live-updates
to confirm the current ProgressStyle API — the exact Segment/Point builder signatures. Do not
guess method names from memory; use what the sample actually calls.

Implement ambient/surfaces/LiveUpdateRenderer.kt.

Hard requirements for a notification to be promoted (all must hold, or it silently renders as
an ordinary notification):
  - style is ProgressStyle (permitted styles are Standard, BigTextStyle, CallStyle,
    ProgressStyle, MetricStyle)
  - setOngoing(true)
  - setRequestPromotedOngoing(true)
  - a contentTitle is set
  - NO custom RemoteViews and NO customContentView
  - not setGroupSummary(true)
  - not setColorized(true)
  - the channel is not IMPORTANCE_MIN

Content, in NORMAL protection:
  - one ProgressStyle segment per leg; won segments filled, pending segments unfilled, lost
    segments marked. Use the theme's positive/negative colours, and make sure won/lost is
    distinguishable by more than colour.
  - a point on the track for each goal in the active match
  - contentTitle: the narrated headline from step 12 (e.g. "Liverpool 1–0 · 61'")
  - contentText: the narrated detail
  - setShortCriticalText(state.chipText) for the status-bar chip ("2/3 ✓ · 61'")
  - contentIntent deep-links to that slip in My Bets
  - setDeleteIntent to a receiver that records the dismissal — and after a dismissal, do NOT
    repost the same slip's Live Update. Google's guidance is explicit about this.
  - setVisibility(VISIBILITY_PRIVATE) so the content is hidden on a locked screen if the user
    has chosen to hide sensitive notifications
  - contentDescription/accessibility: set the narrated spokenText as the notification's
    accessible text so a screen reader hears a whole sentence, not "two slash three tick"

Content in CALM protection:
  - score and minute only. No leg progress, no narrated line about the bet.
  - one action: "Take a break" → panic flow
  - never promoted with urgency, never a sound

UNVERIFIED or BLOCKED: never create the notification at all. Assert this in a unit test.

Also implement:
  - endLiveUpdate(settled = true): final state showing the result, auto-dismiss after 30s
  - endLiveUpdate(settled = false): cancel immediately
  - diagnostics: after posting, read back the active notification and record whether
    FLAG_PROMOTED_ONGOING is actually set, plus canPostPromotedNotifications(). We need to
    show this on stage.
  - if canPostPromotedNotifications() is false, expose an action that opens
    Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS

Install and confirm on the device: lock the phone and the card is expanded and uncollapsible
on the lock screen; the chip shows in the status bar; the always-on display shows it.
Report what the diagnostics say.
```

---

## 14C — The home-screen widget

```
Build the Glance widget: ambient/surfaces/widget/.

Dependency: androidx.glance:glance-appwidget (latest stable) and androidx.glance:glance-material3.

AmbientWidget : GlanceAppWidget with SizeMode.Responsive for two sizes:
  - small  (2x2): chip line + legs progress + one action
  - medium (4x2): everything, plus the narrated detail line

Render all six WidgetState cases from 14A, styled with the PskTheme tokens (never Glance
defaults, never Material purple):

  PreMatch   match name, countdown to kickoff, legs listed greyed, action "Remind me"
  Live       "2/3 ✓ · 61'" large, legs as segments, score, narrated headline, tap → match
  Settled    result, legs with won/lost icons (icon AND colour), tap → My Bets
  Digest     "While you were away" + the digest headline and detail, tap → Moments inbox
  Idle       next fixture for a followed team, or "Follow a team to see it here"
  Protected  CALM     → match score only, reality check line, "Protection active · register
                        checked N minutes ago", panic action
             UNVERIFIED → neutral "Verify to continue" card. No odds, no match, no money.
             BLOCKED   → protection status and a help action only

Actions via ActionCallback: Remind me · Mute this match · 👍 · 👎 · Panic. The 👍/👎 write to
the ledger's feedback table (the learning router reads them in step 13C).

Update mechanism: SurfaceController.refreshWidget calls AmbientWidget.updateAll(context).
Widget state persists across process death — store the current WidgetState with Glance's
state definition or in DataStore, so a widget redrawn by the launcher after a reboot shows
something sensible rather than a blank.

Accessibility: every widget action needs a contentDescription; the whole card needs a
semantic description built from the narrated spokenText; won/lost carries an icon as well as
colour; text must survive 200% font scale — test it.

Install, add the widget to the home screen, and confirm each state renders by driving it from
the Surface Lab in the next step.
```

---

## 14D — Surface Lab: drive every surface by hand

```
Build a developer screen "Surface Lab", reachable from the More sheet under Developer. This
is how we test surfaces without waiting for match events, and it is what I will use on stage
if the simulator misbehaves.

Layout, top to bottom:

1. DIAGNOSTICS card (read-only, live)
   - notification permission: granted / denied / not asked  + a "Request" button
   - canPostPromotedNotifications(): true / false + "Open settings" when false
   - last posted notification carried FLAG_PROMOTED_ONGOING: yes / no
   - alerts sent in the last 24h: n / 1
   - active Live Update slip id, or "none"

2. SLIP BUILDER
   - pick one of the mock slips, or build one: 1–4 legs from the mock matches
   - protection state selector: NORMAL / CALM / UNVERIFIED / BLOCKED
   - tone selector and language selector (feeds the narrator from step 12)

3. LIVE UPDATE controls — each button calls SurfaceController and nothing else
   [ Start ]  [ +1 minute ]  [ Home goal ]  [ Away goal ]  [ Win next leg ]
   [ Lose next leg ]  [ Settle slip ]  [ End / dismiss ]
   Each action rebuilds SlipSurfaceState, asks the Narrator for fresh text, and calls
   updateLiveUpdate. Show the generated headline/detail/spokenText and which narrator engine
   produced them, right there in the screen.

4. WIDGET controls
   [ PreMatch ] [ Live ] [ Settled ] [ Digest ] [ Idle ] [ Protected ]
   plus [ Force refresh ]. Show when the widget was last updated.

5. ALERT controls
   [ Send settlement alert ]  — calls postAlert; if it returns false, show "budget spent"
   rather than doing nothing. [ Reset budget ] for demo purposes only, clearly labelled.

6. SHORTCUTS
   [ Refresh shortcuts ] and a preview of which shortcuts are currently published.

Every button must go through SurfaceController — no renderer is called directly from the UI.
That is what makes the Moment Engine a drop-in replacement in step 13B.

Install and walk through: start a Live Update, advance the minute three times, score a goal,
win a leg, then flip protection to CALM and confirm every surface changes within a second.
```

---

## 14E — Wire it to the app for real

```
Last part: make the surfaces appear from normal app use, not only from the Lab.

1. On bet placement (the BetPlaced flow from PRD-01 §11): if any leg is on a live match, ask
   for notification permission with the "track this slip" rationale, and on grant call
   startLiveUpdate. If the user declines, still refreshWidget — the widget needs no permission
   and is our fallback path to the customer.

2. Subscribe to MatchClock: every tick, rebuild SlipSurfaceState for the active slip and call
   updateLiveUpdate. Throttle to at most one update every 20 seconds unless the score or a leg
   status changed, so we are not redrawing the lock screen every second.

3. On settlement: endLiveUpdate(settled = true), refreshWidget(Settled), and attempt one
   postAlert. Respect the budget's answer.

4. On any change to ProtectionState: immediately refresh every surface. This is the Calm Mode
   demo moment and it must be instant, not on the next tick.

5. Add a small "Live on lock screen" indicator to the My Bets row for a slip that currently
   has an active Live Update, so the app tells the truth about what is on the phone.

Then verify end to end, and report:
  - place a live slip, lock the phone, watch the chip and card update on their own
  - flip protection to CALM in RG settings → every surface goes calm within a second
  - airplane mode → everything still updates, because the clock is local
  - TalkBack on → the lock screen card reads as a complete sentence
```

---

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Notification appears but is not promoted (no chip, collapsible on lock screen) | One of the eight requirements in 14B is violated — most often a custom view, `setColorized(true)`, or an `IMPORTANCE_MIN` channel. Check `hasPromotableCharacteristics()` |
| `canPostPromotedNotifications()` returns false | The user or the OEM disabled it — open `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS`. Some OEMs add their own eligibility rules |
| Updates stop after a minute in the background | The process was frozen. The foreground service is not running, or its type is wrong |
| Widget shows blank after reboot | State was held in memory only — persist `WidgetState` |
| Widget actions do nothing | `ActionCallback` not registered, or the receiver is missing from the manifest |
| Chip text truncated | `setShortCriticalText` is small — keep it under ~14 characters |
| Live Update reappears after the user swiped it away | You are reposting a dismissed notification. Honour `setDeleteIntent` |

---

## Done when

1. A live slip appears on the lock screen and the always-on display, with a status-bar chip,
   and updates on its own while the phone is locked.
2. Diagnostics confirm `FLAG_PROMOTED_ONGOING` was actually set — not just requested.
3. The widget renders all six states and its actions work.
4. Flipping protection to CALM changes every surface within a second; UNVERIFIED and BLOCKED
   create no Live Update at all, with a test proving it.
5. At most one alert in 24 hours, and `postAlert` tells the truth when the budget is spent.
6. TalkBack reads the lock-screen card as a sentence.
7. Everything above works in airplane mode.
