# Phase 2 · Step 12 — On-device narrator (ML Kit GenAI → Gemini Nano)

Prerequisite: PRD-01 steps 1–11 done and installed on the Pixel.
Outcome of this step: the app can turn a set of match facts into one short sentence, written
on the device, in the user's language and a chosen tone — with a template engine underneath
that guarantees it never fails.

Order matters here. Build the contract and the template engine **first**, add Gemini Nano
**second**. If you do it the other way round and Nano turns out to be unavailable on your
device, you have nothing.

---

## 12.0 Two things to know before you start

**Gemini Nano is not a library you ship — it lives in the OS.** ML Kit talks to a system
service called AICore. There is no model file, no download URL you control, and no way to
side-load it. If the device does not have Nano v3, `checkStatus()` returns `UNAVAILABLE` and
there is no workaround. Reported symptom on unsupported hardware: `AICore failed with error
type 3-PREPARATION_ERROR and error code 606-FEATURE_NOT_FOUND`, seen even on a Pixel 8 Pro.

**Your device decides the path.** Nano v3 means Pixel 9 or Pixel 10 series. Step 12B tells
you which path you are on within five minutes. Either way the demo works — the fallback
ladder is a strength in the pitch, not an excuse.

---

## 12A — The contract, the template engine, and the guard

Paste this into Claude Code:

```
We are starting Phase 2 of the Ambient project. Read docs/PRD-01-Native-Shell.md section 11
(the four seams) for context. This step adds the narrator layer. Do NOT add any ML Kit
dependency yet — that is the next step. Build the contract and the template engine first so
the app always has a working narrator.

Create a new package `ambient/narrator/`. `ui/` must not import it directly; it goes through
AppContainer like everything else.

1. NarratorContract.kt

enum class MomentType { GOAL_ON_SLIP, LEG_DECIDED, LEG_LOST, SLIP_SETTLED, KICKOFF_FOLLOWED,
                        HALFTIME, MINUTES_REMAINING, AWAY_DIGEST }
enum class Tone { PLAIN, WITTY, STATS, ONE_LINER }
enum class NarratorLanguage { EN, HR }
enum class NarratorEngine { NANO, TEMPLATE }

data class MomentFacts(
  val type: MomentType,
  val homeTeam: String? = null,
  val awayTeam: String? = null,
  val homeScore: Int? = null,
  val awayScore: Int? = null,
  val minute: Int? = null,
  val period: String? = null,          // "1. poluvrijeme"
  val scorer: String? = null,
  val legsTotal: Int? = null,
  val legsWon: Int? = null,
  val legsLost: Int? = null,
  val myLegDescription: String? = null, // "Sparta to win"
  val minutesRemaining: Int? = null,
  val followedTeam: String? = null,
  val kickoffInMinutes: Int? = null,
  val digestItems: List<String> = emptyList(),
  val habitHints: List<String> = emptyList()  // "follows Sparta for 6 weeks"
)

data class NarratedText(
  val headline: String,   // max 60 chars
  val detail: String,     // max 120 chars
  val engine: NarratorEngine,
  val latencyMs: Long
)

interface Narrator {
  suspend fun narrate(facts: MomentFacts, tone: Tone, language: NarratorLanguage): NarratedText
}

CRITICAL — MomentFacts must never gain a field for odds, stake, balance, payout, bonus,
account id or user name. Add a comment saying so. The compliance argument in the pitch
depends on it being structurally impossible.

2. TemplateNarrator.kt implementing Narrator.
   Cover all 8 MomentTypes × 4 Tones × 2 languages. Keep it readable: a `when (type)` with a
   small `when (tone)` inside, strings pulled from a table, not a giant if-chain.
   Examples of the intended voice (write the rest in the same spirit):
     GOAL_ON_SLIP / PLAIN / EN:
       headline "Liverpool 1–0 · 61'"
       detail   "Your Liverpool win leg is still alive. 29 minutes left."
     GOAL_ON_SLIP / WITTY / EN:
       headline "That'll do · 1–0"
       detail   "Two down, one to go. Sparta just needs to hold on."
     GOAL_ON_SLIP / STATS / EN:
       headline "1–0 · 61' · 2/3 legs"
       detail   "Liverpool win needed. 29 minutes of normal time remain."
     GOAL_ON_SLIP / ONE_LINER / EN:
       headline "1–0 · 2/3 ✓"
       detail   "29 min left."
     AWAY_DIGEST / PLAIN / EN:
       headline "While you were away"
       detail   "2 legs won, Plzeň drew, Sparta kick off in 40 min."
   Croatian versions use the app's existing Croatian strings ("1. poluvrijeme", "poluvrijeme").
   Never emit a currency symbol, an odd, or an instruction to bet.

3. NarratorGuard.kt — a pure function `fun check(text: NarratedText): Boolean`, applied to
   EVERY narrator output including the template one:
   - headline ≤ 60 chars, detail ≤ 120 chars, neither blank
   - rejects any case-insensitive match of: bet, bets, betting, odds, stake, wager, bonus,
     free bet, cash out, cashout, payout, deposit, jackpot, win money, guaranteed,
     kvota, kvote, oklada, okladi, ulog, uloži, isplata, bonus, dobitak
   - rejects any of the characters € $ £ or the string "EUR"
   - rejects text containing a decimal number with exactly two decimal places (that is an
     odd or an amount) — a plain score like "1–0" or a minute like "61'" must still pass
   Write unit tests: 10 valid strings pass, and one string per blocked category fails.

4. Wire a `Narrator` into AppContainer, defaulting to TemplateNarrator.

5. Unit test: for all 8 MomentTypes × 4 Tones × 2 languages (64 cases), TemplateNarrator
   produces output that passes NarratorGuard. Run it. All 64 must pass.

Build with ./gradlew assembleDebug and run the tests before you report back.
```

---

## 12B — Capability probe (find out which path you are on)

```
Now add ML Kit GenAI and find out whether Gemini Nano is available on this device.

1. Add to app/build.gradle.kts:
     implementation("com.google.mlkit:genai-prompt:1.0.0-beta2")
   Check the ML Kit docs for a newer stable version first with WebFetch on
   https://developers.google.com/ml-kit/genai/prompt/android/get-started
   and use whatever version that page states. Do not guess.

2. Create ambient/narrator/NanoAvailability.kt:

   sealed interface NanoState {
     data object Checking : NanoState
     data object Available : NanoState
     data object Downloadable : NanoState
     data class Downloading(val percent: Int) : NanoState
     data class Unavailable(val reason: String) : NanoState
   }

   class NanoAvailabilityChecker {
     // Uses Generation.getClient(), then checkStatus() which returns FeatureStatus:
     //   UNAVAILABLE, DOWNLOADABLE, DOWNLOADING, AVAILABLE
     // download() returns a Flow of DownloadStatus:
     //   DownloadStarted, DownloadProgress, DownloadCompleted, DownloadFailed
     // Expose a StateFlow<NanoState>. Never throw — catch everything and map to
     // Unavailable(reason = throwable.message ?: "unknown").
   }

3. Add a screen at Settings → "AI diagnostics" (developer section, reachable from the
   More sheet) showing:
   - device model, Android version
   - the current NanoState, with the raw reason string when unavailable
   - a "Check again" button and a "Download model" button (only enabled when Downloadable),
     with a progress bar driven by DownloadProgress
   - the ML Kit dependency version being used

4. Install on the connected device, open that screen, and tell me exactly what it says.
   Do not proceed to the next step until I confirm the result.
```

**Read the result like this:**

| Screen says | You are on | Do next |
|---|---|---|
| `Available` | Path A | 12C as written |
| `Downloadable` → download succeeds | Path A | 12C as written |
| `Unavailable` (any reason, incl. `606-FEATURE_NOT_FOUND` / `Feature 636`) | Path C | Skip 12C. Template narrator is your narrator. Tell me and I will give you the LiteRT-LM prompt (Path B) if you want a real model instead |

---

## 12C — The Nano narrator (only if 12B said Available)

```
Gemini Nano is available on this device. Add NanoNarrator.

Create ambient/narrator/NanoNarrator.kt implementing Narrator.

Configuration:
- Build the client with ModelConfig using ModelPreference.FAST and
  ModelReleaseStage.STABLE — we want low latency for a lock-screen update, not maximum
  quality. Check the docs page
  https://developers.google.com/ml-kit/genai/prompt/android/select-model
  for the exact builder syntax before writing it.
- Pre-warm: expose `suspend fun warmUp()` that creates the client and runs one throwaway
  generation. Call it once from Application.onCreate on Dispatchers.IO. First inference is
  slow; the user must not pay for that on their lock screen.

The prompt — use this text exactly, filling the placeholders:

  You write one short status line for a sports app's lock screen.

  RULES
  - Use ONLY the facts given below. Never add teams, scores, players, times or opinions.
  - Never mention odds, stakes, money, bonuses, payouts, or betting advice.
  - Never tell the reader to do anything.
  - Write in {LANGUAGE}.
  - Tone: {TONE_DESCRIPTION}
  - Output EXACTLY two lines and nothing else:
  HEADLINE: <at most 60 characters>
  DETAIL: <at most 120 characters>

  FACTS
  {FACTS_JSON}

  where TONE_DESCRIPTION is:
    PLAIN     "neutral and factual"
    WITTY     "light and human, one small flourish, never sarcastic, never smug"
    STATS     "lead with the number that matters"
    ONE_LINER "as short as possible, telegraphic"
  and LANGUAGE is "English" or "Croatian".
  FACTS_JSON is MomentFacts serialised with kotlinx.serialization, nulls omitted.

Execution:
- Run on Dispatchers.IO.
- Wrap the call in withTimeout(2500). On timeout, exception, or unparseable output, fall
  back to TemplateNarrator with the same facts/tone/language.
- Parse the two lines by their HEADLINE:/DETAIL: prefixes; trim; if either is missing,
  that counts as unparseable.
- Apply NarratorGuard to the parsed result. If it fails the guard, fall back to the
  template. Log which rule tripped, at debug level.
- Set engine = NANO only when the model's own output was used; TEMPLATE otherwise.
  This field is what the demo displays, so it must be truthful.

Then create ambient/narrator/LadderNarrator.kt implementing Narrator: takes a list of
Narrators and returns the first successful result. AppContainer builds it as
[NanoNarrator, TemplateNarrator] when NanoState is Available, and [TemplateNarrator]
otherwise. Nothing else in the app knows which one ran.

Build, install, and confirm the app still launches with no ANR at startup.
```

---

## 12D — Narrator Lab (your test bench and a demo moment)

```
Build a developer screen "Narrator Lab", reachable from the More sheet under a
"Developer" heading. This is how we test the narrator and it is also a slide in the pitch.

Layout:
- A dropdown for MomentType (all 8), Tone (all 4), Language (EN/HR)
- A "Load sample facts" button that fills a realistic MomentFacts for the chosen type,
  using teams from the existing mock data (Liverpool, Sparta, Betis, Real Madrid, Varaždin)
- A "Generate" button
- Two result cards side by side:
    left  = TemplateNarrator output
    right = NanoNarrator output (or "Nano unavailable on this device")
  Each card shows headline, detail, engine badge, and latency in ms.
- A "Run all 64" button that generates every MomentType × Tone × Language through the
  current ladder and shows a pass/fail list against NarratorGuard, plus median latency.

Also add, at the bottom, a read-only panel titled "What the model was given" showing the
exact FACTS_JSON for the current selection. In the demo I will use this to prove no odds,
stake or identity ever reach the model.

Install and show me the median latency from "Run all 64".
```

---

## 12E — Optional: structured output instead of two-line parsing

Only do this if 12C is working, you have time, and the two-line parser is misbehaving.
It is an **alpha** API, Kotlin-only, and adds KSP — which is a classic version-mismatch
time sink. The guard already protects you, so this is a quality upgrade, not a necessity.

```
Optional upgrade: replace the two-line text parsing in NanoNarrator with ML Kit structured
output. Read https://developers.google.com/ml-kit/genai/prompt/android/structured-output
first and follow the versions on that page rather than any version I give you.

Roughly: add the KSP plugin, add ksp("com.google.mlkit:genai-schema-compiler:<version>"),
annotate a data class with @Generable and its fields with @Guide (description + maxItems),
then build the request with GenerateContentRequest.Builder(TextPart(prompt)) and
generateTypedContentRequest(baseRequest, OutputClass::class).

Keep the two-line path behind a boolean flag so we can switch back in seconds if the KSP
version fights the project's Kotlin version. If it does not compile within 20 minutes,
revert and stay on the text path — say so rather than fighting it.
```

---

## Troubleshooting

| Symptom | Cause | What to do |
|---|---|---|
| `checkStatus()` → `UNAVAILABLE`, message mentions `606-FEATURE_NOT_FOUND` or "Feature 636" | Device has no Nano v3 (needs Pixel 9/10 class hardware) | Not fixable. Go Path C (template) or Path B (LiteRT-LM + Gemma) |
| `DOWNLOADABLE` forever, download never completes | AICore or Play services out of date, or no Wi-Fi | Update Google Play services and the "Android System Intelligence" app, retry on Wi-Fi |
| First generation takes 5–10 s | Cold model load | That is expected — this is why `warmUp()` exists. Never remove it |
| Output has extra commentary around the two lines | Prompt drift | Tighten the prompt: repeat "EXACTLY two lines and nothing else" as the last line before FACTS |
| Croatian output is poor or drifts to English | Small model, weak on non-English | Acceptable — the guard catches malformed output. For the demo, prefer English for Nano and let templates carry Croatian |
| Guard rejects almost everything | Blocklist too broad | Check the two-decimal rule first; it is the usual culprit. Scores and minutes must pass |
| App ANRs at launch | `warmUp()` on the main thread | Move it to Dispatchers.IO inside a coroutine scope, never block onCreate |

---

## What "done" looks like for step 12

1. `Run all 64` in Narrator Lab is green — every combination passes the guard
2. Median latency under ~1.5 s on the Nano path, or instant on templates
3. The engine badge tells the truth about which one produced the text
4. Airplane mode on: everything still works, identically
5. `MomentFacts` still has no field for odds, stake, balance or identity

Then Step 13 is the Moment Engine (safety gate → scorer → attention budget → learning
router), which is the thing that decides *whether* to narrate at all.
