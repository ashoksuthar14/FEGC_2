# Ambient v2 — "Your bet, tracked like a flight"

FEG Hackathon · Challenge 2 · Native App Experience
Android-first · solo build · 24 hours · Google Pixel + Windows

---

## 1. What the brief is really asking

Read the brief as three sentences.

FEG's app only exists while it is open. The moments a bettor cares about — a goal that settles a leg, a kickoff of a team they follow, a slip that just won — happen when the app is closed, and they follow those moments in a scores app (in FEG's home market that is often Livesport/FlashScore, a Czech company). FEG's only tool between sessions today is broadcast push, which users mute, and on Android a denied notification permission is effectively permanent.

The ask is: use the phone's own surfaces (widget, lock screen, status bar, always-on display, watch, shortcuts, voice) so the product feels alive **without** becoming another push spammer, and with responsible gambling and GDPR built into the mechanism, not added as a filter.

What the judges reward, in order: a **credible number chain to business outcome with a cost view (30%)**, a better experience for the user not just the funnel (20%), a mechanism the brief did not already list (15%), a realistic production path (15%), something FEG can integrate fast (10%), compliance embedded (10%).

Two things follow. First, "a widget and smarter pushes" is the floor — every team will have that. Second, the deck we have today ("score the moment, pick the quietest surface") is a good *spine* but a weak *show*: the judges never see anything new on the screen, the sub-1 MB trained model is not credible to build in 24 hours with no data, and the surfaces still show generic feed content.

Ambient v2 keeps the spine and changes what appears on the glass.

---

## 2. The idea in one paragraph

Treat a placed bet the way Uber treats a ride and Flighty treats a flight: an ongoing, user-initiated, time-bound journey that the operating system is *designed* to track. A live bet slip becomes an Android 16 **Live Update** — a status chip in the status bar ("2/3 ✓ · 61'"), an un-collapsible card on the lock screen and always-on display, with one progress segment per leg. A small **on-device LLM (Gemini Nano)** narrates each moment in the user's own language from structured facts only — never odds, never a call to action. A **Moment Engine** decides, for every event, which surface (if any) earns it, with silence as the default output and a decision ledger the user can open ("Why am I seeing this?"). And the same surfaces flip into **Calm Mode** for at-risk, limit-approaching or self-excluded players: no money, no odds, only the match — with the Czech-law **panic button** available directly on the widget and the notification, one tap from the lock screen.

The reframing does the heavy lifting: the user's *own position* is the only content that is both maximally relevant and compliance-safe. A generic odds push is marketing. "Your Sparta leg is still alive, 18 minutes left" is information about a contract the user already holds — like a delivery status.

On top of that spine sit four things that make the entry hard to copy: an **on-device learning loop** (a contextual bandit that learns from taps, dismissals and a 👍/👎 which surface, tone and time each user actually responds to); **personal, not promotional messaging** written in the user's language and preferred tone from local data only; a **"While you were away" digest** on unlock; and **"the paper ticket comes alive"** — scan a retail betting slip and it is tracked on the lock screen exactly like an online bet. Sections 5 and 6 detail these.

---

## 3. Why this is different from what the room will build

| Typical entry | Ambient v2 |
|---|---|
| Widget shows the feed / next matches | Widget is a **moment card** with four states (pre-match, live, settled, idle) driven by the user's own slips and follows |
| Push with better targeting | **Silence is the default**. Push is the last surface, capped at ≤1 interrupting push/day; most moments render silently on chip, lock screen, widget |
| Cloud LLM chatbot inside the app | **On-device Gemini Nano** narrates moments in CZ/SK/PL/RO/HR from structured facts. Zero tokens billed, zero personal data leaves the phone |
| Responsible gambling = a settings page | **Calm Mode** is a rendering mode of every surface; **panic button on the lock screen** (Czech law requires it "permanently accessible in the game interface" — we put it on the OS) |
| Ask for notification permission at first launch | **Earned opt-in**: widget first (needs no permission), permission asked at the moment a live bet is placed ("Track this slip on your lock screen?"), purpose-based channels so users mute a *thing*, not the app |
| Black-box relevance | **Explainable**: every decision (including "show nothing") is logged on-device; "Why this?" sheet; feedback (tap/dismiss/👍👎) tunes a contextual bandit — honest, small AI that visibly learns during the demo |
| Same message to everyone | **Personal, not promotional**: tone, length, surface and time are chosen per user by the bandit; content comes from the user's own slips, teams and habits stored locally. Zomato-style voice, never Zomato-style coupons |
| Nothing happens until the app opens | **"While you were away"**: one AI-written card on unlock summarises what happened to *your* things — no push needed |
| Online only | **Retail ticket comes alive**: scan the barcode on a paper Fortuna slip and it becomes a Live Update — FEG's retail base gets the same experience with zero acquisition spend |
| Static launcher icon | **Dynamic shortcuts**: long-press the icon → "Sparta – Slavia (live)", "My slip 2/3", "Panic button" |
| Bonus and voucher pushes to drive opens | **Two zones (§10)**: offers live in an in-app inbox behind their own consent, active-player and market checks; OS surfaces stay offer-free. The behavioural levers are kept, the inducements are not — and the design survives the bonus bans now spreading across the EU |
| Custom server-side personalisation | **Fan-out by match, personalise on device**: one FCM topic message per match event reaches every follower; the phone joins it with the user's slip. No per-user push pipeline |

Platform fit is itself an argument: Google's own Live Update guidelines say they are for *ongoing, user-initiated, time-sensitive* activities and explicitly not for ads, promotions or upcoming events. A live slip qualifies; a bonus push does not. The OS policy and the gambling guardrail point the same way — that is what "compliance by design" looks like.

---

## 4. Architecture

Everything runs on the phone. The only server component in production is FEG's existing event stream, delivered per match (FCM topic), never per user.

```
 FEG event stream / demo Match Simulator      Retail ticket scan     Device signals
   (goal, card, HT, FT, kickoff, leg settled)   (barcode → legs)      (unlock, DND, time)
                 │                                   │                     │
                 ▼                                   ▼                     ▼
 ┌───────────────────────────────────────────────────────────────┐
 │ 1. SAFETY GATE  (runs first, always)                          │
 │    self-excluded? · RVO flag · panic button active?           │
 │    limit ≥ 80% used? · consent per purpose? · age OK?         │
 │    market × channel rules (see §10) · isPromotional?          │
 │    → CalmMode | Normal | Blocked                              │
 ├───────────────────────────────────────────────────────────────┤
 │ 2. MOMENT BUILDER                                             │
 │    joins event with local user state: open slips (legs),      │
 │    followed teams, muted matches → Moment{facts, ownership}   │
 ├───────────────────────────────────────────────────────────────┤
 │ 3. RELEVANCE SCORER  (is this moment worth anything?)          │
 │    rules: on-my-slip 0.6 · decides-a-leg +0.3 · followed 0.3  │
 │    · minutes-left urgency · duplicates decay                  │
 ├───────────────────────────────────────────────────────────────┤
 │ 4. ATTENTION BUDGET                                           │
 │    quiet hours (user + system DND/Bedtime) · token bucket     │
 │    (e.g. 6 silent updates/h, 1 interrupt/day) · dedupe        │
 ├───────────────────────────────────────────────────────────────┤
 │ 5. LEARNING ROUTER  (how should it appear?)  — see §5         │
 │    contextual bandit picks arm = surface × tone × timing      │
 │    from Beta(win, loss) counters per context bucket           │
 │    hard floors: score <0.3 → NOTHING; Calm → no money/promo;  │
 │    push only if budget allows                                 │
 ├───────────────────────────────────────────────────────────────┤
 │ 6. NARRATOR  (Gemini Nano, ML Kit Prompt API, structured out) │
 │    facts + tone + language + local habits → one sentence      │
 │    hard guard: blocklist (odds, bet, cash out, bonus, €),     │
 │    length cap, template fallback if model unavailable         │
 │    also writes the "While you were away" digest on unlock     │
 ├───────────────────────────────────────────────────────────────┤
 │ 7. RENDERERS                                                  │
 │    Live Update (ProgressStyle, 1 segment per leg)             │
 │    Glance widget (4 states + digest state, 👍/👎 actions)     │
 │    Heads-up notification (rare) · In-app Moments inbox        │
 │    Dynamic launcher shortcuts · Voice answer (stretch)        │
 ├───────────────────────────────────────────────────────────────┤
 │ 8. DECISION LEDGER (Room)  →  "Why this?" sheet, debug panel  │
 │    FEEDBACK COLLECTOR: tap, time-to-tap, dismiss, ignore,     │
 │    👍/👎 → reward → updates the bandit arm that was used      │
 └───────────────────────────────────────────────────────────────┘
```

Key design rules: the safety gate runs before anything else, so for a self-excluded user the inducement path does not exist in code. Interest data, slips, limits and the ledger live only in local storage. The narrator is fed *facts*, never odds or prices, and its output is checked by a deterministic guard before rendering. Every branch — including "do nothing" — writes a ledger row, which is what makes the "silence" claim measurable.

### Surfaces and what each shows

| Surface | Normal mode | Calm mode |
|---|---|---|
| Status-bar chip | `2/3 ✓ · 61'` | `1–0 · 61'` |
| Lock screen / AOD Live Update | Legs as progress segments, narrated line, "Open match" | Score only, "Take a break" + Panic button action |
| Home-screen widget | Pre-match countdown → live legs → settled result → "While you were away" digest → idle (next followed fixture); 👍/👎 on every card | Match card only; reality check ("2h 10m this week"); panic button; limits shortcut |
| Heads-up notification | Only for settlement or a leg decided, ≤1/day, tone chosen by the bandit | Never |
| Launcher shortcuts (long-press icon) | "Sparta – Slavia (live)", "My slip 2/3", "Scan a ticket" | "Panic button", "My limits", "Scores" |
| Retail ticket | Scanned slip appears everywhere above like an online bet | Same, money hidden |
| Voice (stretch) | "How's my slip?" → on-device answer, deep link | Answers about scores only |
| In-app Moments inbox | Everything, with "Why this?" | Everything, with "Why this?" |

---

## 5. The learning loop — an algorithm that lives on the phone

### 5.1 What it learns

Not *whether* a moment matters (that is the rule-based scorer — deterministic, auditable) but *how it should appear* for this user: which surface, which tone, at which time of day. Every time we show something and the user ignores it, we should try something different next time. Every time they tap, we should do more of that.

### 5.2 The algorithm: contextual Thompson sampling

Each decision picks one **arm** from a small grid:

| Dimension | Values | Count |
|---|---|---|
| Surface | lock-screen Live Update · widget only · heads-up notification · nothing | 4 |
| Tone | plain · witty · stats-heavy · one-liner | 4 |
| Timing | now · hold until next unlock · hold until quiet hours end | 3 |

That is 48 arms. Each arm keeps two counters per **context bucket** — *moment type* (leg decided · goal on my slip · followed team kickoff · settlement · digest) × *time of day* (morning · day · evening · night) — so 20 buckets × 48 arms = 960 pairs of integers. Trivial storage, trivial compute.

Each counter pair is a Beta distribution: `Beta(wins + 1, losses + 1)`. When a moment arrives:

1. The safety gate and attention budget prune arms that are not allowed (Calm Mode removes any arm showing money; an exhausted push budget removes the notification arms; score < 0.3 leaves only "nothing").
2. For every remaining arm, draw one random sample from its Beta.
3. Use the arm with the highest sample. Log arm + context in the ledger.

That is Thompson sampling. It explores automatically when it is unsure (wide Beta) and exploits when confident (narrow Beta). Early on it tries several tones and surfaces; after 15–20 interactions the winning arm dominates visibly — which is why the demo can show it learning live.

### 5.3 Reward signals (implicit + explicit)

| Signal | How we detect it | Reward |
|---|---|---|
| Tap on the surface within 30 min | pending intent → ledger | **+1.0** (win) |
| Tap after 30 min | same, timestamp diff | +0.5 |
| Dismiss in under 2 s | `setDeleteIntent` / widget dismiss with timestamp | **−1.0** (loss) |
| Dismiss later, no tap | same | −0.3 |
| Shown, never touched, expired | expiry worker | −0.2 |
| Explicit 👍 / 👎 on widget or notification action | Glance `ActionCallback` / notification action | **±1.5** (strong) |
| User mutes the match or a channel | settings hook | −2.0 on every arm of that context |

Partial rewards update the counters fractionally (wins += 0.5). A decay of 0.98 per week on all counters keeps the model responsive if habits change. The "nothing" arm also gets rewards: if the user opens the app on their own within an hour after we stayed silent, "nothing" earns +0.5 — silence that was right is rewarded, which is how the system learns *not* to notify.

### 5.4 Guarantees the bandit cannot override

The bandit chooses only among arms the rules allow. It can never turn a Calm Mode user into a promo recipient, never exceed the push cap, never break quiet hours, never show a moment the scorer rated irrelevant. This keeps the learned part small and the safety-critical part deterministic — and it is what lets us tell judges "the AI cannot misbehave here by construction".

### 5.5 Explainability

Every ledger row stores: moment, context bucket, arm chosen, sampled probability, reward received. The "Why this?" sheet renders it in plain words: *"Goal on your slip, evening. You tapped lock-screen updates 6 of 7 times and ignored witty pushes 4 times, so this went to the lock screen in plain tone."* A debug screen shows the 48 arms of the current bucket as bars that move as you tap and swipe during the demo.

### 5.6 Cold start

New users start with priors seeded from sensible defaults (Live Update + plain + now for slip moments; widget-only for followed-team news) so day one behaves well before any feedback exists. In production the priors could be the population averages computed from opt-in aggregated ledgers — but per-user learning stays on the device.

---

## 6. Beyond widgets and pushes — the extra layer

### 6.1 Personal, not promotional (the "Zomato style", done legally)

Zomato's messages work because they sound like a person who knows you, and they use your own history. We can do both from data that never leaves the phone: followed teams, open slips, when you usually check, which matches you muted, your language.

Example outputs (tone chosen by the bandit, written by Gemini Nano):

- *plain:* "Sparta – Slavia kicks off at 20:00. Two of your legs are on it."
- *witty:* "Friday, 20:00, Sparta. Same as the last six weeks. We'll keep the seat warm."
- *stats-heavy:* "Sparta: 4 wins in 5 at home. Your leg needs a win. Kickoff 20:00."
- *one-liner:* "Sparta 20:00 · 2 legs."

The hard line, and why it is a strength: in FEG's markets, and under the brief's guardrail, the message may carry **information about things the user already chose** (their teams, their slips, their matches) but never **offers** — no bonus, no boosted odds, no "bet now", no free bet. The narrator's guard enforces this mechanically with a blocklist and a schema that has no field for prices. So we get Zomato's voice without Zomato's coupons, which is exactly what a regulator wants and what a competitor cannot safely copy.

Prompt design: the model receives `{facts, tone, language, habit_hints}` where `habit_hints` are pre-computed on device ("checks app most Fridays 19:30–21:00", "follows Sparta for 6 weeks") and returns `{headline ≤ 60, detail ≤ 120}`. No identity, stake, balance or odds ever enter the prompt.

### 6.2 "While you were away" — the unlock digest

Trigger: `ACTION_USER_PRESENT` (device unlocked) after ≥ 60 minutes of no interaction, or the first unlock after quiet hours end. The engine collects every moment logged since the last session, ranks them, and asks the narrator for a single card: *"2 legs won, Plzeň drew, Sparta kick off in 40 min."* Rendered as the widget's digest state and, if the bandit chose it and budget allows, as one notification. This replaces the three or four pushes a normal app would have sent overnight with zero pushes and one card at the moment the user is provably looking at the phone. It is the cleanest answer to "between sessions we're invisible".

### 6.3 The paper ticket comes alive (retail → OS surface)

FEG is the largest betting operator in Central and Eastern Europe with a large retail network. Retail customers hold a paper slip and wait. In the app: **Scan a ticket** → ML Kit barcode scanning reads the ticket code → lookup (mock JSON in the demo; FEG's retail ticket API in production) returns the legs → the ticket becomes a normal slip in the engine, so it gets the Live Update, the widget, the digest and the shortcuts exactly like an online bet. No deposit, no account funding, nothing promotional — the user just sees their own paper bet live on their lock screen. Business angle: these are existing FEG customers (in scope), and every scan is a retail user forming a digital habit with zero acquisition spend. Nobody else in the room will build this because it needs FEG's business context, not a generic app idea.

### 6.4 Dynamic launcher shortcuts

`ShortcutManagerCompat` publishes up to four shortcuts that change with state: live match on my slip, my slip status, "Scan a ticket", and in Calm Mode "Panic button" and "My limits". Long-press the icon and the user is one tap from the moment. It is in the brief, it takes under an hour, and it makes the Calm Mode story stronger (help is one press away without opening the app).

### 6.5 Voice (stretch)

On-device speech → Nano intent parse (`slip_status | team_next | open_match`) → local answer + deep link. Production path is Android 16 **AppFunctions**, which lets Gemini call `getSlipStatus()` / `followTeam()` directly; that integration is in private preview today, so the demo uses in-app voice and the pitch names AppFunctions as the roadmap.

---

## 7. Where AI actually sits (and where it deliberately does not)

**Narration (perceived AI).** A small language model running entirely on the phone turns structured facts into one sentence in the user's language. No API key, no server, no token bill, nothing personal leaving the device — which is what makes the GDPR story in §9 true rather than aspirational. §7.1 picks the runtime and the model.

**Learning (quiet AI).** The contextual bandit in §5 — small, explainable, needs no training data, learns per user on the device, rewarded for silence as much as for taps.

**What is not AI on purpose.** The relevance scorer is rules. The safety gate is rules. Frequency caps are a token bucket. The panic button is a button. Judges will trust a system where the safety-critical parts are deterministic and the learned part is bounded.

### 7.1 Choosing the on-device model

The job is deliberately tiny: take a JSON object of facts and emit `{headline ≤ 60 chars, detail ≤ 120 chars}` in one of four tones, in the user's language. That is text *shaping*, not reasoning or knowledge — so the smallest class of model is the right one, and a large model would be a liability (APK size, battery, latency, and more room to say something non-compliant).

**Three ladders, tried in order at runtime.** The app defines one `Narrator` interface and picks the best available implementation on first launch; the rest of the engine never knows which one ran.

| # | Option | What it costs | When it is used |
|---|---|---|---|
| **A** | **ML Kit GenAI Prompt API → Gemini Nano** (`com.google.mlkit:genai-prompt`) | **0 MB in the APK**, 0 €, no download you host | First choice. The model already lives in the OS (AICore), so nothing ships with the app. Requires Gemini Nano v3: Pixel 9–10 series and recent flagships. Beta, but zero-integration-cost |
| **B** | **LiteRT-LM + Gemma 3 270M or Gemma 3 1B** (`com.google.ai.edge.litertlm:litertlm-android`) | ~300 MB (270M int8) or ~529 MB (1B int4 QAT) downloaded on first run, not bundled | Devices without Nano, and any market/OEM where AICore is absent. Fully self-hosted — FEG serves the file from their own CDN, so still no third-party call |
| **C** | **Template narrator** (pure Kotlin string templates, four tones, per language) | ~0 MB, 0 ms | Old devices, low storage, model download declined, or model unavailable. Also the demo-day insurance policy |

**Why not MediaPipe.** The older `com.google.mediapipe:tasks-genai` LLM Inference API is now in maintenance-only mode; Google's own docs point Android projects to the LiteRT-LM Kotlin API. Use LiteRT-LM for anything new.

**Model choice inside option B.** Gemma 3 270M is the headline candidate — 270M parameters, INT4-quantised, and Google measured **0.75% battery for 25 conversations on a Pixel 9 Pro**, their most power-efficient Gemma. It is explicitly built for "high-volume, well-defined tasks" like structuring text, which is exactly our job, and explicitly *not* built for open conversation — also fine, because we never expose a chat box. Two caveats: Android GPU acceleration for 270M was still work-in-progress at the time of writing (CPU is adequate for one short sentence), and a 270M model's Czech, Polish and Romanian output is noticeably weaker than a 1B's. Where CEE-language quality matters more than footprint, Gemma 3 1B IT int4-QAT is the step up: 529 MB, and on a Samsung S24 Ultra it benchmarks at ~379 tok/s prefill and ~55 tok/s decode on CPU with ~1 GB memory — far more headroom than a 40-token sentence needs.

**Making a small model safe and consistent.** Three techniques do most of the work, and they matter more than model size:

1. **Constrained output.** Ask for structured output (a fixed schema with two short string fields), not free prose. The model fills slots; it cannot ramble.
2. **Facts in, never knowledge out.** The prompt carries every fact the sentence needs. The model is forbidden from adding information, so its weak world-knowledge never becomes a wrong claim about a match.
3. **Deterministic guard after generation.** Length caps, a blocklist (odds, bet, bonus, cash out, currency symbols), and a language check. Anything that fails falls back to the template for that tone — so the compliance guarantee in §9 holds even if the model misbehaves.

A fourth option, if output quality in Czech ever disappoints: **fine-tune Gemma 3 270M** on a few hundred example moments. Google publishes a fine-tuning recipe for exactly this "small model, one narrow job" pattern. Out of scope for 24 hours; worth one line in the roadmap slide.

**Licence.** Gemma models ship under the Gemma Terms of Use, which permit commercial use and redistribution subject to the prohibited-use policy — FEG's legal team should sign off before production, and the template ladder means the product still works if they say no.

### 7.2 The 20-minute decision, hour one

Do this before writing any narrator code:

1. Install the **Google AI Edge Gallery** APK on the Pixel and load Gemma 3 270M. If it generates a Czech sentence acceptably, option B is proven on your exact device.
2. In a scratch activity, call ML Kit's `checkStatus()`. `AVAILABLE` or `DOWNLOADABLE` → option A is live and you ship zero megabytes. `UNAVAILABLE` → your Pixel predates Nano v3; go with option B and say so on the slide (the fallback ladder is a *strength* in the pitch, not an excuse).
3. Whatever the result, write the template narrator first. It takes 30 minutes, it defines the output contract, and it guarantees the demo runs.

### 7.3 What this buys in the pitch

Zero marginal cost per message (§8 prices the cloud alternative at roughly $110k/yr for the same volume), no personal data in flight, six markets covered by one structured event instead of six copywriting workflows, and a system that keeps working in airplane mode — which is a 10-second demo moment that makes the on-device claim undeniable.

---

## 8. Business impact — the number chain

All figures below are marked either **benchmark** (public source) or **assumption** (to validate with FEG data). The pitch should show the chain and let FEG plug in real values.

**Scale.** FEG reports ~1.3 million active players (Penta). Assume 50% are app users → **650k app MAU** (assumption).

**Mechanism 1 — opt-in retention.** Benchmark: at 3–6 pushes/day about 40% of users disable notifications; at ≤1 push/week about 10% do. Ambient routes most moments silently and caps interrupting pushes at ≤1/day (typically <2/week), moving the cohort from the 40% band toward the 10–15% band. Holding permission is the precondition for every other surface except the widget, so this is the base of the chain.

**Mechanism 2 — relevance beats broadcast.** Benchmark: users receiving targeted notifications show 39% retention past 11 sessions vs 21% for broadcast (≈1.9×). Every Ambient surface is about the user's own slip or team; there is no broadcast path.

**Mechanism 3 — tap-through.** Benchmark: Android push CTR ≈ 4.6%. Assumption: a Live Update about your own live slip earns 20–30% tap-through per slip, because it is status about something you own (flight-tracker behaviour, no public sportsbook benchmark — measure in A/B).

**Mechanism 4 — app opens and DAU/MAU.** Scenario table (assumptions in italics):

| | Conservative | Base |
|---|---|---|
| MAU engaged by a surface weekly | *15% = 98k* | *30% = 195k* |
| Incremental active days / engaged user / week | *0.5* | *1.0* |
| Incremental DAU | +7k | +28k |
| DAU/MAU (from a *25%* baseline) | 25% → 26.1% | 25% → 29.3% |
| Widget adoption at 90 days (Android MAU) | *6%* | *12%* |

**Mechanism 5 — live moments back in our app.** Every followed match and every live slip renders on our surface, not in a scores app. Metric: share of live matches with an in-app open by a user holding a slip on that match. Target: from today's baseline (FEG has it) to >50%.

**Mechanism 6 — the learning loop compounds the others.** Benchmark: peak notification CTR sits at about 2 pushes/day and falls after that; targeted beats broadcast ≈1.9× on retention. A per-user bandit is the mechanism that keeps each user at *their* peak — fewer, better-placed, better-worded moments — instead of a segment average. Assumption to validate in A/B: +20–30% relative tap-through after 20 interactions versus the fixed default routing, measured directly from the ledger (arm chosen, reward earned — the analytics are built in).

**Mechanism 7 — retail tickets.** FEG's retail customers are existing customers (in scope). Each scanned ticket creates a digital habit on the OS surface with zero acquisition cost. Metric: scans per week and the share of scanners active in the app 30 days later. Assumption: even 5% of retail slips scanned would be a new, measurable bridge that does not exist today.

**Value formula.** Incremental retained users × ARPU − cost. We deliberately do not claim more betting per user; the value is retention and reactivation of *existing* customers, which is what the brief scopes.

**Cost-value.** Production cost is small and the comparison favours on-device:

| Item | Cloud alternative | Ambient |
|---|---|---|
| Narration, 650k MAU × 2 live slips/wk × 8 moments ≈ 10.4M moments/wk, ~350 tokens in / 40 out, Gemini 3.5 Flash-Lite ($0.30 / $2.50 per M) | ≈ $2.1k/week ≈ **$110k/yr** | **$0** (on device) |
| Per-user push pipeline (segmentation, per-user sends) | ~10.4M targeted messages/wk | ~3k topic messages/wk (one per match event) — ~3,000× fewer |
| Localisation of moment copy in 6 markets | copywriting + translation per campaign | one structured event, model renders language |
| Personal data leaving device | slips, interests, behaviour to a server | none; ledger and interests stay local |

The dollar saving is real but modest; the bigger value is that FEG can ship personal, real-time surfaces in six regulated markets without building a per-user personalisation backend or exporting behavioural data.

---

## 9. Compliance by design (the 10% that also protects the other 90%)

GDPR: consent per surface purpose (Art. 6/7) captured in-app; data minimisation by architecture (nothing personal leaves the phone); transparency for automated decisions via the "Why this?" ledger; erasure = wipe local store. Czech Gambling Act (2024 amendment): panic button (48h block, offer RVO entry) is mandated "easily and permanently accessible in the game interface" — Ambient exposes it on the widget and the Live Update as well; self-exclusion register check gates every render; approaching self-set limits (bets, losses, time) switches to Calm Mode. Marketing rules across CZ/PL/RO: no bonus, no odds, no "bet now" on any OS surface — the narrator guard enforces it mechanically. Google Play: real-money gambling apps are allowed in CZ, SK, RO, HR with licence proof and RG statements; Poland is not on Google's list, so FEG's Polish distribution stays sideloaded — Ambient has no Play-Services-only dependency on the critical path (Nano and FCM are optional with fallbacks). Android Live Update policy: ongoing, user-initiated, time-bound only — a live slip qualifies; promotions never enter that renderer. Quiet hours: honour system Do Not Disturb and Bedtime plus user quiet hours; frequency caps as a token bucket; lock-screen privacy: notifications use private lock-screen visibility and the widget has a "discreet" toggle that hides all money figures. Personal messaging: it is service information about the user's own selections, not direct marketing, so it does not need a marketing consent — but we still capture a separate "personal messages" toggle, the bandit and habit hints are computed and stored only on the device (no profiling data leaves), the user can wipe them in one tap, and Calm Mode users receive no personal messages at all. The learning loop itself is explained in plain words in "Why this?", which is how we meet GDPR transparency for automated decisions without a legal-text wall.

---

## 10. The offers layer — where promotions can legally live

FEG runs promotions today and will keep running them. The question is not whether offers exist but *where they are allowed to appear*. This section is the answer, and it doubles as the "what we deliberately did not build" slide in the pitch — the one that shows the judges we know exactly where the line sits.

### 10.1 The two-zone rule

**Zone A — OS surfaces (widget, Live Update, status chip, always-on display, shortcuts): information only.** Content is limited to things the user already chose: their slip, their teams, their matches, real kickoff times. No bonus, no odds, no price, no "bet now". This is enforced mechanically — the narrator's output schema has no field for a price or an offer, and a blocklist rejects the words. Google's own Live Update policy bans promotions in that renderer, so the platform rule and the gambling rule point the same way.

**Zone B — in-app Offers inbox and (where permitted) a standard notification: promotions allowed, heavily gated.** This is where a welcome bonus, an odds boost or a free bet lives. Four gates must all pass: valid marketing consent, active player, `riskState == Normal`, and the market allows that offer on that channel.

The line between the zones is a single boolean in the renderer contract (`isPromotional`), so a compliance reviewer can audit it in one file.

### 10.2 What makes the consent valid

A marketing consent that would not survive a GDPR audit is worse than no consent, so the demo models it properly:

| Requirement | How Ambient does it |
|---|---|
| Separate, unbundled | Its own dialog, never stacked on the notification permission or the T&Cs |
| Granular | Two independent toggles: *Match updates* and *Offers*. One consent covering both is invalid |
| Informed and specific | "Bonus offers and promotions from Fortuna, up to 2 per week" — not "improve your experience" |
| Freely given | Declining costs nothing: the widget, Live Updates and digest all keep working. Nothing is conditional on it |
| Unambiguous | No pre-ticked boxes, no default-on |
| Easy withdrawal | One toggle in settings, plus an opt-out in every promotional message |
| Refreshed | Treated as stale after a long inactive period; re-asked rather than assumed |

**Timing matters as much as wording.** Asking for offers consent immediately after the notification permission is a product mistake: two asks back to back read as pushy, depress both accept rates, and on Android a denied notification permission is close to permanent — and notification opt-in retention is one of the metrics being judged. Ambient asks each permission at the moment the user wants the thing it unlocks: notifications when the first live bet is placed ("track this slip on your lock screen"), offers consent later — after a settled bet, or when the user opens the Offers screen themselves.

### 10.3 What consent does *not* unlock

1. **It does not outrank the safety gate.** A consent given six months ago means nothing once a player is self-excluded, on a cooling-off period, on the 48-hour panic block, or flagged at-risk. The gate runs first and consent is never a defence — this is exactly where operators get fined.
2. **It does not override market rules.** Romania permits bonus promotion only on the operator's own site, licensed affiliate sites, or email to opted-in *active* players — push is not on that list, so no bonus pushes in Romania even with consent. Poland bans casino promotion outright and restricts betting promotion. Consent is necessary, not sufficient.
3. **It does not open the OS surfaces.** Android reserves Live Updates for ongoing user-initiated activities; that is Google's rule and a user cannot consent it away.
4. **It does not make a chance-based mechanic free.** A prize wheel run by an operator is a promotional competition at best and a licensable game of chance at worst; if a deposit or bet is required to spin, it needs its own licence.

### 10.4 Market × channel matrix (demo config, FEG's legal team owns the real values)

| Market | Offers in-app | Offers by push | Casino promotion | Notes |
|---|---|---|---|---|
| Czech Republic | ✅ with consent | ✅ with consent | ✅ | 18+ and addiction warning required in ads; never to RVO-listed players |
| Slovakia | ✅ | ✅ | ✅ | As CZ |
| Croatia | ✅ | ✅ | ✅ | New 2025 act: ad restrictions, national self-exclusion register |
| Romania | ✅ | ❌ | ✅ | Bonus promotion limited to site / affiliates / email to opted-in active players; celebrity ads banned since Oct 2025 |
| Poland | ⚠️ betting only | ⚠️ betting only | ❌ | Casino promotion prohibited; betting ads cannot link gambling to success or relaxation |

This lives in a config file, not in code, so FEG can switch a cell off per market without a release.

### 10.5 What we deliberately did not build, and why

| Rejected idea | Why |
|---|---|
| "Spin the wheel, win a voucher" push | Three separate problems: a promotional push needs marketing consent (not the OS permission); the voucher is an inducement restricted per market; the wheel is itself a chance mechanic. Removing the word "casino" fixes only the Polish part |
| "Bet in the next 10 minutes for a chance to win X" | Manufactured urgency is an unfair commercial practice under the UCPD and a named target of the coming Digital Fairness Act; CZ and PL forbid presenting gambling as a route to money. And it cannot be guaranteed to miss at-risk users, which the brief requires |
| "We miss you" + bonus to inactive users | People go quiet after losses or after deciding to stop. CZ forbids actively enticing back those who used self-exclusion tools; RO limits bonuses to *active* opted-in players. UK Behavioural Insights Team research found at-risk gamblers are the group most attracted to free bets and most likely to say offers make them gamble beyond their intention — the tactic works best on precisely the people it must not reach |

### 10.6 The same psychology, kept legal

Every rejected tactic has a replacement that pulls the same behavioural lever using something the user already owns. These are what Ambient actually ships:

| Lever | Rejected version | Ambient version |
|---|---|---|
| Variable reward / curiosity | Spin the wheel | **"Your slip is settled — tap to reveal."** Plus a free predictions game with badges and a friends leaderboard: no stake, no prize, not gambling |
| Loss aversion / endowment | "Don't lose your bonus" | **"1 leg left"**, progress segments, "6 weeks following Sparta" — progress the user built themselves |
| Urgency | Invented countdowns | **Real deadlines only**: kickoff in 40 min, half-time, derby tomorrow |
| Reciprocity | Free bet as a gift | **Information as the gift**: form stats for a followed team, and the digest that gives back time |
| Social proof | "10,000 claimed this bonus" | **"12,400 Fortuna fans are following this match"** — aggregate, no personal data |
| Zeigarnik (unfinished business) | "Your bonus is waiting" | **"2/3"** in the status bar — the strongest single reason to open the app, and it needs no offer |
| Commitment device | "Set a reminder to claim" | **"Remind me at kickoff"** — user-initiated, so the highest tap-rate message we can send, and consented by definition |
| Reactivation | "We miss you" + voucher | **Event-based return**: "Season starts Saturday — your follow list is ready." Never to protected users; stops after two ignores instead of nagging |
| Trust as retention | — | **Calm Mode, limits, "Why this?"** Markets that leaned hardest on inducements (NL, BE) are the ones now banning them; low-pressure retention is the strategy that survives the next regulation |

The business argument for the deck: this is not a compliance compromise, it is a durability argument. The Netherlands is moving to ban online gambling advertising and bonuses outright, Belgium's bonus ban is already in force, and the EU's Digital Fairness Act is aimed squarely at urgency and nagging patterns. A retention engine built on inducements has to be rebuilt within a few years; one built on the user's own activity does not.

---

## 11. Tech stack (Windows + Pixel, one developer)

| Layer | Choice | Notes |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose | App shell replicating 4–5 Fortuna screens |
| Widget | Glance (`androidx.glance:glance-appwidget`, latest stable) | Interactive actions (Follow, Mute, Remind, Panic) via `ActionCallback` |
| Live Update | `androidx.core` NotificationCompat `ProgressStyle`, `setRequestPromotedOngoing(true)`, `setShortCriticalText`, manifest `POST_PROMOTED_NOTIFICATIONS` | Android 16 (API 36). Below 16 it degrades to a normal ongoing notification |
| On-device LLM (A) | `com.google.mlkit:genai-prompt` — Gemini Nano via AICore, structured output | 0 MB in the APK. Needs Nano v3 (Pixel 9–10). Check `checkStatus()` in hour one |
| On-device LLM (B) | `com.google.ai.edge.litertlm:litertlm-android` + Gemma 3 270M (~300 MB) or Gemma 3 1B int4-QAT (~529 MB) `.litertlm` | Self-hosted model file, downloaded on first run. **Not** MediaPipe `tasks-genai` — that API is maintenance-only now |
| On-device LLM (C) | Kotlin template narrator, four tones × language | Always present. Defines the output contract and guarantees the demo |
| Engine | Pure Kotlin module `ambient-core` with coroutines/Flow | Unit-testable; this is the integration artefact for FEG |
| Learning loop | Pure Kotlin: Beta counters in Room, `kotlin.random` for sampling (no ML library) | ~150 lines; unit tests for pruning and reward updates |
| Ticket scan | `com.google.mlkit:barcode-scanning` + CameraX, mock ticket JSON | Print 2–3 demo tickets with Code-128 barcodes beforehand |
| Digest | `BroadcastReceiver` on `ACTION_USER_PRESENT` + last-interaction timestamp in DataStore | Feeds the narrator with moments since last session |
| Shortcuts | `ShortcutManagerCompat` dynamic shortcuts (max 4) | Updated by the engine on state change |
| Storage | Room (ledger, slips, follows) + DataStore (consents, limits, quiet hours) | |
| Consent + market rules | `Consents{matchUpdates, offers, grantedAt}` in DataStore; market×channel matrix as a JSON config asset | Offers inbox screen + settings toggles; §10 is the spec |
| Scheduling | WorkManager + foreground service during live slips | |
| Event source | Local Match Simulator (scripted timelines, speed control, "next goal" button) | FCM topic listener as optional path if time allows |
| Voice (stretch) | ML Kit GenAI Speech Recognition or Android `SpeechRecognizer` → Nano intent | AppFunctions named as roadmap |
| Location (stretch) | Play Services Geofencing on a stadium polygon → "At the match" mode | Mock location in demo |
| Tooling | Android Studio on Windows, physical Pixel over USB, Claude Code/Copilot for boilerplate | No emulator for Live Updates or Nano — use the device |

Skip: Wear OS (no watch on hand; show a static complication mock in the deck), Hilt (plain constructors), a backend (nothing needs one for the demo), geofencing (dropped in favour of the ticket scan and digest).

---

## 12. 24-hour build plan (solo)

Rules: the Moment Engine and the Live Update are the demo — protect them. Cut from the bottom of the list, never the top. Commit every hour.

| Hours | Block | Output |
|---|---|---|
| 0–1 | Setup | New project (API 26 min, target 36), Pixel connected, one Live Update hello-world promoted on the lock screen, and the **20-minute narrator decision in §7.2** (AI Edge Gallery test + `checkStatus()`) so the model path is settled before any narrator code. **Decide fallbacks now.** |
| 1–4.5 | Fortuna shell | Compose screens from the VPN site's look: Home (fixtures + followed teams), Match, Bet slip, My bets, RG settings (limits, self-excluded toggle, panic button, quiet hours, consents). Sample data JSON. Keep it to 5 screens. |
| 4.5–6 | Match Simulator | Scripted timelines for 3 matches (kickoff, goals, cards, HT, FT), speed ×1/×20, debug panel with "next event" and "jump to leg decided". Emits `Event` on a Flow. |
| 6–8.5 | Moment Engine | Safety gate → builder → scorer → budget → ledger, with a fixed default router first. Pure Kotlin + unit tests (self-excluded never reaches a promo surface; cap holds; quiet hours hold). |
| 8.5–10.5 | Learning router | Beta counters in Room, arm grid, pruning, Thompson sampling, reward updates, weekly decay, cold-start priors. Unit tests: pruned arms are never chosen; 20 simulated taps flip the winning arm. Feedback collector wired to tap/dismiss intents. |
| 10.5–12.5 | Live Update renderer | ProgressStyle, one segment per leg, points for goals, chip text, deep link, dismissal detection with timestamp, private lock-screen visibility, 👍/👎 actions, Calm variant with panic action. |
| 12.5–15 | Glance widget | Five states (pre-match, live, settled, digest, idle), small + medium sizes, actions (Follow, Mute match, Remind, 👍/👎, Panic), discreet toggle. |
| 15–17 | Narrator | Template narrator first (30 min, defines the contract), then the chosen model behind the same `Narrator` interface; input `{facts, tone, language, habit_hints}`; structured output; guard (blocklist, length, language) with per-tone template fallback; CZ/EN switch. |
| 17–18 | Digest | Unlock receiver, ≥60 min gap rule, collect moments since last session, narrator writes one card, widget digest state + optional single notification. |
| 18–19.5 | Ticket scan | CameraX + ML Kit barcode, mock ticket lookup, ticket → slip in engine. Print demo tickets. |
| 19.5–20.5 | Calm Mode + shortcuts + "Why this?" + offers gate | Wire RG state to all renderers; panic → 48h lock + RVO offer; dynamic shortcuts per state; ledger sheet with plain-word reasons; bandit debug bars. Offers inbox stub with the two consent toggles and the market matrix visible (§10) — 20 minutes, and it earns the compliance slide. |
| 20.5–22.5 | Pitch | Update deck: problem read, reframing, architecture, learning loop, extra layer, number chain, cost table, compliance map, integration story. Record a 60-second backup video of the phone. |
| 22.5–24 | Rehearse | Run the demo script 3×, pre-seed the bandit with ~10 interactions so the "learning" moment is quick, charge the phone, disable auto-updates, airplane-mode test. |

Voice is dropped to a slide (AppFunctions roadmap). If time slips, cut in this order: ticket scan → digest notification (keep widget digest) → widget medium size. Never cut the engine, the learning router or Calm Mode.

### Demo script (6 minutes)

1. Open the mock Fortuna app, follow Sparta, place a three-leg live slip. Lock the phone.
2. Press "next event" on the simulator: a goal lands — the status chip shows "2/3 ✓ · 61'", the lock screen shows the legs and a Czech one-liner written on the device.
3. **Learning moment.** Swipe the update away fast. Advance again: the next moment goes to the widget instead, in a shorter tone. Tap 👍. Open the bandit debug screen and show the bars that moved. Say the sentence: *"It learned that in 30 seconds, on the phone, from two gestures."*
4. Scan a printed retail ticket: it appears on the lock screen like the online slip. *"Every paper ticket in every Fortuna shop can do this."*
5. Lock the phone, wait out the simulated hour (debug button), unlock: the "While you were away" card appears. Zero pushes were sent.
6. Toggle "self-excluded" in RG settings: every surface goes calm within a second, money disappears, the panic button appears on the lock screen and in the long-press shortcuts.
7. Open "Why this?" — events processed, surfaces used, pushes sent (0–1), reasons in plain words. Airplane mode on, advance again: still works — no server.
8. **The line we drew.** Open the Offers screen: promotions exist, behind their own consent toggle, blocked in Romania by push, invisible in Calm Mode, and never on an OS surface. Say the sentence: *"Offers are legal here and we built the gate for them — the safety gate outranks the consent, and the lock screen stays offer-free."*
9. Close with the number chain and the one-file integration point.

---

## 13. Integration story for FEG (product thinking)

`ambient-core` exposes the interfaces FEG implements — `MomentSource` (their event stream), `UserStateProvider` (open slips, follows, RG flags, consents) and optionally `TicketLookup` (retail ticket code → legs) — and one `AmbientEngine.start()` call. Renderers are pure functions of `MomentState`, so FEG's design system replaces the demo visuals without touching the engine. The bandit's arm grid, reward table and caps are a config file, so FEG's compliance team can remove a tone or a surface per market without a code change. The ledger schema doubles as the analytics contract for the brief's metrics (surface shown, surface tapped, decision silent, permission held, arm chosen, reward). Rollout: ship the widget first (no permission needed), then the Live Update behind the just-in-time permission ask, then voice via AppFunctions when Google opens it. An iOS twin uses the same engine logic in Swift with ActivityKit and WidgetKit; the surface mapping is one-to-one (Live Update ↔ Live Activity, Glance ↔ WidgetKit, AppFunctions ↔ App Intents).

---

## 14. Risks and fallbacks

Gemini Nano missing on the Pixel (needs Nano v3, i.e. Pixel 9 or 10): drop to LiteRT-LM with Gemma 3 270M, or to the template narrator — all three sit behind one `Narrator` interface, so the story stays "on-device, zero egress" and the fallback ladder itself becomes a production-readiness argument on the slide. Small-model output weak in Czech: raise to Gemma 3 1B int4-QAT (529 MB), or let the guard fall back to templates for that language — quality is checked by a deterministic guard, never by trust. Live Update not promoted by the OS on the day: it still renders as an ongoing notification on the lock screen; keep the debug flag to show `hasPromotableCharacteristics()`. Bandit looks random in a short demo: pre-seed ~10 interactions so two gestures are enough to flip the winner, and always show the debug bars so the judges see the mechanism, not luck. Barcode scan fails under stage lighting: keep a "Enter ticket code" text fallback. Judges challenge "personalised marketing to gamblers": the answer is that every message is about something the user already chose, offers cannot exist in the schema, and Calm Mode users get no personal messages at all. Time overrun: cut ticket scan, then the digest notification, then widget sizes — never the engine, the learning router or Calm Mode. Judges asking "where is the trained model?": the answer is that the safety-critical parts are deterministic by design, the learned part is a small bandit tuned on the user's own taps, and the perceived AI is the on-device narrator.

---

## Sources

- FEG about page — markets, brands, native app redesign: https://feg.eu/about-us/
- Penta Investments — FEG ~1.3M active players: https://www.pentainvestments.com/en/our-company/fortuna-entertainment-group/
- Android Live Updates (requirements, surfaces, usage criteria): https://developer.android.com/develop/ui/compose/notifications/live-update
- ML Kit GenAI Prompt API (beta, dependency, `checkStatus`, structured output, 4k token limit): https://developers.google.com/ml-kit/genai/prompt/android/get-started
- ML Kit GenAI device support (Prompt API nano-v3 = Pixel 9–10): https://developers.google.com/ml-kit/genai
- LiteRT-LM Android (Kotlin) guide — Gradle artifact, Engine/Conversation API, CPU/GPU/NPU backends: https://developers.google.com/edge/litert-lm/android
- MediaPipe LLM Inference API is maintenance-only; migrate to LiteRT-LM: https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android
- Gemma 3 270M announcement — 0.75% battery for 25 conversations on Pixel 9 Pro, built for narrow structured tasks: https://developers.googleblog.com/en/introducing-gemma-3-270m/
- Gemma 3 270M model card — 140+ languages, stated limitations: https://huggingface.co/google/gemma-3-270m-it
- Gemma3-1B-IT LiteRT builds — int4-QAT 529 MB, S24 Ultra benchmarks (379 tok/s prefill, 55 tok/s decode, ~1 GB): https://huggingface.co/litert-community/Gemma3-1B-IT
- LiteRT community model repository (.litertlm files): https://huggingface.co/litert-community
- Fine-tuning Gemma 3 270M for on-device use: https://developers.googleblog.com/own-your-ai-fine-tune-gemma-3-270m-for-on-device/
- AppFunctions overview (Android 16, Gemini private preview): https://developer.android.com/ai/appfunctions
- Push benchmarks — opt-in 81% Android / 51% iOS, CTR 4.6%/3.4%, disable rates by frequency, targeted vs broadcast retention: https://www.businessofapps.com/marketplace/push-notifications/research/push-notifications-statistics/
- Airship 2026 push benchmark report (volume trends): https://www.airship.com/blog/your-guide-to-airships-mobile-app-push-notification-benchmarks-for-2026/
- Czech gambling regulation 2024/2025 — panic button, RVO, self-limits, marketing: https://www.dlapiper.com/en/insights/blogs/mse-today/2024/changes-to-the-czech-gambling-regulation and https://arws.cz/en/news-at-arrows/regulation-of-online-gambling-in-the-czech-republic-in-2025-news-challenges-and-recommendations
- Google Play gambling country allowances: https://support.google.com/googleplay/android-developer/answer/12256011
- Gemini API pricing (Flash-Lite $0.30 in / $2.50 out per M): https://ai.google.dev/gemini-api/docs/pricing
- Push notifications as direct marketing under ePrivacy (consent required; OS permission is not consent): https://www.mccannfitzgerald.com/knowledge/data-privacy-and-cyber-risk/mobile-app-push-notifications-the-next-frontier-in-direct-marketing
- ICO — requirements for valid consent (unbundled, granular, freely given, easy withdrawal): https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/lawful-basis/consent/what-is-valid-consent/
- App Store guideline 4.5.4 — marketing push allowed only with explicit in-app opt-in and easy opt-out: https://acceptmy.app/guidelines/4-5-4-push-notification-consent-and-marketing
- Romania — bonus promotion limited to site/affiliates/email to opted-in active players; celebrity ad ban Oct 2025: https://practiceguides.chambers.com/practice-guides/gaming-law-2025/romania/trends-and-developments
- Poland — casino promotion prohibited; betting ad content restrictions: https://cms.law/en/int/expert-guides/cms-expert-guide-to-gambling-laws-in-cee/poland
- Croatia — 2025 Gambling Act, advertising restrictions and national self-exclusion register: https://www.igamingtoday.com/croatia-launches-national-self-exclusion-register-as-gambling-reform-takes-shape/
- Behavioural Insights Team — at-risk gamblers most influenced by free bets and bonuses: https://www.bi.team/impact-of-free-bets-and-promotions-on-gambling/
- UK Gambling Commission — ban on mixed-product promotions, wagering-requirement cap: https://cms.law/en/gbr/legal-updates/gambling-commission-introduces-ban-on-mixed-product-promotional-offers-and-cap-on-wagering-requirements
- Netherlands — proposed complete ban on online gambling ads and bonuses: https://next.io/news/regulation/netherlands-introduce-complete-ban-online-gambling-ads/
- Belgium — bonus ban in force: https://next.io/news/belgium-bonus-ban-enter-force-1-september/
- Dark patterns under the UCPD and the Digital Fairness Act (false urgency, nagging): https://www.osborneclarke.com/insights/digital-fairness-act-unpacked-dark-patterns
