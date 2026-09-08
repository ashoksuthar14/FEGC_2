# Ambient — measuring the six metrics

Metrics: Value per Session · Session Conversion Rate · Actions per Session · Final-step
Conversion · Sessions per User · Time to First Action.

Almost all of this is free, because the decision ledger already records every surface shown,
every surface tapped and every silence. What has to be added is **session attribution** — the
answer to "what started this session" — and everything else derives from it.

---

## 1. The one thing that must be instrumented first

Every metric below is only interesting when split by **entry point**. Without that, Ambient's
effect is invisible; with it, the whole argument is measurable.

```kotlin
enum class EntryPoint {
  LIVE_UPDATE,     // tapped the lock-screen card
  STATUS_CHIP,     // tapped the status-bar chip
  WIDGET,          // tapped the home-screen widget
  SHORTCUT,        // long-press launcher shortcut
  DIGEST,          // tapped the "while you were away" card
  ALERT,           // tapped the rare interrupting notification
  ORGANIC          // opened the app icon directly — the control group
}

data class Session(
  val id: String,
  val userRef: String,
  val entryPoint: EntryPoint,
  val entryMomentId: String?,      // links back to the ledger row that created the surface
  val startedAt: Instant,
  val endedAt: Instant?,
  val protectionAtStart: ProtectionState
)
```

**Session boundary:** a foreground period ending after 30 minutes of no interaction, or on
process death. Standard, and it matches how FEG's existing analytics will already be defined —
use their definition if it differs, so the numbers are comparable to their baseline.

**Attribution rule:** every deep link from every surface carries `entryPoint` and `momentId`
as intent extras. `ORGANIC` is what everything else falls back to. This single field is what
turns the ledger into a business case.

---

## 2. Metric by metric

### Sessions per User

**Definition:** distinct sessions ÷ distinct active users, per week.

**How:** count `Session` rows. Report it three ways — total, organic-only, and surface-originated.

**What Ambient claims:** organic sessions stay roughly flat and surface-originated sessions are
added on top. That is the honest shape of the claim, and splitting it this way is what proves it
rather than asserting it. If organic sessions *fall* while total rises, we have cannibalised
rather than grown, and we should say so.

### Time to First Action ★

**Definition:** milliseconds from session start to the first *meaningful* interaction. Scrolling
does not count; a tap on a match, a slip, a market or a control does.

**How:** timestamp at session start, timestamp on the first event of type `MEANINGFUL_ACTION`.

**What Ambient claims:** this is where OS surfaces should win most clearly. A surface-originated
session arrives with intent already formed and the deep link lands on the target screen, so the
customer does not navigate — they are already there. Expect a large gap versus `ORGANIC`, and it
is a *customer-experience* number as much as a funnel one: less time spent finding things.

**Watch for:** near-zero times that are really accidental taps. Discard sessions shorter than
~2 seconds with no second action, and report that you did.

### Actions per Session

**Definition:** count of meaningful actions per session.

**How:** count `MEANINGFUL_ACTION` events, grouped by session.

**Read this one carefully.** More is not automatically better here, and claiming it is will get
picked apart. A moment-driven session is often *supposed* to be short: glance, check the slip,
leave satisfied. Segment it:

| Session intent | Healthy shape |
|---|---|
| Check-in (from Live Update or chip) | Low actions, low time, high satisfaction — a *good* session |
| Exploration (organic, pre-match) | Higher actions |
| Transaction (started a bet flow) | Actions until completion, then stop |

Report Actions per Session **within** each intent class, never pooled. Pooled, Ambient will look
like it lowers the number, when what it has actually done is add a class of short, useful sessions
that did not exist before.

### Session Conversion Rate

**Definition:** sessions with at least one completed primary action ÷ all sessions.

**Define "primary action" per intent class, and publish the definition** — this is the metric
most easily gamed by choosing a flattering denominator:

- Check-in session → viewed the thing the surface was about
- Exploration → followed a team, added a selection, or set a reminder
- Transaction → placed a bet

**What Ambient claims:** the rate rises not because we persuade anyone, but because the mix
changes. Broadcast push produces many low-intent sessions that convert at close to nothing.
Ambient produces fewer, moment-driven sessions with intent already formed. Removing the
low-intent denominator is most of the effect, and saying that plainly is more credible than
implying we improved persuasion.

### Final-step Conversion

**Definition:** of sessions that *entered* a defined flow, the share that completed its last step.

**How:** emit `FlowStarted(flowId, step)` and `FlowStep(flowId, step, index)` events. Final-step
conversion is `completed ÷ started`, per flow.

The flows worth instrumenting: bet slip (selection → stake → place), age verification (start →
attribute shared → verified), permission (asked → granted), and register-check-blocked (blocked →
help opened).

**What Ambient claims:** small or no change, and that is the correct answer. Ambient does not
touch the bet slip. What it *does* affect is the permission flow — asking at bet placement rather
than at launch should raise that flow's final-step conversion sharply, and that is a genuine,
attributable win.

### Value per Session

**Definition:** net value attributable to a session ÷ sessions. In this business, gross gaming
revenue per session is the natural numerator; FEG's finance team owns the exact definition.

**How:** attribute value to the session in which the action occurred, and carry `entryPoint` on
it so value can be split by surface.

**Be careful and be explicit here.** Ambient's thesis is *not* "more stake per session". The
claim is that the **mix** of sessions improves — you get more of the sessions that were going to
happen anyway, earlier and with less friction, and fewer wasted low-intent ones. Present it as:

```
Value per Session  =  Σ value / Σ sessions,  reported separately for
                      surface-originated and organic sessions
```

and pair it with a flat-or-better **harm guardrail** in the same view. A rise in Value per Session
alongside a rise in harm indicators is a failure, not a success, and the deck should say so
before a judge does.

---

## 3. Guardrail metrics — report these beside the six, always

| Guardrail | Must |
|---|---|
| Deposits, losses and session length per at-risk-flagged user | stay flat |
| Self-set limit changes upward | stay flat |
| Panic-button and self-exclusion rates | stay flat or fall |
| Notification permission revocations | fall |
| Late-night session share (00:00–06:00) | stay flat |

Ambient's whole design intent is that these do not move. Showing them is the difference between
"we grew engagement" and "we grew engagement responsibly", and the second is what the brief asks
for.

---

## 4. The event schema (small, and mostly already there)

Two new tables beside the existing ledger:

```
sessions(id, user_ref, entry_point, entry_moment_id, started_at, ended_at,
         protection_at_start)

session_events(id, session_id, at, type, target, flow_id, flow_step, meaningful)
  type ∈ { MEANINGFUL_ACTION, FLOW_STARTED, FLOW_STEP, FLOW_COMPLETED, FLOW_ABANDONED, VALUE }
```

The existing ledger already carries what we need on the other side:
`moment_id, context, arm_chosen, surface, shown_at, tapped_at, decision (shown|silent),
reward, register_ref, checked_at`.

Joining `sessions.entry_moment_id → ledger.moment_id` is what closes the loop: for any session
you can see the exact moment that caused it, which surface carried it, which arm the router
chose, and what the customer then did. That join is the analytics contract FEG inherits, and it
is worth one line on the integration slide.

---

## 5. How to prove it, given we have no real users

Three honest levels, and say which one you are showing.

**Level 1 — measured live in the demo (real numbers, tiny n).** The Surface Lab and the
"Why this?" view already show: moments processed, surfaces used, silences, alerts sent (0–1),
and — once sessions are instrumented — Time to First Action for a surface-originated session
versus an organic one. Two real numbers on stage beat twenty modelled ones.

**Level 2 — the model, with assumptions labelled.** The number chain in the concept doc, with
every assumption marked as an assumption and every benchmark cited. Judges reward a chain they
can argue with.

**Level 3 — the A/B design FEG would actually run.** This is what makes the 30% business-impact
criterion credible:

- **Randomise** at user level, 90/10 holdout, minimum 4 weeks (two full fixture cycles).
- **Control:** current broadcast push. **Treatment:** Ambient, push capped at ≤1/day.
- **Primary:** Sessions per User and Session Conversion Rate.
- **Secondary:** Time to First Action, notification permission retention, widget adoption at 90 days.
- **Guardrails:** the table in §3 — the experiment stops if any of them moves adversely.
- **Segment by** entry point and by protection state, always.
- **Powering:** at FEG's scale (~650k app MAU assumed) a 10% holdout gives ample power for a
  few-percent effect on session counts within four weeks; Value per Session is noisier and needs
  the full period.

---

## 6. What to put on the metrics slide

One table, three columns — metric, how we measure it, what we expect Ambient to move — and one
line under it: *"Every number is measured on the device from the same ledger that explains each
decision to the customer, and the guardrails are reported in the same view as the gains."*

That sentence answers the business-impact criterion and the compliance criterion at once.
