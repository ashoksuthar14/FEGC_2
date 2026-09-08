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

## 14.0b — Building step 14 before step 13 (this is the plan)

Step 13 (protection layer, moment engine, learning router) does not exist yet, and step 14 was
written not to need it. Two adjustments make that work cleanly.

**Adjustment 1 — a temporary `ProtectionState`.** The real one arrives in 13A from the register
check and the age proof. For now derive it from what the shell already has
(`UserState.riskState` from PRD-01 §6) plus a stub age flag, and keep it in
`ambient/surfaces/` so 13A can move it later without touching a renderer:

```kotlin
enum class ProtectionState { NORMAL, CALM, UNVERIFIED, BLOCKED }

// TEMPORARY — replaced in step 13A by the register check + age assurance.
// Renderers must depend on ProtectionState only, never on UserState.riskState.
fun UserState.toProtectionStateTemp(ageVerified: Boolean): ProtectionState = when {
  !ageVerified                          -> ProtectionState.UNVERIFIED
  riskState == RiskState.SELF_EXCLUDED  -> ProtectionState.BLOCKED
  riskState == RiskState.AT_RISK        -> ProtectionState.CALM
  panicUntil?.let { it > Clock.System.now() } == true -> ProtectionState.CALM
  limits.anyAtOrAbove(0.8)              -> ProtectionState.CALM
  else                                  -> ProtectionState.NORMAL
}
```

**Adjustment 2 — dummy data.** Create `ambient/surfaces/DemoSurfaceData.kt` with three
ready-made slips built from the existing mock matches, so every surface has something real to
show before any engine exists:

- `threeLegLive` — Liverpool–Ipswich 1–0 at 61', legs: Liverpool win (pending), Betis win
  (won), Sparta win (won) → renders as `2/3 ✓ · 61'`
- `twoLegSettled` — one won, one lost, settled
- `oneLegPreMatch` — Varaždin–Istria, kickoff in 40 minutes

The Surface Lab (14D) picks between these. Nothing else needs to change.

**The two swap points, so nothing has to be rewritten later**

| Later step | What it replaces |
|---|---|
| 13A | `toProtectionStateTemp` → the real register check and age proof. Renderers untouched |
| 13B | The Surface Lab's buttons → the engine calling the same `SurfaceController` |

---

## 14.0c — Do this 20-minute spike before anything else

The single biggest unknown in step 14 is whether your Pixel 10a will actually *promote* a
notification. If it does not, the lock-screen centrepiece changes shape and you want to know
that now, not at hour six.

```
Before building anything else, spike this in a scratch file and delete it afterwards.

Add to AndroidManifest.xml:
  <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
  <uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS"/>

Add a temporary button to any screen that:
  1. requests POST_NOTIFICATIONS if not granted
  2. creates a channel "spike" with IMPORTANCE_DEFAULT
  3. posts a NotificationCompat notification that meets every promotion requirement:
     ProgressStyle, setOngoing(true), setRequestPromotedOngoing(true), a contentTitle,
     setShortCriticalText("2/3 · 61'"), NO custom RemoteViews, not a group summary,
     not colorized
  4. immediately reads back the posted notification from
     NotificationManager.getActiveNotifications() and logs:
       - notificationManager.canPostPromotedNotifications()
       - whether the notification's flags include FLAG_PROMOTED_ONGOING
       - notification.hasPromotableCharacteristics()

Check the current ProgressStyle builder signatures against Google's sample at
https://github.com/android/platform-samples/tree/main/samples/user-interface/live-updates
rather than guessing them.

Install, tap the button, lock the phone, and tell me three things:
  a) is there a chip in the status bar
  b) is the card expanded and uncollapsible on the lock screen
  c) what the three logged values say
```

**How to read the result**

| Outcome | What it means | What to do |
|---|---|---|
| Chip appears, `FLAG_PROMOTED_ONGOING` set | Promotion works | Build 14A–14E as written |
| No chip, `canPostPromotedNotifications()` false | The user or OEM disabled it | Open `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS`, enable, retry |
| No chip, `hasPromotableCharacteristics()` false | One of the eight requirements is violated | Usually a custom view, `setColorized(true)`, or an `IMPORTANCE_MIN` channel |
| No chip, everything else true | The OEM applies extra eligibility rules | The notification still renders ongoing on the lock screen — lead the demo with the **widget**, keep the lock-screen card as the second beat, and say plainly that promotion is device-dependent. Keep the diagnostics screen; showing the real reason is stronger than pretending |

---

## 14.1 — FAST PATH (use this; ~2h wall clock instead of 4h serial)

The 4-hour figure was serial and over-tested. Three things cut it roughly in half.

**1. All shared-file edits happen once, up front.** Manifest, Gradle, theme and `AppContainer`
are the only files three workstreams would collide on. Do them all in the foundation prompt,
then the parallel agents create *new files only* and cannot conflict.

**2. Fan out three agents.** Live Update, widget and Surface Lab touch disjoint packages and
share nothing but the contract from step one. They are genuinely parallel.

**3. Test only what protects a claim.** One test — that `UNVERIFIED` and `BLOCKED` create no
Live Update — because that is the compliance property a judge could break on stage. Everything
else is verified by looking at the phone, which is faster and more honest than a unit test of
a notification builder.

### Rules that keep the parallel run from going wrong

- **Only the main session runs Gradle.** Parallel agents write files and stop. Concurrent
  `./gradlew` invocations fight over the daemon and the build lock, and you lose more time to
  that than you saved. Say this explicitly in the agent prompts.
- **Give each agent an exact file allowlist.** An agent that "helpfully" edits the manifest
  undoes the whole point.
- **Ask for complete files, not incremental edits.** Faster to write and faster to review.
- **No Compose previews for the notification** (you cannot preview one). Keep previews for the
  widget only, and only the small size.

### Prompt 1 · Foundation — do everything shared, in one pass (~25 min)

```
Read docs/CLAUDE_CODE_PROMPTS_PHASE2-Step14-Surfaces.md sections 14.0b and 14A.

Do ALL of the following in one pass. Write complete files. Do not write tests yet.

Shared-file edits (these are the only files anyone will touch outside their own package —
after this, nothing else in step 14 modifies them):
  - AndroidManifest.xml: POST_NOTIFICATIONS, POST_PROMOTED_NOTIFICATIONS, the
    LiveSlipService declaration with foregroundServiceType="dataSync", and the
    GlanceAppWidgetReceiver.
  - app/build.gradle.kts: androidx.glance:glance-appwidget and glance-material3, latest stable.
  - AppContainer: expose `surfaceController: SurfaceController` and `demoData: DemoSurfaceData`.

New files:
  - ambient/surfaces/SurfaceContracts.kt — ProtectionState, LegStatus, LegState,
    SlipSurfaceState, WidgetState, SurfaceDiagnostics, and the SurfaceController interface,
    exactly as specified in 14A. Include the comment that SlipSurfaceState carries no money.
  - ambient/surfaces/ProtectionStateTemp.kt — toProtectionStateTemp() from 14.0b.
  - ambient/surfaces/DemoSurfaceData.kt — the three demo slips from 14.0b, built from the
    existing mock matches.
  - ambient/surfaces/notifications/Channels.kt — the three channels from 14A.
  - ambient/surfaces/notifications/NotificationPermission.kt — request helper + rationale.
  - ambient/surfaces/AlertBudget.kt — 1 per rolling 24h in DataStore.
  - ambient/surfaces/LiveSlipService.kt — foreground service shell that owns the tick.
  - ambient/surfaces/AndroidSurfaceController.kt — implements SurfaceController with every
    method as TODO() that logs its arguments. The three agents will fill these in.

Then run ./gradlew assembleDebug once and fix whatever does not compile. Report the file list.
```

### Prompt 2 · Fan out three agents in parallel (~50 min wall clock)

```
Spawn three agents in parallel. Each writes ONLY the files in its allowlist and does NOT run
Gradle — I will build once when all three finish. If an agent needs something outside its
allowlist, it must stop and say so rather than editing the file.

AGENT 1 — Live Update
  Spec: section 14B of docs/CLAUDE_CODE_PROMPTS_PHASE2-Step14-Surfaces.md
  Files: ambient/surfaces/live/LiveUpdateRenderer.kt, ambient/surfaces/live/DismissReceiver.kt
  Before writing, check the current ProgressStyle builder signatures against
  https://github.com/android/platform-samples/tree/main/samples/user-interface/live-updates
  — do not guess method names.
  Skip for now: the settled auto-dismiss timer, the deep-link intent (use a TODO), CALM styling
  polish. Get NORMAL rendering promoted and correct first.

AGENT 2 — Glance widget
  Spec: section 14C
  Files: everything under ambient/surfaces/widget/
  Build all six WidgetState cases but only the SMALL size for now — add Responsive/medium later
  if there is time. One @Preview for the Live state only.
  Use PskTheme tokens, never Glance or Material defaults.

AGENT 3 — Surface Lab
  Spec: section 14D
  Files: ambient/ui/dev/SurfaceLabScreen.kt, ambient/ui/dev/SurfaceLabViewModel.kt, and the
  one-line nav entry under the More sheet.
  It calls SurfaceController only — never a renderer directly. Build the full control set;
  this screen is how everything else gets tested, so it is not the place to cut corners.

When all three report back, I will build.
```

### Prompt 3 · Integrate and see it on the phone (~25 min)

```
Build with ./gradlew assembleDebug, fix compile errors across the three agents' output, wire
AndroidSurfaceController's TODOs to the three renderers, then installDebug.

Then write exactly ONE test — the compliance property, nothing else:
  LiveUpdateRendererTest: ProtectionState.UNVERIFIED and ProtectionState.BLOCKED must post no
  notification. Everything else we verify by looking at the phone.

Open Surface Lab and drive it: start a Live Update, +1 minute three times, home goal, win a
leg, then flip protection to CALM. Tell me what the diagnostics card says and what you see.
```

### Prompt 4 · Wire to real app use (~20 min)

```
Section 14E. Bet placement asks for notification permission and starts the Live Update;
MatchClock ticks drive updates throttled to 20s unless score or leg status changed; settlement
ends it and attempts one alert; any ProtectionState change refreshes every surface immediately.
```

### Where the time actually goes

| Phase | Wall clock |
|---|---|
| 14.0c spike | 0h 20 |
| Prompt 1 foundation | 0h 25 |
| Prompt 2 three agents in parallel | 0h 50 |
| Prompt 3 integrate, one test, look at the phone | 0h 25 |
| Prompt 4 wire to real use | 0h 20 |
| **Total** | **~2h 20** |

Do not cut the spike. It is 20 minutes that decides whether the other two hours build the right
thing, and it is the one step where being wrong is expensive.

### Deliberately deferred to later steps or "if time"

Medium widget size · settled auto-dismiss timer · digest state polish · CALM visual refinement ·
the accessibility pass (step 19 owns it) · shortcuts refresh (step 16) · every unit test except
the one above.

---

> **Sections 14A–14E below are the detailed specification.** The fast path above executes
> them; read them as the reference the agents are pointed at, not as five prompts to paste
> one after another. Paste them individually only if you would rather build serially.

## 14A — The surface contract and notification plumbing

```
We are building the Ambient surfaces. Read docs/Ambient_Final_Spec.md sections 3 (feature set),
5 (architecture and the renderer matrix) and 6 (protection layer).

This step builds the contract and the plumbing only — no visible surface yet.
Step 13 does not exist yet: use the temporary ProtectionState and DemoSurfaceData from
section 14.0b of this document, and make sure no renderer ever reads UserState directly.

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
