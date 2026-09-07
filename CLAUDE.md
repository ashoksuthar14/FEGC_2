# Ambient — PSK native replica

## What this is
A native Android replica of PSK (psk.hr), FEG's Croatian betting brand, built for a 24-hour
hackathon. Phase 1 is the app shell with mock data. Phase 2 adds the "Ambient" moment engine
(Android 16 Live Updates, Glance widget, on-device LLM narration). Build Phase 1 so Phase 2
is purely additive.

## Read first
- `docs/PRD-01-Native-Shell.md` — the specification. It wins any disagreement.
- `docs/screens/*.jpg` — sliced screenshots of the real product. These are the visual truth.
  Look at the relevant slice before building a screen. Do not invent layout.

## Stack (do not substitute)
Kotlin · Jetpack Compose · Material 3 · Navigation Compose · ViewModel + StateFlow ·
kotlinx.serialization · Coil 3 · Coroutines/Flow.
min SDK 26, compile/target SDK 36. Single Activity. No DI framework — one `AppContainer`.
No backend, no network calls, no API keys. All data from `assets/mock/*.json`.

## Hard rules
1. **No stock Material colours anywhere.** Every colour comes from `PskTheme`. If you see
   purple, it is a bug.
2. **Odds use tabular figures** (`FontFeatureSetting("tnum")`) so numbers do not reflow when
   they change.
3. **Compose previews are mandatory** for every component in `ui/components/`. A component
   without a preview is not done.
4. **No new dependencies** without saying why first.
5. Strings stay in Croatian where the screenshots show Croatian (`1. poluvrijeme`,
   `Prijavi se da bi mogao/la komentirati`). Everything else in English.
6. Keep files under ~300 lines. Split by component, not by "utils".
7. `ui/` never imports from a future `ambient/` package. `data/` never imports `ui/`.

## The four Phase 2 seams — build these exactly as specified
- `BetPlaced` event on a shared flow when a slip is placed
- `MatchClock` interface (Phase 2 swaps in a scripted simulator)
- `UserState.riskState` + `UserState.consents` (Phase 2's safety gate reads these first)
- `PlacedBet.legs` (become progress segments on the lock screen)

## Definition of done for a step
It compiles, it installs on the connected Pixel, and the thing described actually appears.
Run `./gradlew assembleDebug` before claiming a step is finished.

## Commit style
One commit per numbered step, message `step N: <what>`.
