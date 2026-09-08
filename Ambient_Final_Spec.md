# Ambient — final specification & architecture

FEG Hackathon 2026 · Challenge 2 "Native App Experience — Relevance on Every Surface" · Croatian brand (PSK) track
Supersedes the earlier concept doc where the two disagree. Gaps from FEG's EU & Croatia compliance guide are closed here.

---

## 1. Scope — what is in, and what is deliberately not

The problem statement is about **surfaces and relevance between sessions**. Everything below earns its place by serving that. Compliance items are included only where they gate a surface; the rest are named on a slide and not built.

**In scope — because a surface depends on it**

| Item | Why it is in |
|---|---|
| Excluded-players register check | Decides whether any surface may render at all |
| 18+ age assurance | Our OS surfaces are entry points; the guide requires gating at every entry point |
| Consent per purpose | Decides whether a surface may carry match updates, and separately offers |
| Accessibility (WCAG 2.1 AA) | A surface nobody can read is not a surface |
| Self-set limits, panic button | Flip every surface into Calm Mode |
| Market × channel rules | Decide which surface may carry an offer in which country |

**Explicitly out of scope — say so, do not build**

KYC and identity verification · AML monitoring, source-of-funds, PEP screening · registration and onboarding · payments and deposits · document capture of any kind · the full EUDI Wallet (we use exactly one attribute from it) · NIS2 incident reporting · CSRD, CS3D, EU Taxonomy.

One line for the deck: *"KYC and AML sit before Ambient in the funnel and are untouched by it. Ambient begins after a customer is verified, and its only interest in identity is a single boolean: is this person over 18, and are they on the register."*

---

## 2. The solution in one paragraph

A placed bet is an ongoing, user-initiated, time-bound journey — the same shape as a flight or a delivery — so the operating system already has the right surfaces for it. Ambient turns a live slip into an Android 16 **Live Update** on the lock screen and always-on display, a **status-bar chip**, and a **home-screen widget**, then decides for every match event whether that event has earned a place on any of them. Its default answer is silence. A small model on the phone writes the sentence; a small learning loop on the phone chooses the surface; and a protection layer — register check, age proof, limits, consent, quiet hours — runs before either of them, so for a protected customer the path to an inducement does not exist in code.

---

## 3. Final feature set

### A · Surfaces (the brief's core ask)

| # | Feature | Notes |
|---|---|---|
| A1 | **Live Update** — lock screen + always-on display | `ProgressStyle`, one progress segment per leg |
| A2 | **Status-bar chip** | `2/3 ✓ · 61'`, readable without unlocking |
| A3 | **Home-screen widget** | 6 states: pre-match · live · settled · digest · idle · **protected** |
| A4 | **Dynamic launcher shortcuts** | State-driven; reduced set when protected or unverified |
| A5 | **"While you were away" digest** | One card on unlock after ≥ 60 min — replaces overnight pushes |
| A6 | **Retail ticket scan** | Barcode on a paper slip → tracked like an online bet |
| A7 | **Rare alert** | Settlement or urgent only, ≤ 1/day, never in Calm Mode |

### B · Intelligence, all on device

| # | Feature | Notes |
|---|---|---|
| B1 | **Moment engine** | Safety gate → builder → scorer → budget → router |
| B2 | **On-device narrator** | Gemma 3 270M via LiteRT-LM, falling back to Kotlin templates |
| B3 | **Narrator guard** | Deterministic: length caps, money blocklist, no odds, no currency |
| B4 | **Learning router** | 48-arm contextual bandit, Thompson sampling, rewarded for correct silence |
| B5 | **Decision ledger** | Every decision including every silence, stored locally |

### C · Protection (each one gates a surface)

| # | Feature | Notes |
|---|---|---|
| C1 | **Register check** | Queried, cached with a validity window, **fails closed**. Read-only |
| C2 | **Age assurance** | Single attribute `age_over_18`, EUDI-Wallet-shaped, checked at every entry point |
| C3 | **Calm Mode** | A rendering mode of every surface — no money, no odds, no offers |
| C4 | **Panic button** | On the widget, the Live Update and the shortcuts. Local 48h block + register request |
| C5 | **Attention budget** | Quiet hours, system DND/Bedtime, token bucket |
| C6 | **Two-zone offers** | Offers in-app only, behind four gates; OS surfaces offer-free by construction |
| C7 | **Consent per purpose** | *Match updates* and *Offers* — separate, unbundled, withdrawable |

---

## 4. New features that capture the opportunities

Four additions, all directly on-brief, ~2¼ hours total.

### N1 · Spoken moments — relevance for a surface you cannot look at ★

**The reframe:** "relevance on every surface" has been read by everyone as *visual* surfaces. For a blind or low-vision customer, the surface is the screen reader — and today a betting notification read aloud is a soup of fragments: *"two slash three tick sixty-one apostrophe"*.

Ambient's narrator already produces a clean sentence, so it can produce a **spoken variant** at no extra model cost: `"Two of your three legs have won. Sparta must hold for eighteen more minutes."` Numbers expanded, symbols spoken, odds absent.

**Build:** a `spokenText` field alongside `headline`/`detail`; set it as the `contentDescription` on the Live Update, widget and shortcuts; announce score changes through a live region rather than swapping text silently. The guard checks it like any other output.

**Why it wins:** it satisfies the WCAG 2.1 AA ground rule with a *feature* rather than a checklist, it is a reading of the brief nobody else will have, and it is honestly useful. ~40 min.

### N2 · Protection status card — the register check made visible

The register check is a legal duty. Showing it turns it into trust: a small card in Responsible Gaming reading *"Protection active · register checked 4 minutes ago · next check in 11 minutes · HR-MOF-REG (synthetic)"*, with the same line surfacing on the widget in Calm Mode.

**Why it wins:** it is the visible proof that self-exclusion is a register check and not a checkbox, which is exactly what FEG's guide asked for — and it is the demo moment where you add the player to the register on the laptop and the phone reacts on its own. ~30 min.

### N3 · Age proof once, honoured everywhere

The single attribute `age_over_18 = true`, requested EUDI-Wallet-style with the user shown exactly what is shared, no document and no ID number stored. Then every surface honours it: unverified means a neutral widget card, no Live Update ever created, and only *Verify* and *Help* in the shortcuts.

**Why it wins:** the guide calls attribute-based proof "a good forward-looking design choice", and age-gating a *widget* is something no other team will think to do. ~45 min.

### N4 · Compliance view of the ledger

The ledger already exists. Add a filter that shows only protection events — register checks with timestamps and references, blocked renders, Calm Mode transitions, offers withheld — and a "copy as text" action.

**Why it wins:** it turns "compliance by design" from a claim into something a judge can scroll, and it is the DSA recommender-transparency answer in the same screen. ~20 min.

---

## 5. Architecture

Everything below runs on the phone. The only server component in production is FEG's existing event stream, delivered per match, never per user.

```
 FEG event stream / Match Simulator      Retail ticket scan      Device signals
   goal · card · HT · FT · settled          barcode → legs        unlock · DND · time
                 │                               │                      │
                 ▼                               ▼                      ▼
 ┌──────────────────────────────────────────────────────────────────────────┐
 │ 0. PROTECTION LAYER   (runs first, always — fails closed)                │
 │                                                                          │
 │    0a REGISTER CHECK   excluded-players register · cached w/ validity     │
 │                        expired or unreachable → treat as excluded         │
 │    0b AGE ASSURANCE    age_over_18 proof present and valid?               │
 │    0c PLAYER STATE     panic active? · self-set limit ≥ 80% used?         │
 │    0d CONSENT          matchUpdates? · offers? (per purpose)              │
 │    0e MARKET RULES     country × channel × isPromotional                  │
 │                                                                          │
 │    →  BLOCKED   ·   UNVERIFIED   ·   CALM   ·   NORMAL                    │
 ├──────────────────────────────────────────────────────────────────────────┤
 │ 1. MOMENT BUILDER   join event with my slips, my follows, my mutes        │
 ├──────────────────────────────────────────────────────────────────────────┤
 │ 2. RELEVANCE SCORER   rules only · on-my-slip 0.6 · decides-a-leg +0.3    │
 ├──────────────────────────────────────────────────────────────────────────┤
 │ 3. ATTENTION BUDGET   quiet hours · system DND · token bucket · dedupe    │
 ├──────────────────────────────────────────────────────────────────────────┤
 │ 4. LEARNING ROUTER    48 arms (surface × tone × timing), Thompson sample  │
 │                       chooses only among arms stages 0–3 already allowed  │
 │                       score < 0.3 → NOTHING (logged)                      │
 ├──────────────────────────────────────────────────────────────────────────┤
 │ 5. NARRATOR    facts + tone + language → headline · detail · spokenText   │
 │                Gemma 3 270M (LiteRT-LM) → templates · guard on all output │
 ├──────────────────────────────────────────────────────────────────────────┤
 │ 6. RENDERERS   pure functions of MomentState × ProtectionState            │
 │                Live Update · chip · widget · shortcuts · in-app · alert   │
 ├──────────────────────────────────────────────────────────────────────────┤
 │ 7. LEDGER      every decision, every silence, every register check        │
 │                → "Why this?" · compliance view · bandit reward            │
 └──────────────────────────────────────────────────────────────────────────┘
                 feedback: tap · dismiss · 👍👎 · correct silence  →  stage 4
```

**What changed from the previous architecture.** The safety gate has become a five-check **protection layer** with four outcomes instead of three, and it now fails closed. `UNVERIFIED` is new — a state where surfaces render in a neutral, non-gambling form rather than not at all, so an unverified customer still sees a coherent app.

### Renderer matrix

| Surface | NORMAL | CALM | UNVERIFIED | BLOCKED |
|---|---|---|---|---|
| Live Update | Legs, minute, narrated line | Score + minute only, panic action | Never created | Never created |
| Status chip | `2/3 ✓ · 61'` | `1–0 · 61'` | Never created | Never created |
| Widget | Full moment card | Match only + reality check + protection status + panic | "Verify to continue" — no odds, no match | Protection status + help only |
| Shortcuts | Live match · my slip · scan ticket | Panic · limits · scores | Verify · help | Help only |
| Alert | ≤ 1/day, settlement or urgent | Never | Never | Never |
| Offers inbox | Behind four gates | Unreachable | Unreachable | Unreachable |

---

## 6. Protection layer specification

```kotlin
sealed interface ProtectionState {
  data class Blocked(val reason: ExclusionReason, val ref: String) : ProtectionState
  data object Unverified : ProtectionState
  data class Calm(val trigger: CalmTrigger) : ProtectionState   // AT_RISK, LIMIT_NEAR, PANIC
  data object Normal : ProtectionState
}

interface ExclusionRegister { suspend fun check(playerRef: String): RegisterResult }

data class RegisterResult(
  val excluded: Boolean,
  val reason: ExclusionReason?,          // SELF_EXCLUDED · COOLING_OFF · OPERATOR_IMPOSED
  val checkedAt: Instant,
  val validUntil: Instant,               // 15 min in the demo
  val registerRef: String                // "HR-MOF-REG · synthetic"
)

data class AgeProof(val verified: Boolean, val method: String, val provenAt: Instant)
```

**Non-negotiable rules**

1. The register is checked at **three** points: app start, before a bet may be placed, and before any surface renders.
2. A missing, failed or expired result is treated as **excluded**. There is a unit test named for this.
3. The app **reads** the register and never writes to it. The panic button applies a local 48-hour block and raises a *request* to be added.
4. Age assurance stores one boolean and a timestamp. No document, no image, no identifier.
5. Every check is written to the ledger with its reference and timestamp.
6. Nothing downstream can widen what this layer permits — the learning router selects only from arms already allowed.

---

## 7. Compliance conformance (final)

| Requirement | How it is met |
|---|---|
| GDPR — synthetic data only | All data from `assets/mock/*.json`; no registration, no production feed |
| GDPR — minimisation, privacy by design | Personalisation entirely on device; `MomentFacts` has no field for identity, stake, balance or odds |
| GDPR — automated-decision transparency | "Why this?" ledger, plain words, on device |
| ePrivacy — real opt-in | Two unbundled consents, never pre-ticked, asked at separate moments, one-tap withdrawal |
| Croatia — register check before play | C1, checked at three points, fails closed, read-only |
| Croatia / RG — 18+ at every entry point | C2, including widget, Live Update and shortcuts |
| RG — no dark patterns | Silence is the default; only real deadlines; §"did not build" list |
| RG — no inducements to protected customers | Protection layer runs first; offers confined to Zone B behind four gates |
| EU AI Act — no manipulation or exploitation | The learned component never chooses *who* is reached; safety-critical logic is deterministic |
| DSA — recommender transparency | Ledger compliance view (N4) |
| Accessibility — WCAG 2.1 AA | Spoken moments (N1) plus contrast, 48 dp targets, live regions, 200% type, reduced motion, icon-not-colour |
| eIDAS 2.0 | Single-attribute age proof, EUDI-Wallet-shaped (N3) |
| No live production feeds | Match Simulator is the event source; VPN used only to study the interface |

---

## 8. Remaining build order

Phase 1 (shell) and step 12 (narrator) are done. What is left:

| Step | Work | Est. |
|---|---|---|
| 13A | Protection layer — register check, age assurance, fail-closed tests, synthetic register panel | 1h 30 |
| 13B | Moment engine — builder, scorer, attention budget, ledger | 2h 00 |
| 13C | Learning router — arms, Thompson sampling, rewards, decay | 1h 30 |
| 14 | Live Update + status chip, all four protection states | 1h 30 |
| 15 | Widget, six states, inline actions | 2h 00 |
| 16 | Digest on unlock · dynamic shortcuts | 1h 15 |
| 17 | N1 spoken moments · N2 protection status card · N4 compliance view | 1h 30 |
| 18 | Retail ticket scan | 1h 15 |
| 19 | Accessibility pass — contrast, targets, focus, 200% type, reduced motion | 0h 45 |
| 20 | Pitch, rehearsal, backup video | 2h 30 |

Cut order if time runs short: retail ticket scan → digest alert (keep widget digest) → widget sizes. **Never cut:** the protection layer, the moment engine, the Live Update, Calm Mode.

---

## 9. Demo script

1. Verify age with the wallet-style proof — show the single attribute being shared, nothing else.
2. Follow a team, place a three-leg live slip, lock the phone.
3. Advance the simulator: a goal lands. Status chip appears, lock screen shows the legs and a sentence written on the device.
4. **Turn on TalkBack.** The same card reads as a complete sentence. *"Relevance on every surface — including the one you listen to."*
5. Swipe an update away fast, advance again: the next moment goes to the widget in a shorter tone. Show the bandit's bars moving. *"It learned that in thirty seconds, on the phone."*
6. Scan a printed retail ticket — it appears on the lock screen like the online slip.
7. **On the laptop, add the demo player to the synthetic register.** Within one check interval every surface goes calm, the panic button appears on the lock screen, and the protection card says when the register was last checked. *"Not a checkbox — a register."*
8. Open the compliance view: register checks, blocked renders, offers withheld, pushes sent — zero or one.
9. Airplane mode on, advance again: everything still works.
10. Close on the number chain and the three-interface integration.
