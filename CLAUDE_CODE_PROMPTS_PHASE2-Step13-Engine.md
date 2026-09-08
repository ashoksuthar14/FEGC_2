# Phase 2 · Step 13 — The engine: protection layer, moment engine, learning router

Prerequisite: step 14 built, so `SurfaceController` exists and every surface renders from
`SlipSurfaceState` / `WidgetState`. Step 13 replaces your hand on the Surface Lab buttons with
a decision engine.

**Serial estimate 5h. Fast path below: ~2h 30.**

Step 13 parallelises better than step 14 did, because it is almost all pure Kotlin with no UI
and no device dependency. Three modules, one shared schema, and a short assembly pass.

---

## 13.0 — What changes, and what you delete

| Now | After step 13 |
|---|---|
| `toProtectionStateTemp()` from §14.0b | **Deleted.** Replaced by the real register check + age assurance |
| Surface Lab buttons call `SurfaceController` | The engine calls it. The Lab stays as a manual override for the stage |
| 👍/👎 write to the ledger, nothing reads them | The router reads them |
| `MatchClock` drives updates directly | Events flow through the engine, which decides whether they surface at all |

The `SurfaceController` interface does not change. Not one renderer is touched.

---

## 13.0c — 15-minute spike: prove the bandit is demoable

The demo claim is *"it learned that in thirty seconds, from two gestures."* If it takes 200
interactions to visibly flip, the beat dies on stage. Prove the shape before building the rest.

```
Write a single JVM unit test — no Android, no UI — as a throwaway spike.

Implement a minimal Thompson sampler: arms are Beta(wins+1, losses+1) counters in a map.
Simulate one context bucket with 4 arms where arm B is genuinely best (reward probability
0.8 vs 0.3 for the others).

Then answer three questions and print the numbers:
  1. After how many interactions does arm B become the most-sampled choice in 9 of 10 runs?
  2. If I pre-seed arms with 8 prior observations, how many NEW interactions are then needed
     to flip the winner from A to B?
  3. What prior strength makes two consecutive negative rewards visibly change the choice?

Report the three numbers. Do not build anything else yet.
```

**How to read it.** Question 2 is the one that matters — it sets how many interactions to
pre-seed before the demo so that two gestures on stage produce a visible flip. If the answer is
above ~6, raise the reward magnitudes or lower the prior strength until it is 2–4, and note the
values. That tuning IS the demo.

---

## 13.1 — FAST PATH

Same three techniques as step 14: all shared schema up front, three agents on disjoint packages,
tests only where they protect a claim. One addition — an explicit assembly pass, because unlike
step 14 the three modules have to be chained together.

### Rules for the parallel run

- **Only the main session runs Gradle.** Agents write files and stop.
- **Room entities and the DAO are defined in the foundation prompt**, not by an agent. Two agents
  editing a Room schema is the one thing guaranteed to cost you an hour.
- Each agent gets an exact file allowlist and must stop rather than edit outside it.
- Complete files, not incremental edits.

### Prompt 1 · Foundation — shared types, schema, interfaces (~25 min)

```
Read docs/Ambient_Final_Spec.md sections 5 (architecture), 6 (protection layer spec) and the
learning-loop section of docs/Ambient_Concept_v2.md (§5).

Create package ambient/engine/ and define everything the three workstreams share. Write complete
files. No behaviour yet — interfaces, data types and the Room schema only. No tests yet.

1. ambient/engine/Domain.kt
   enum class MomentType { GOAL_ON_SLIP, LEG_DECIDED, LEG_LOST, SLIP_SETTLED,
                           KICKOFF_FOLLOWED, HALFTIME, MINUTES_REMAINING, AWAY_DIGEST }
   enum class Surface { LIVE_UPDATE, WIDGET, IN_APP, ALERT, NOTHING }
   enum class TimeBucket { MORNING, DAY, EVENING, NIGHT }

   data class MatchEvent(...)        // from the simulator: match, type, minute, score, scorer
   data class Moment(
     val id: String,
     val type: MomentType,
     val facts: MomentFacts,          // reuse the step-12 type unchanged
     val slipId: String?,
     val matchId: String,
     val ownership: Ownership,        // ON_MY_SLIP, DECIDES_A_LEG, FOLLOWED_TEAM, NEITHER
     val createdAt: Instant
   )
   data class Decision(
     val momentId: String, val score: Double, val surface: Surface,
     val tone: Tone, val armId: String, val sampled: Double,
     val protection: ProtectionState, val reason: String
   )

2. ambient/engine/Interfaces.kt — the three seams, one per agent:
     interface ProtectionEvaluator { suspend fun evaluate(): ProtectionState }
     interface RelevanceScorer     { fun score(moment: Moment): Double }
     interface Router              { suspend fun choose(moment: Moment, score: Double,
                                                       allowed: Set<Surface>): Decision
                                     suspend fun reward(armId: String, context: String, r: Double) }

3. ambient/engine/ledger/ — Room, defined here so no agent touches the schema:
     @Entity LedgerEntry(id, momentId, momentType, contextBucket, protection, score,
                         surface, tone, armId, sampled, reason, shownAt, tappedAt,
                         dismissedAt, reward, createdAt)
     @Entity RegisterCheck(id, registerRef, checkedAt, validUntil, excluded, reason)
     @Entity ArmStat(contextBucket, armId, wins, losses, updatedAt)   // composite PK
     @Entity SessionRow(...)  // the entry-point fields from docs/Metrics_Instrumentation.md §1
     LedgerDao with the queries each agent needs — write them all now.
     AmbientDatabase with all four entities, version 1, no migrations.

4. ambient/engine/AmbientEngine.kt — the pipeline shell. Chains
   ProtectionEvaluator → MomentBuilder → RelevanceScorer → AttentionBudget → Router → Narrator
   → SurfaceController → Ledger, with every collaborator injected and every method TODO().
   The assembly pass fills this in.

5. AppContainer: expose engine, protectionEvaluator, scorer, router, ledgerDao.

6. Delete ambient/surfaces/ProtectionStateTemp.kt and move ProtectionState into
   ambient/engine/Domain.kt. Fix the imports in the surfaces package — that is the only
   change outside ambient/engine/ in this whole step.

Run ./gradlew assembleDebug once and fix what does not compile.
```

### Prompt 2 · Three agents in parallel (~50 min wall clock)

```
Spawn three agents in parallel. Each writes ONLY its allowlist and does NOT run Gradle —
I build once when all three are done. If an agent needs something outside its allowlist it must
stop and tell me, not edit the file.

AGENT 1 — Protection layer (spec: Ambient_Final_Spec.md §6, Compliance_Conformance.md §3)
  Files: ambient/engine/protection/*
    ExclusionRegister.kt, SyntheticExclusionRegister.kt, AgeAssurance.kt,
    DefaultProtectionEvaluator.kt
  - Register result cached 15 min; expired, missing or failed ⇒ EXCLUDED. Fail closed.
  - Checked at app start, before bet placement, and inside evaluate().
  - Read-only: the panic button creates a RegisterEntryRequest plus an immediate local 48h block.
  - Every check writes a RegisterCheck row.
  - evaluate() returns BLOCKED | UNVERIFIED | CALM | NORMAL, in that precedence.
  - Backed by assets/mock/register.json copied to app storage on first run so the demo can edit it.

AGENT 2 — Moment engine (spec: Ambient_Final_Spec.md §5)
  Files: ambient/engine/moment/*
    MomentBuilder.kt, DefaultRelevanceScorer.kt, AttentionBudget.kt
  - MomentBuilder joins a MatchEvent with local state (open slips, follows, mutes) → Moment?
    Returns null when the event touches nothing the user owns. No moment, no work downstream.
  - Scorer, deterministic and auditable: on-my-slip 0.6 · decides-a-leg +0.3 · followed 0.3 ·
    urgency from minutesRemaining · decay for a repeat of the same moment type within 10 min.
    Clamp 0..1. No model, no randomness.
  - AttentionBudget returns the allowed Set<Surface>: strips ALERT when the 1/day bucket is
    spent or quiet hours / system DND is active; strips everything but IN_APP during quiet hours;
    dedupes an identical moment inside 60s.
  - Read quiet hours and DND from the existing settings; do not invent new storage.

AGENT 3 — Learning router (spec: Ambient_Concept_v2.md §5)
  Files: ambient/engine/router/*
    Arms.kt, BanditRouter.kt, RewardTable.kt
  - Arm = surface × tone × timing (4 × 4 × 3 = 48). Context bucket = momentType × timeOfDay.
  - Beta(wins+1, losses+1) per (context, arm) in the ArmStat table. Thompson sampling.
  - choose() may only return an arm whose surface is in `allowed`. Prune first, sample second.
    If allowed is empty or score < 0.3, return the NOTHING arm — and still write a ledger row.
  - Rewards: tap +1.0 · tap after 30 min +0.5 · dismissed <2s −1.0 · dismissed later −0.3 ·
    ignored/expired −0.2 · thumbs ±1.5 · muted −2.0 on every arm in that context ·
    correct silence +0.5 (NOTHING chosen and the user opened the app within the hour).
  - Weekly decay ×0.98 on all counters.
  - Cold-start priors from the spike in 13.0c — use the numbers it produced, and put them in one
    named constant block so I can retune them in ten seconds before the demo.

When all three report, I will build.
```

### Prompt 3 · Assemble, test what matters, wire to the surfaces (~35 min)

```
Build, fix cross-agent compile errors, then fill in AmbientEngine's pipeline:

  onEvent(event):
    protection = protectionEvaluator.evaluate()
    if (protection == BLOCKED)   → ledger row (reason "blocked"), return
    moment = momentBuilder.build(event, protection) ?: → ledger row ("not mine"), return
    score = scorer.score(moment)
    allowed = attentionBudget.allowedSurfaces(moment, protection)
    decision = router.choose(moment, score, allowed)
    if (decision.surface == NOTHING) → ledger row, return
    text = narrator.narrate(moment.facts, decision.tone, language)
    surfaceController.<render per decision.surface>(state)
    ledger row with everything

Then subscribe the engine to MatchClock/MatchSimulator events, and route Surface Lab's buttons
through the engine as well so the Lab still works as a manual override on stage.

Now write EXACTLY these six tests — no others. Each protects a claim a judge could break:

  1. registerExpired_isTreatedAsExcluded            — fail-closed (Croatian law claim)
  2. blockedOrUnverified_producesNoMoment           — protection precedes relevance
  3. router_neverSelectsPrunedArm                   — 100 draws, allowed set of 1
  4. alertBudget_holdsUnderBurst                    — 20 events, at most 1 ALERT
  5. quietHours_allowNoInterruptingSurface
  6. correctSilence_increasesNothingArm             — the "rewarded for silence" claim

Skip: scorer arithmetic, builder permutations, Room round-trips, everything else. We verify
those by driving the app.

Then install and drive the simulator: report how many events produced a surface, how many were
silent, and how many alerts were sent.
```

### Prompt 4 · The three screens that make it visible (~25 min)

```
These three views are what turn the engine from invisible logic into demo material.

1. Synthetic register panel (Developer section) — replaces the old three-state radio.
   List the register entries, add/remove the demo player, show when the next re-check is due,
   and a "check now" button. Nothing in the app may set ProtectionState directly any more.

2. "Why this?" sheet — reachable from any moment in the in-app inbox and from Settings.
   Render a ledger row in plain words:
   "Goal on your slip, evening. You tapped lock-screen updates 6 of 7 times and ignored witty
    pushes 4 times, so this went to the lock screen in plain tone."
   Include a compliance filter showing only register checks, blocked renders, Calm transitions
   and offers withheld — that filter is the DSA transparency answer and the compliance slide.

3. Bandit debug view — the 48 arms of the current context as bars, updating live as rewards land,
   with the pre-seed values shown. This is the step-5 demo beat; it must be legible from two
   metres away on a phone screen, so few bars, big, labelled.

Then run the full demo script from Ambient_Final_Spec.md §9 end to end and tell me where it
breaks.
```

### Where the time goes

| Phase | Wall clock |
|---|---|
| 13.0c bandit spike | 0h 15 |
| Prompt 1 foundation | 0h 25 |
| Prompt 2 three agents in parallel | 0h 50 |
| Prompt 3 assemble, six tests, wire | 0h 35 |
| Prompt 4 the three screens | 0h 25 |
| **Total** | **~2h 30** |

### Deliberately deferred

Bandit persistence across reinstall · decay scheduling (apply lazily on read instead of a job) ·
session/entry-point instrumentation from `Metrics_Instrumentation.md` (worth 20 min later if the
metrics slide needs a live number) · per-market rule config (a constant for now) · every test
beyond the six.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| Everything is silent | The scorer never clears 0.3 — check `Ownership` is being set; a moment with `NEITHER` should not have been built at all |
| Everything surfaces | `allowed` is not being passed into `choose()`, or the budget returns the full set |
| Bandit looks random on stage | Priors too strong or rewards too small — retune with the constants from 13.0c |
| Calm Mode takes a tick to appear | `ProtectionState` change is not pushing an immediate refresh — it must, per §14E |
| Room crash on launch | Schema changed after install. Uninstall the app rather than writing a migration |
| Register check runs on every event and lags | Cache it for its validity window; only `evaluate()` reads the cache |

## Done when

1. The simulator drives every surface with no hand on the Surface Lab.
2. Most events produce nothing, and the ledger says why for each one.
3. Adding the demo player to the synthetic register turns every surface calm within one check interval.
4. Two gestures visibly move the bandit bars.
5. The six tests pass.
6. "Why this?" explains a real decision in a sentence a non-engineer understands.
