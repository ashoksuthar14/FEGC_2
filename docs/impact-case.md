# Impact case & cost-value analysis

**The proposition in one line:** an engagement engine that is paid to stay quiet — and can prove it did.

---

## 1. The problem, stated honestly

Every operator's notification stack answers one question: *how do we get them back into the app?* That question has an obvious optimal answer — send more, use loss framing, time it for when they are losing — and the industry has found it. The result is a race that ends in the same place for everyone:

- **Customers mute the channel.** Once notifications are off they are off for everything, including the messages that would have been welcome. The operator loses the surface permanently for a short-term click.
- **Regulators are closing it anyway.** The UCPD already covers manipulative practice; the **Digital Fairness Act** proposal targets dark patterns and manipulative personalisation directly. Loyalty schemes paying in bonus funds and mystery boxes are exactly what is being looked at.
- **It concentrates harm.** A system optimised for re-engagement is, by construction, most effective on the customers who respond most to prompting — which is not a neutral group.

Ambient inverts the objective: **is this moment worth a customer's attention?** Most of the time it is not, and the engine says so out loud and records why.

---

## 2. What is actually built

Not a concept deck. A working Android app in which:

- Every notification passes one pipeline — protection, relevance, attention budget, router, guard — and **every exit writes a ledger row, including the ones that render nothing.**
- The router **learns from four signals**, including the one nobody instruments: *the customer came back on their own after we stayed quiet* (`+0.50`). Without that reward a bandit can only learn from the times it spoke, which biases it toward speaking.
- The words are written on-device by Gemma 3 270M and checked by an automated guard that rejects inducement, money and urgency in two languages.
- The compliance rules are enforced by **types**, not conventions (see [compliance-note.md](compliance-note.md)).
- 103 automated tests, of which the load-bearing ones assert *claims* rather than code paths.

---

## 3. Value to FEG

### 3.1 The surface is an appreciating asset

The economics of a notification channel are the economics of a reputation. Each unwanted push costs a small, permanent probability that the customer disables the channel for good. An engine that spends attention only when it has something worth saying **compounds in the opposite direction**: the messages that do arrive are trusted, and the surface stays available.

That is not a soft claim in this build — it is instrumented. The ledger separates *shown* from *silent*, with a reason on each, so the ratio is measurable rather than asserted.

### 3.2 Regulatory readiness as a product feature

The Digital Fairness Act is a proposal today. Operators will have to demonstrate that engagement mechanics are not manipulative — and the ordinary answer, "our policy forbids it", is weak. This prototype's answer is different:

> The rule cannot be broken by writing ordinary code, and here is the test that proves it.

`MissionType` has no wagering case. `PerkCategory` has no free bet. `MomentFacts` has no money field. `NarratorGuard` rejects inducement in English and Croatian across all 96 narrator combinations. **That is an audit artefact**, and producing it later, retrofitted onto a system built the usual way, costs far more than building it in.

### 3.3 The differentiator competitors cannot copy quickly

Anyone can ship missions and tiers in a sprint. What is hard to copy is the *position*: a loyalty scheme that gives a badge for setting a deposit limit, and rewards that a self-excluded customer could still redeem. Copying that means giving up bonus-funded loyalty, which is the point of theirs.

> *Every competitor rewards you with a free bet. We reward you with a match ticket — and with a badge for setting a limit.*

### 3.4 It extends beyond sport, correctly

The same engine pointed at casino produces a **reality check**, not a re-spin prompt — the one message a slot machine never volunteers, exempt from the marketing cap because it is not marketing. It also makes a control that already existed (`realityCheckMinutes`) actually function; it was a setting nothing read.

---

## 4. Value to the customer

| | |
|---|---|
| **Fewer, better interruptions** | A relevance floor, a one-a-day cap, quiet hours, and a router that learns what this person actually wants. |
| **Nothing that pressures** | No countdowns, no "last chance", no loss framing. Enforced, not promised. |
| **Protection that actually acts** | Calm Mode strips alerts and pauses missions without erasing what was earned; exclusion removes a card already on the lock screen. |
| **Privacy on a locked phone** | The lock screen shows the score, never the slip. |
| **An explanation** | "Why this?" renders the ledger. The customer can see why they were interrupted — or why they were not. |
| **Loyalty worth having** | Rewards redeemable by someone taking a break. |

---

## 5. Cost-value analysis

### 5.1 Build cost

| Item | Cost |
|---|---|
| Prototype (this repository) | One hackathon cycle. ~28,500 lines of Kotlin, 178 source files, 103 tests. |
| Production hardening | The seams are already in place: `ExclusionRegister`, `AgeAssurance`, `MatchClock`, `TicketLookup` and `Narrator` are interfaces with mock implementations. Productionising means implementing them, not rearchitecting. |
| Persistence | The `Ledger` is deliberately shaped like a DAO. Moving to Room is mechanical. |
| Ongoing | No inference cost, no serving infrastructure, no per-message spend — the model runs on the customer's device. |

### 5.2 Running cost, versus the usual approach

| | Conventional push stack | Ambient |
|---|---|---|
| Message generation | Server-side templates or hosted LLM — per-message cost | **On-device. Zero marginal cost.** |
| Personalisation data | Profile shipped to a server | **Never leaves the device** |
| Infrastructure | Campaign tooling, delivery, storage | **None. No backend at all.** |
| Data-protection exposure | Behavioural profiles held centrally | Materially reduced — nothing is transmitted |
| Volume | Grows with campaign ambition | **Capped by design** (≤1 alert/day) |

The on-device choice is not only a privacy position; it removes the per-message cost that makes sending *more* look free to whoever owns the campaign budget.

### 5.3 The cost that is real, and worth naming

**Fewer messages means fewer sessions in the short run.** An engine that stays quiet will, measured over a fortnight against a conventional stack, show lower notification-driven session counts. Any honest business case has to accept that trade.

The argument for taking it: notification-driven sessions are borrowed against channel permission, and the borrowing is invisible until the channel is gone. The metric worth watching is not sessions-per-push but **notification opt-out rate and channel survival over months** — and the ledger in this build is what makes that measurable per decision.

### 5.4 Risk if not addressed

| Risk | Exposure |
|---|---|
| Digital Fairness Act enforcement against manipulative mechanics | Retrofit under deadline, on a live product |
| Loyalty scheme re-classified as inducement | Redesign of a shipped mechanic customers already hold balances in |
| Loot-box position extended to randomised loyalty rewards | Belgium already treats loot boxes as gambling |
| Channel death by opt-out | Slow, quiet, and effectively permanent |

---

## 6. How to judge whether it works

Measurable with what the app already records:

1. **Silence ratio** — decisions ending `NOTHING` over total. The engine's whole claim.
2. **Correct-silence reward rate** — unprompted opens within an hour of a silence.
3. **Alert survival** — swipe-away rate per arm, already learned per context bucket.
4. **Opt-out rate** — the number the conventional approach quietly degrades.
5. **RG engagement** — deposit limits set, reality checks acknowledged; the deposit-limit mission makes this directly attributable.

---

## 7. Honest limitations

- Effectiveness claims are **argued, not yet measured**. There is no A/B result; a prototype cannot produce one. What is demonstrated is that the mechanism exists, runs, and records enough to be measured properly.
- All data is synthetic (see [compliance-note.md §7](compliance-note.md)).
- Croatian copy needs a native reviewer before real use.
- Production integration — exclusion register, age assurance, real fixtures — is stubbed behind interfaces.
