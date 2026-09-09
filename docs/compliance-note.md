# Compliance note

How this prototype meets the requirements that apply to a gambling operator's engagement surfaces, and where those requirements are enforced.

**The central claim:** the rules that matter are enforced by the **type system and an automated guard**, not by convention or code review. If a rule can be broken by writing ordinary, plausible code, we treat the model as wrong and change the model. Each section below names the file and the test.

---

## 1. Applicable requirements

| Area | Instrument | Where it bites |
|---|---|---|
| Unfair commercial practices / inducement | **UCPD 2005/29/EC**; **Digital Fairness Act** (proposal) — dark patterns, manipulative nudging | Notification copy, consent dialog, loyalty mechanics |
| Consent for direct marketing | **GDPR** Art. 4(11), 6(1)(a), 7; **ePrivacy** 2002/58/EC Art. 13 | Marketing consent dialog |
| Data minimisation & purpose limitation | **GDPR** Art. 5 | Facts carried to the narrator; ledger contents |
| Loot boxes / randomised paid rewards | Belgian Gaming Commission position; NL, DE positions | Loyalty rewards |
| Responsible gambling duties | Croatian gambling law; operator self-exclusion & limits obligations | Protection state machine, limits, reality check |
| Sensitive data on a locked device | Practical privacy | Live Update lock-screen redaction |

This is a prototype for evaluation, not a certified production build. It handles **no real customer data** and takes **no real money** — see §7.

---

## 2. No surface ever displays money

**Enforced by:** `MomentFacts` (`ambient/narrator/NarratorContract.kt`) has **no field** for a stake, an odd, a balance, a return or a payout. A narrator template cannot print a figure that does not exist in its input.

The same rule extends outward:
- `SlipSurfaceState` — the type every notification and widget is drawn from — has no money field.
- `LoyaltyLine` on the season widget carries a tier and a badge count and nothing else.
- `DefaultMomentBuilder.factsFor` copies a leg's *description* and never its `odds`, and a slip's leg *counts* and never its `stake`. The comment in that file states the rule at the point it would be broken.

**Belt and braces:** `NarratorGuard` independently rejects any generated line containing a currency symbol (`€ $ £ EUR`) or a two-decimal figure — the shape of an odd or an amount — while allowing scores (`1–0`), minutes (`61'`) and periods.

**Tests:** `RecapNoMoneyTest`, `NarratorGuardTest`, `SpokenTextGuardTest`.

---

## 3. No inducement, no urgency, no dark patterns

**Enforced by:** `NarratorGuard.BLOCKED_PHRASES` (`ambient/narrator/NarratorGuard.kt`), applied to **every** line the app generates before it reaches a surface, in **English and Croatian**.

Blocked, with the Croatian equivalents alongside:

- **Inducement:** "bet now", "back them", "you're missing", "don't miss", "miss out", "last chance" · *"kladi se"*, *"kladite se"*, *"ne propusti"*, *"propuštaš"*, *"zadnja prilika"*
- **Money:** "free bet", "cash out", "win money"
- **Urgency / time pressure:** "expires soon", "hurry", "act now", "don't wait", "ends today", "only today", "while it lasts" · *"požuri"*, *"požurite"*, *"istječe"*, *"samo danas"*, *"ne čekaj"*

The guard is deliberately **not** over-broad. A short `SAFE_PHRASES` list protects legitimate copy that contains a blocked word — "deposit limit" (the responsible-gambling tool) and "no hurry" — and there are tests asserting that *"three days in a row"* and *"No hurry, and nothing expires"* still pass. **A guard that rejects valid copy is worse than one that is slightly permissive**, because it gets switched off.

**Coverage:** all **96** narrator combinations (12 moment types × 4 tones × 2 languages) are run through the guard in CI.

**Tests:** `TemplateNarratorTest`, `UrgencyGuardTest`, `KickoffCopyTest`, `LadderNarratorTest`.

**Model output is guarded too.** The on-device LLM is not trusted: if its output fails the guard, the app falls to the next rung of the ladder and the "written by" badge on screen reflects what actually produced the text. A model cannot talk its way past the rule.

---

## 4. Loyalty without gambling — three structural rules

The competitive context matters here: rival operators ship missions, XP and tiers where the rewards are bonus funds, free spins and mystery boxes, and players continue wagering to earn points while losing money. That is the harm pattern this design exists to avoid, and it is avoided in the types.

| Rule | Enforcement | File |
|---|---|---|
| **No mission may require a wager** | `MissionType` has no `PLACE_BETS` / `WAGER_AMOUNT` case, and `Mission` has no stake, amount or bet-count field. *"Place five bets"* cannot be expressed. | `ambient/loyalty/LoyaltyModel.kt` |
| **No reward may be gambling credit** | `PerkCategory` has no `FREE_BET`, `BONUS`, `CASHBACK` or `ODDS_BOOST`. Rewards are match tickets, merchandise, experiences, feature access, partner vouchers, charity donations. | same |
| **No randomised rewards** | `Perk` carries a stated `badgeCost` and `stock` and **no probability, rarity or draw field**. A mystery box has nowhere to live. Redemption is deterministic end to end, including the code, which is derived rather than rolled. | same |

**The test that matters:** every perk in the catalogue is redeemable by a customer who has self-excluded. Gambling credit would fail that test by definition.

`SCAN_SHOP_SLIP` deserves a note: it is **not** a wagering mission. The slip exists in the customer's hand before the mission is aware of it, so nothing can be progressed by betting more.

Adding any of the forbidden cases means editing an enum — a visible decision in a diff, argued about — rather than a line of JSON nobody reviews.

**Tests:** `LoyaltyCatalogueTest` asserts against the **enums**, not merely the fixtures.

**And the positive case:** *"Set a deposit limit"* is a first-class mission worth three badges — the most of any mission in the catalogue. On screen it is deliberately styled as care rather than as a task to grind.

---

## 5. Marketing consent

`ui/consent/MarketingConsentDialog.kt`, shown once, **after** the system notification permission.

GDPR requires consent to be freely given, specific, informed and unambiguous. Consent extracted by interface pressure is none of those — so a manipulative dialog would also produce consent that does not hold up. The design follows from that:

- **Both buttons are identical** — same size, shape, weight, border. No emphasised Accept beside a de-emphasised Decline.
- **Nothing is pre-selected.** `Consents.offers` defaults to `false`; this is opt-in, never opt-out.
- **No third "not now"** that quietly means ask again.
- **No loss framing.** The vocabulary `NarratorGuard` blocks everywhere else is not used here either.
- **Specific:** what arrives (promotions, prize games, bonus offers), how often (at most weekly, never 22:00–09:00), that it is separate from bet updates, that it is two taps to undo, and that declining changes nothing else.
- **Asked once.** `UserState.marketingConsentAsked` is a separate field from the consent itself, because "not asked" and "said no" are different states. Re-asking a settled no is nagging, and a yes given to stop a dialog reappearing is not freely given.
- **Granular:** match updates (service) and offers (marketing) are two independent switches, not one combined toggle — `Consents(matchUpdates, offers)`.
- **Withdrawable:** both switches live under Responsible gaming and can be changed at any time.

---

## 6. Responsible gambling

| Control | Implementation |
|---|---|
| **Protection state machine** | `NORMAL / CALM / UNVERIFIED / BLOCKED`, derived from a synthetic exclusion register plus the customer's own limits. Evaluated **before** relevance — an excluded customer's moment is never scored — and again at the point of drawing, because a stored snapshot can outlive the decision that allowed it. |
| **Self-exclusion honoured across surfaces** | `BLOCKED` and `UNVERIFIED` yield an **empty** allowed-surface set. A protection change also **takes down a card already posted**, tied to the state rather than to our bookkeeping. |
| **Calm Mode** | Triggered by risk state, a panic action, or any limit at ≥80% used. Removes alerts, strips the slip framing from copy, pauses loyalty missions **without erasing what was earned**, and replaces the launcher shortcuts with protection tools. |
| **Deposit / loss / time limits** | Editable by the customer. The picker offers modest amounts, ascending, with **nothing preselected** — an operator nudging toward a higher ceiling is the problem, not the fix. |
| **Reality check** | `SESSION_LENGTH`, at the customer's own `realityCheckMinutes`. Casino's only moment is *how long you have been playing* — deliberately not a nudge to keep playing, which is the harm pattern in the highest-harm vertical. **Exempt from the one-alert-a-day budget**, because a reality check is not marketing and must not be silenced by a marketing cap. |
| **Attention budget** | ≤1 alert per day, quiet hours honoured, 60-second dedupe, and a relevance floor below which nothing is shown at all. |
| **Auditability** | Every decision *and every silence* is written to the ledger with its reason. A "Why this?" screen renders it. |

---

## 7. Data protection

- **No real customer, player or personal data** is present anywhere in this repository. All fixtures in `assets/mock/` are synthetic and written for the prototype.
- **No network calls, no backend, no API keys, no telemetry.** There is no HTTP client in the dependency graph.
- **The LLM runs on-device.** No prompt, fixture or user input is transmitted to Google or anyone else.
- **Data minimisation:** the ledger records decisions — moment type, score, surface, tone, reason, reward — and deliberately carries no identifiers beyond a synthetic demo player reference.
- **Lock-screen redaction:** the Live Update is `VISIBILITY_PRIVATE` **with a public version supplied**. On a secure lock screen the customer sees the fixture and score; the slip — how many legs are home, the narrated line, the Listen action — is withheld. A score is a fact about football; a slip reveals that the phone's owner has money on the game.
- **Permissions:** `POST_NOTIFICATIONS` and `CAMERA` only. The camera is used solely to read a barcode locally; no image is stored or transmitted.
- **No secrets are committed.** `.gitignore` excludes `local.properties` and `.env`; there are no credentials in the project because there is nothing to authenticate against.

---

## 8. Trademarks and brand assets

- **No club logo is used, scraped or bundled.** Crests are generated at runtime (`ambient/identity/CrestBitmap.kt`) as a shield from two colours, a pattern and two initials. Club crests are trademarks; this avoids them by construction.
- **Club colours are approximations**, chosen for mutual distinguishability and contrast, and are stated in the source as **not licensed brand values**. A production build would source them with rights cleared.
- PSK/FEG branding appears because this is a replica built for an FEG challenge. Reference screenshots in `docs/screens/` are used to build the replica, not redistributed as product assets.

---

## 9. Known limitations (stated deliberately)

1. **Croatian copy is model-checked, not native-checked.** It should be reviewed by a Croatian speaker before any real use.
2. **Club colours are unlicensed approximations** (§8).
3. **The exclusion register is synthetic.** A production build would integrate the national register; the interface (`ExclusionRegister`) is the seam for it.
4. **Age verification is stubbed** to "verified" so the demo shows anything at all — `AgeAssurance` is the seam, and it defaults the *unsafe-looking* way only because a hackathon audience has not done KYC. `UNVERIFIED` is fully implemented and demonstrable.
5. **A Live Update swipe** suppresses reposting but does not yet feed the learning router; alert swipes do.
6. This is a **prototype for evaluation**. It is not certified, not penetration-tested, and takes no real money.
