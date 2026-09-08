# Ambient — conformance against the FEG Hackathon EU & Croatia guide

Source: *FEG Hackathon 2026 — EU & Croatia Regulatory Compliance Guide for Participants*, track: Croatian brand (PSK), as of 4 September 2026.
Verdict: **7 of 10 requirements already satisfied by the architecture. 3 gaps require build work. 1 framing correction.**

---

## 1. The framing correction — read this first

Our compliance argument has been written around **Czech** law (the panic button, the RVO register). The track is the **Croatian brand (PSK)**, and the guide names a different binding instrument:

> *Act on Games of Chance (Zakon o igrama na sreću) plus its implementing Regulation on Measures on Socially Responsible Organisation of Games of Chance — this is binding, not soft law: it requires ID/age verification and a check against a register of excluded players before play is allowed.*

And it gives a direct build instruction:

> *Build any "self-exclusion" or "age-gate" concept in your prototype as a **register-check pattern, not just a UI checkbox**.*

**What this changes.** Today our `UserState.riskState` is a toggle in the RG settings screen. That reads as a UI checkbox — exactly what the guide says not to do. It must become a *check against a register* that returns a result, with the result gating play and every surface. Same demo, different shape, and the shape is the point.

Keep the Czech material as a secondary-market note in the deck — it strengthens the "one engine, per-market config" argument — but lead with Croatia.

---

## 2. Conformance matrix

| # | Requirement | Status | Where it lives in Ambient |
|---|---|---|---|
| 1 | **GDPR** — no real personal data; synthetic/sample only | ✅ | All data from `assets/mock/*.json`. No registration, no real profiles, no production feed |
| 2 | **GDPR** — data minimisation, privacy by design (Art. 25) | ✅ **Strong** | Personalisation happens entirely on the device. Slips, interests, bandit counters and ledger never leave the phone. `MomentFacts` structurally has no field for identity, stake, balance or odds |
| 3 | **ePrivacy** — real opt-in, never pre-ticked | ✅ | Two independent consent toggles (*Match updates*, *Offers*), unbundled from the OS notification permission and from T&Cs, asked at separate moments, withdrawable in one tap |
| 4 | **RG** — no dark patterns (no false urgency, forced continuity, disguised ads) | ✅ **Strong** | Silence is the default output; caps and quiet hours are token-bucket rules; only *real* deadlines are ever shown (kickoff, half-time). §10.5 of the concept doc lists the three tactics we deliberately did not build |
| 5 | **RG** — no inducements to at-risk or self-excluded customers | ✅ **Strong** | Safety gate runs before scoring or routing, so the inducement path does not exist in code. Offers confined to Zone B behind four gates; consent never outranks the gate |
| 6 | **EU AI Act** — AI must not be manipulative or exploit vulnerabilities | ✅ | The bandit chooses only among arms the rules already permit; it can never widen reach, break a cap, or reach a protected user. Safety-critical logic is deterministic, not learned |
| 7 | **NIS2 / security hygiene** | ✅ | No secrets, no credentials, no network calls in the demo build |
| 8 | **Accessibility — EAA / WCAG 2.1 AA** | ❌ **GAP** | Never specified. Contrast, touch targets, keyboard/switch navigation, content descriptions, dynamic type |
| 9 | **Croatia** — self-exclusion as a **register check**, not a checkbox | ❌ **GAP** | Currently a settings toggle. Must become a checked register with a returned result |
| 10 | **18+ at every relevant entry point**, not only registration | ❌ **GAP** | Never specified — and our OS surfaces *are* entry points |
| — | **AMLD / KYC** | ➖ N/A | We build no onboarding. One line in the deck: KYC sits before Ambient, unchanged |
| — | **eIDAS 2.0 / EUDI Wallet** | 🎁 **Opportunity** | The guide calls attribute-based "over 18" proof *"a good forward-looking design choice."* Our age gate can use exactly that pattern |
| — | **DSA** — recommender-system transparency | 🎁 **Opportunity** | Our "Why this?" ledger already is this. We have simply never named the instrument |

---

## 3. The three gaps, and what to build

### Gap A — Self-exclusion as a register check (highest priority)

**Why it matters:** it is the one place the guide gives a direct build instruction, and it is Croatian binding law rather than soft principle.

**What to build.** Replace the `riskState` toggle with a mock national register, modelled as a service the app *queries* and whose answer it must respect:

```
interface ExclusionRegister {
  suspend fun check(playerRef: String): RegisterResult
}

data class RegisterResult(
  val excluded: Boolean,
  val reason: ExclusionReason?,   // SELF_EXCLUDED, COOLING_OFF, OPERATOR_IMPOSED
  val checkedAt: Instant,
  val validUntil: Instant,        // result is cached, then must be re-checked
  val registerRef: String         // "HR-MOF-REG · synthetic"
)
```

Rules that make it a register check rather than a checkbox:
- **Checked before play is allowed**, not only in settings — on app start, before a bet is placed, and before any Ambient surface renders
- The result **expires** (`validUntil`) and is re-checked; a stale result fails closed
- The app **cannot write** to the register — it only reads. Adding yourself to it goes through a request flow, which is what the panic button triggers
- Every check is written to the ledger with its `registerRef` and timestamp — auditable
- Demo control moves from a toggle to a **"synthetic register" panel** where you seed the register's contents, then watch the app react to what it returns

**Demo moment this creates:** add the demo player to the synthetic register on the laptop, and within one check interval every surface on the phone goes calm — because the *register* said so, not because a switch was flipped.

### Gap B — 18+ at every entry point

The guide: *"age-gating should apply at every relevant entry point in your prototype, not only at registration."* Our OS surfaces are entry points nobody usually thinks about.

**What to build:**
- An `AgeAssurance` check in the safety gate, evaluated **before** relevance — same position as the register check
- **Widget:** unverified → a neutral "Verify to continue" card, never odds, never a match
- **Live Update / status chip:** never created for an unverified account
- **Launcher shortcuts:** unverified → only "Verify" and "Help" are published
- **Deep links:** any link from a surface re-checks before opening the target screen
- Verification screen uses the **EUDI Wallet attribute-proof pattern**: request the single attribute `age_over_18 = true`, display exactly what is being shared, never a document image or an ID number. Label it clearly as a synthetic wallet simulation

This is the highest-value item after Gap A, because the guide explicitly praises this pattern and no other team will apply age-gating to a *widget*.

### Gap C — Accessibility to WCAG 2.1 AA

An explicit ground rule, and currently unaddressed. Minimum credible pass:

| Check | Target |
|---|---|
| Text contrast | ≥ 4.5:1 body, ≥ 3:1 large text — verify the sampled PSK palette, especially `textSecondary #A9A9B4` on `surface #17171C` |
| Non-text contrast | ≥ 3:1 for odds-button borders, the live dot, focus rings |
| Touch targets | ≥ 48 × 48 dp — odds buttons and widget actions are the risk |
| Content descriptions | Every icon-only control; every odds button reads as "Liverpool win, 1.85" not "1.85" |
| Live regions | Score and minute changes announced, not silently swapped |
| Dynamic type | Layout survives 200% font scale without clipping |
| Focus | Visible focus state for keyboard and switch access; logical order |
| Motion | Respect "remove animations"; the pulsing live dot must have a static alternative |
| Colour alone | Won/lost legs carry an icon, not just green/red |

Budget ~45 minutes. It is cheap, it is an explicit ground rule, and a judge who turns on TalkBack will find out in ten seconds.

---

## 4. Two free wins to name in the deck

**EU AI Act, Article 5.** The prohibition is on AI that manipulates or exploits vulnerabilities. Our answer is structural, not a claim: the learned component sits *after* the safety gate and the attention budget, chooses only among already-permitted options, and is rewarded for staying silent. Say it in one line: *"The AI cannot reach a vulnerable user, because it never selects who is reached — the rules do."*

**DSA, recommender transparency.** The bandit is a recommender system. The DSA expects users to be told why they see what they see. "Why this?" already does it in plain words, on device. Name the instrument on the compliance slide — it costs nothing and shows range.

---

## 5. One hard rule to restate

> *No real player or customer data, no real identity documents, and **no live production data feeds or APIs** may be used in any hackathon build.*

The VPN access to the live web product is for **understanding and replicating the interface**. Do not wire the app to any live endpoint, even if one is reachable. Our Match Simulator is the correct and compliant source, and it is worth saying so on the slide rather than leaving it implicit.

---

## 6. Claude Code prompt — Step 13A: compliance hardening

```
Read docs/Compliance_Conformance.md. We are implementing the three gaps it identifies.
Do these in order; each is independently demoable. Do not start the Moment Engine work yet.

=== A · Self-exclusion as a register check (replaces the riskState toggle) ===

Create ambient/compliance/ExclusionRegister.kt:

  enum class ExclusionReason { SELF_EXCLUDED, COOLING_OFF, OPERATOR_IMPOSED }

  data class RegisterResult(
    val excluded: Boolean,
    val reason: ExclusionReason?,
    val checkedAt: Instant,
    val validUntil: Instant,
    val registerRef: String
  )

  interface ExclusionRegister { suspend fun check(playerRef: String): RegisterResult }

Implement SyntheticExclusionRegister backed by a local JSON file the demo can edit
(assets/mock/register.json, copied to app storage on first run so it is writable).
registerRef = "HR-MOF-REG · synthetic". Results are valid for 15 minutes, then re-checked.

Wire it so the check happens at three points, not one:
  1. app start
  2. before a bet can be placed  (block placement, show the register's reason)
  3. inside the safety gate, before ANY Ambient surface renders
A missing, failed or expired result must FAIL CLOSED — treat as excluded. Add a unit test
for that specific case; it is the one a reviewer will look for.

The app must never write to the register. The panic button instead creates a
RegisterEntryRequest (48-hour block applied locally and immediately, plus a pending
"request to be added" state) — that separation is the point of the pattern.

Log every check to the decision ledger with registerRef, checkedAt and the outcome.

In the developer section, replace the three-state "Simulate state" radio with a
"Synthetic register" panel: list the entries, add/remove the demo player, and show when
the next re-check is due. Nothing in the app may set riskState directly any more.

=== B · 18+ at every entry point ===

Create ambient/compliance/AgeAssurance.kt with a stored AgeProof
(verified: Boolean, method: String, provenAt: Instant) and a check used by the safety gate
BEFORE relevance scoring.

Gate these entry points, not just the app:
  - Widget: unverified → neutral "Verify to continue" card. No odds, no match, no money.
  - Live Update / status chip: never created for an unverified account.
  - Launcher shortcuts: unverified → publish only "Verify" and "Help".
  - Deep links from any surface: re-check before opening the target screen.

Build a verification screen using the EUDI Wallet attribute-proof pattern: it requests the
single attribute age_over_18 = true, shows the user exactly what is being shared, and never
asks for a document image, a photo, or an ID number. Label the screen clearly:
"Synthetic wallet — demo only. No real identity data." Store only the boolean and a timestamp.

=== C · Accessibility to WCAG 2.1 AA ===

Audit and fix:
  - Contrast: check every text/background pair in PskTheme against 4.5:1 (body) and 3:1
    (large text and non-text). Report the failures and adjust the tokens minimally —
    textSecondary #A9A9B4 on surface #17171C is the first one to measure.
  - Touch targets ≥ 48x48dp — odds buttons and widget actions especially.
  - contentDescription on every icon-only control. An OddsButton must read as
    "Liverpool win, 1.85", never "1.85".
  - Score and minute updates announced via a live region, not silently replaced.
  - Layout survives 200% font scale with no clipping — test it and fix what breaks.
  - Visible focus indicators, logical focus order.
  - Respect reduced-motion: the pulsing live dot needs a static alternative.
  - Won/lost legs must carry an icon as well as colour.

Deliver a short report: each check, pass or fail, and what you changed.

Build, install, and confirm: adding the demo player to the synthetic register makes every
surface go calm within one check interval, and an unverified account sees the neutral
widget card instead of a match.
```
