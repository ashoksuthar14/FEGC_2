# Claude Code build prompts — PSK native replica (Phase 1)

How to use this file: do §0 by hand (5 minutes), save §1 as `CLAUDE.md` in the project root, then paste the prompts in §3 one at a time, in order. Do not paste more than one at a time — each ends with something you can see on the phone, and that is what keeps a 24-hour build honest.

---

## 0. Before you start Claude Code

**0.1 Create the Android project in Android Studio** (Claude Code should not scaffold this; the IDE wizard gets the Gradle wiring right in 60 seconds).

- New Project → **Empty Activity** (Compose)
- Name: `Ambient` · Package: `eu.feg.ambient` · Language: Kotlin
- Minimum SDK: **API 26** · Build config: Kotlin DSL

Then open `app/build.gradle.kts` and set `compileSdk = 36` and `targetSdk = 36`.

**0.2 Put the reference material where Claude Code can see it.**

```
<project root>/
├─ app/
├─ docs/
│  ├─ PRD-01-Native-Shell.md          ← copy the PRD here
│  └─ screens/                        ← sliced screenshots (next step)
└─ CLAUDE.md                          ← §1 of this file
```

**0.3 Slice the screenshots.** The originals are 1920 × up-to-17800 px and 14 MB — too big for a model to read usefully. Run this once (Python + Pillow, `pip install pillow`), pointing `SRC` at your screenshots folder:

```python
from PIL import Image
import os, glob

SRC = r"C:\Users\ashok\OneDrive\Desktop\FEG HACKATHON\FEG Website screenshots"
OUT = r"docs\screens"          # relative to your project root
os.makedirs(OUT, exist_ok=True)

W = 1100                        # readable width for a model
SLICE = 1700                    # original px per slice

for path in glob.glob(os.path.join(SRC, "*.png")):
    name = os.path.splitext(os.path.basename(path))[0].replace(" ", "_")
    im = Image.open(path).convert("RGB")
    for i, top in enumerate(range(0, min(im.height, 8500), SLICE)):
        crop = im.crop((0, top, im.width, min(top + SLICE, im.height)))
        crop = crop.resize((W, int(crop.height * W / im.width)), Image.LANCZOS)
        crop.save(os.path.join(OUT, f"{name}_{i:02d}.jpg"), quality=80)
        print(name, i)
```

You will get files like `home_page_00.jpg`, `live_page_01.jpg`. Those filenames are what the prompts below refer to.

**0.4 Enable the phone.** USB debugging on, `adb devices` shows it, and Android Studio can install to it. Do not use the emulator — Phase 2 needs a real device.

---

## 1. Save this as `CLAUDE.md` in the project root

````markdown
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
````

---

## 2. How to work with Claude Code on this

- **One prompt at a time.** Paste, let it finish, install on the phone, look at it, then move on.
- **When a screen looks wrong**, do not describe it in words. Say: *"Compare your Home screen against `docs/screens/home_page_00.jpg` and list the differences, then fix the top three."* Comparing against the image beats any description you can write.
- **If it starts inventing features**, point at the PRD section number.
- **Use plan mode** (`Shift+Tab` twice) for steps 4, 5 and 7 — the big screens — so you see the approach before it writes 600 lines.
- **Budget**: steps 1–3 ≈ 60 min, steps 4–5 ≈ 90 min, steps 6–9 ≈ 60 min, steps 10–11 ≈ 60 min.

---

## 3. The prompts

### Step 1 — Theme and design tokens

```
Read docs/PRD-01-Native-Shell.md section 3, and look at docs/screens/home_page_00.jpg.

Create the theme layer in ui/theme/:

Color.kt — exactly these tokens, no others, no Material defaults:
  brandBlue        #1852BE   top app bar, primary buttons, active tab indicator
  brandBlueDark    #1647A6   secondary nav strip, pressed state
  brandBlueDeep    #011576   promo hero backgrounds
  background       #0E0E10   app background
  surface          #17171C   cards, league section bodies
  surfaceVariant   #22222A   section headers, chips
  surfaceRaised    #3B3B43   match rows, odds buttons
  oddsCell         #36363F   odds button fill
  betslipPanel     #2E2E38   bet slip container
  jackpotYellow    #F8C102   casino jackpot bars, TOP markers
  jackpotYellowDim #DDB905   jackpot bar gradient end
  textPrimary      #FFFFFF
  textSecondary    #A9A9B4   meta text, kickoff times, counts
  positive         #3BC66B
  negative         #E5484D

Type.kt — Inter if available in the project, else Roboto:
  titleLarge   20sp SemiBold
  titleMedium  16sp SemiBold
  bodyMedium   14sp Regular
  labelMedium  12sp Medium
  labelSmall   11sp Regular
  oddsValue    14sp Bold with FontFeatureSetting("tnum")
  oddsLabel    10sp Regular

Shape.kt — card 8dp, chip 16dp, odds button 6dp.

PskTheme.kt — dark only. Wire the tokens into a Material 3 ColorScheme AND expose them
directly via a CompositionLocal called LocalPskColors so components can use exact names.
Set the status bar to brandBlue.

Then update MainActivity to use PskTheme and render a placeholder Scaffold with a
brandBlue top app bar containing the text "PSK" in white bold, over the background colour.

Build with ./gradlew assembleDebug and confirm it compiles.
```

### Step 2 — The two components everything depends on

```
Look at docs/screens/home_page_00.jpg and docs/screens/live_page_00.jpg carefully.
Read PRD section 3.4.

Build these in ui/components/, with a @Preview for each on a #0E0E10 background:

OddsButton.kt
  - a small label on top (1 / X / 2 / "Yes" / "No"), value below in oddsValue style
  - fill oddsCell, corner 6dp, min height 44dp, fills available width
  - states: default, selected (brandBlue fill, white text), locked (40% alpha, no ripple)
  - optional small "TOP" badge in jackpotYellow at the top-right corner
  - a flashUp/flashDown parameter that briefly tints the border positive/negative

MatchRow.kt
  Layout left to right, matching the screenshot:
  - favourite star outline (24dp)
  - left column: kickoff chip ("tomorrow 00:30") or live chip ("1. poluvrijeme · 44m" with a
    red dot); below it, badge chips (BB, 90+, stream icon, stats icon) in textSecondary
  - team column: two rows, crest circle 20dp + team name in bodyMedium
  - score column (live only): two right-aligned numbers, one per team line
  - odds group: a "Basic offer" caption above three OddsButtons in a row
  - trailing chevron
  Row background surfaceRaised, corner 8dp, 1dp divider between rows.
  Height ~72dp for the two-line layout.

Previews: prematch row, live row with score, row with TOP badge, locked odds.

Do not build any screen yet. Show me the previews compile first.
```

### Step 3 — Data model, mock JSON, repositories

```
Read PRD sections 6 and 7.

1. Create data/model/ with exactly the data classes in PRD section 6. Use kotlinx.serialization
   @Serializable, and kotlinx-datetime Instant (add the dependency).
   Include UserState, RiskState, Limits, Consents exactly as specified — Phase 2 depends on them.

2. Create assets/mock/ with sports.json, leagues.json, matches.json.
   Rules from PRD section 7:
   - Use these real fixtures seen in the screenshots: Ipswich–Liverpool, Betis–Real Madrid,
     Paris SG–Monaco, Varaždin–Istria 1961, Stuttgart–1.FC Cologne, Bayer Lev.–Union Berlin,
     Hoffenheim–Bor.Dortmund, Genoa–Como, Port–Moreirense, Lommel SK–Cl. Bruges,
     Istanbul Basaksehir–Galatasaray, Viborg–Lyngby BK, Fredrikstad–Bodo/Glimt
   - Plus live fixtures: NK Lucko–Solin, Oil burner–Dugo Selo, Panthers–Rayon Sports,
     FC Gabala–Imisli FK, GrIFK–Espoo Bollklubb, Gas Alshimal–AL Julan, Karbala–AL Kharkh,
     Shabab Al Ordon–Al Ramtha, FC Prishtina–KF Vushtrri
   - 45+ matches total, 10 of them LIVE with plausible scores and minutes
   - Leagues grouped by country as in the screenshots: Croatia 1, England 1, France 1,
     Italy 1, Germany 1, Spain 1, Portugal 1, Belgium 1, Turkey 1, Denmark 1, Norway 1
   - Odds between 1.02 and 90.00, two decimals, realistic (favourites ~1.15–2.50)
   - Each match: a "Match" market (1/X/2) plus 2–4 more markets
   - Sports with counts: Football 314, Basketball 22, Tennis, Handball, Hockey, eFootball,
     eBasketball, Ice Hockey, Esports Counter Strike, Volleyball

3. Create data/mock/MockDataSource.kt that loads and parses those files from assets.
4. Create data/clock/MatchClock.kt — an interface with `val ticks: Flow<Unit>` and
   `fun now(): Instant` — plus a SystemMatchClock implementation ticking every second.
   Phase 2 replaces the implementation; nothing else may depend on the concrete class.
5. Create data/repo/MatchRepository, BetRepository, UserStateRepository exposing StateFlows.
   BetRepository must expose `val betPlaced: SharedFlow<String>` emitting the slip id.
6. Create core/AppContainer.kt wiring them, held by the Application class.

Write one unit test that loads matches.json and asserts 45+ matches parse and at least
10 are LIVE. Run it.
```

### Step 4 — Home screen

```
Study docs/screens/home_page_00.jpg, home_page_01.jpg and home_page_02.jpg.
Read PRD section 5.1. Use plan mode first — show me the composable breakdown before writing.

Build ui/home/HomeScreen.kt + HomeViewModel.kt with all 12 blocks in PRD 5.1, in order:
promo carousel, quick-link icon row, time tabs, "starts in 3 hours" cards, country chips,
player-props carousel, sport filter chips, league sections with MatchRows, "all events"
link, special bets card, arena preview, footer block.

Notes:
- LazyColumn with the horizontal carousels as LazyRow items
- League sections collapsible, expanded by default, header shows flag + name + count
- Skeleton shimmer for 400ms on first load
- Reuse MatchRow and OddsButton — do not write new odds UI
- Promo carousel auto-advances every 5s with page dots
- The footer needs About us / Responsible gaming / Self-exclusion form / licence text

Then install on the connected device and tell me what to look at.
```

### Step 5 — Live screen and the ticking clock

```
Study docs/screens/live_page_00.jpg and live_page_01.jpg. Read PRD section 5.2.

Build ui/live/LiveScreen.kt + LiveViewModel.kt:
- Title "Live betting", time tabs with LIVE active
- Sport accordions with live counts (Football 50, eFootball 5, Tennis 31, Basketball 13,
  eBasketball 5, Ice Hockey 12, Handball 2, Esports Counter Strike 8, Volleyball 4)
- Live match rows: red pulsing dot, minute chip "1. poluvrijeme · 44m", per-team score
  column, market label above the odds group ("Match", "Match · double chance",
  "Both teams to score")
- Leagues further down collapsed to a single header row with a chevron

Drive minutes and scores from MatchClock, not static text:
- every tick advances the minute of LIVE matches
- roughly every 45 seconds one random live match scores; its odds shift and the affected
  OddsButtons flash (flashUp/flashDown)

Install and confirm minutes visibly tick and at least one score changes while watching.
```

### Step 6 — Match detail

```
Read PRD section 5.3. Build ui/match/MatchDetailScreen.kt reachable by tapping any MatchRow
(Navigation Compose, route "match/{matchId}").

Header with competition, teams, score, minute. Market group tabs (Match, Goals, Handicaps,
Player props, Statistics — Statistics can be a placeholder). Market accordions of OddsButton
rows. Sticky bottom bar showing the bet slip count when it is non-empty.
```

### Step 7 — Bet slip

```
Study docs/screens/home_page_00.jpg (right-hand panel). Read PRD section 5.4. Plan first.

Build ui/betslip/ as a bottom sheet plus a full screen:
- Slip tabs 1 2 3 4, type dropdown ("Plain"), clear-all icon
- Empty state, this exact copy: "The ticket is empty." / "If you want to add a bet to your
  bet slip, review our odds offer and select the bet of your choice." with the crossed-ticket icon
- Populated: selection rows (match, market, pick, odd, remove X), stake field with quick
  chips €5 €10 €20 €50, total odds, possible payout, PLACE BET button
- Tapping any OddsButton anywhere in the app adds/removes that selection and updates the
  bet-slip badge in the top bar

On place: create a PlacedBet with status OPEN, clear the slip, show a success sheet, and
emit the slip id on BetRepository.betPlaced. Nothing listens to that flow yet — that is
correct, Phase 2 attaches there.
```

### Step 8 — My bets

```
Read PRD section 5.5. Build ui/mybets/ with Open / Settled tabs.

Rows: leg list with per-leg status icons (pending dot, won ✓ in positive, lost ✗ in negative),
stake, total odds, possible payout. For slips with live legs show a progress line "2/3 · 61'".
Add a "Scan a branch ticket" button top-right that opens a stub screen saying
"Ticket scanning — Phase 2".

Legs of live matches must update as the Live screen's clock advances.
```

### Step 9 — Responsible gaming

```
Read PRD section 5.6. This is small but it carries the whole compliance story — build it properly.

ui/rg/ResponsibleGamingScreen.kt:
- Deposit / loss / time limits, each with a used-amount progress bar
- Panic button: prominent, red-bordered, "Pause my account for 48 hours", with a confirm
  dialog; on confirm sets UserState.panicUntil to now + 48h
- Self-exclusion entry point (a confirm screen, no real action)
- Reality check interval picker
- Quiet hours time-range picker
- Two separate consent switches: "Match updates" and "Offers" — never one combined switch
- A developer section at the bottom: "Simulate state" with three radio options
  Normal / At-risk / Self-excluded, writing to UserState.riskState

All of it persisted through UserStateRepository. Phase 2's safety gate reads this and
nothing else.
```

### Step 10 — Navigation, then the P1 screens

```
Read PRD section 4.

1. Build ui/nav/AppNavHost.kt and BottomBar.kt with the five tabs: Sport, Live, Casino,
   Arena, My bets. Top app bar: PSK wordmark left; search, bet-slip badge with count, and
   profile icon right. Profile opens a More sheet with Lotto, Promo, Forum, Results,
   Statistics, News, Champions Club, Branches, Help, Responsible gaming, Settings.
   Responsible gaming and My bets must both be reachable in two taps.

2. Then build these three, each against its screenshot:
   - ui/casino/CasinoScreen.kt      → docs/screens/casino_page_00.jpg, PRD 5.7
   - ui/arena/ArenaScreen.kt        → docs/screens/prk_arena_page_00.jpg, PRD 5.8
   - ui/promo/PromoScreen.kt        → docs/screens/promo_page_00.jpg, PRD 5.9
   Casino tiles: placeholder gradient + game name + badge (NEW/JACKPOT/EXCLUSIVE) + yellow
   jackpot value bar. Arena "copy slip" loads that tipster's picks into the bet slip.
```

### Step 11 — Verification pass

```
Do a review pass, no new features.

1. For each of home, live, casino, arena, promo: open your screen next to the matching
   docs/screens/*.jpg and list every visual difference. Fix the top three per screen.
2. Grep the whole codebase for hardcoded Color( values outside ui/theme/ and for any
   Material default colour. Fix them.
3. Confirm every component in ui/components/ has a @Preview.
4. Scroll the Home screen on the device and report any dropped frames; fix the worst offender
   (usually a missing key in a LazyColumn or a non-remembered lambda).
5. Verify the four Phase 2 seams exist and are wired: BetPlaced emits, MatchClock is only
   used through the interface, UserState.riskState/consents persist, PlacedBet.legs populated.
6. Run ./gradlew assembleDebug and the unit tests.

Report as a numbered list: what you fixed, what you left, and why.
```

---

## 4. If you fall behind

Cut in this order and say so on the pitch slide rather than shipping something broken:

1. Step 10's Casino / Arena / Promo → keep navigation, stub the three screens
2. Home blocks 6, 10, 11 (player props, special bets, arena preview)
3. Match detail → let rows open the bet slip directly

Never cut: theme, MatchRow/OddsButton, Live screen, bet slip, My bets, Responsible gaming.
Those five are what Phase 2 stands on.
