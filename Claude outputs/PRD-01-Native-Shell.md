# PRD-01 — PSK Native App Replica (Phase 1 shell)

FEG Hackathon · Challenge 2 · Android · solo build
Source of truth: 9 full-page screenshots of psk.hr in `FEG HACKATHON/FEG Website screenshots/`
Status: ready to build · Phase 2 (Ambient engine) builds on top of this

---

## 1. Why this document

The hackathon gives VPN access to a live European web product. We cannot use their SDK, their API or their code. What we can do is rebuild the *look and the flows* as a real Android app with sample data, so that Phase 2 — the Ambient moment engine, Live Updates, widget, on-device narrator — has a believable host application to live inside.

This PRD covers **only the shell**: screens, navigation, design system, mock data. No engine, no notifications, no AI. Those are Phase 2 and have their own document.

**One-line goal:** a judge picks up the phone, and for the first thirty seconds believes they are holding Fortuna/PSK's real app.

---

## 2. Research findings

### 2.1 What the product actually is

The screenshots are **PSK** (psk.hr / pskmobile.hr) — FEG's Croatian brand, operated by Hattrick-PSK d.o.o. FEG runs the same platform pattern across Fortuna (CZ/SK/PL/RO) and PSK (HR). The web front end is a Vue/Nuxt single-page app, dark-themed, with a persistent right-hand bet slip.

FEG already ships **separate native apps per brand**, not a wrapped website: *PSK Sport* (`hr.psk.sport.betting`, publisher "Fortuna Game a.s.", 50k+ installs, last updated August 2026) and *Fortuna Sport* (`cz.fortuna.sport.sazky.bet`). FEG's own corporate site describes recent work as "redesigned native apps for sportsbook and casino". The store listing advertises live streaming, live match tracking, real-time notifications and **QR/barcode scanning of a betting slip from a branch** — the last one is worth noting, because our Phase 2 "paper ticket comes alive" feature extends a capability their users already know.

### 2.2 Is there a "Vue native" path?

Yes, three of them, and all three are wrong for this project.

| Option | State in 2026 | Why not here |
|---|---|---|
| **NativeScript-Vue** | Alive, Vue 3 GA, but a small ecosystem (~6.5k stars) | Renders real native views, but there is no binding for Android 16 Live Updates, Glance, ML Kit GenAI or LiteRT-LM. We would write all of it as custom native modules anyway |
| **Ionic Vue + Capacitor** (also Quasar) | Mature and popular | It is a WebView. Widgets and Live Activities need plugins that only *pass data* — the widget UI itself must still be written in Kotlin with Glance/RemoteViews. Community confirms: "these plugins don't create the widget UI for you" |
| **Vue Native** | Deprecated | Dead end |

The decisive point is that **every differentiator in our solution is a native-only API**:

- Android 16 Live Updates (`ProgressStyle` + `setRequestPromotedOngoing`) — no cross-platform binding exists
- Glance app widgets — Kotlin only
- ML Kit GenAI Prompt API / LiteRT-LM on-device model — Kotlin/JNI
- Dynamic launcher shortcuts, `ACTION_USER_PRESENT` unlock receiver, ML Kit barcode scanning

A hybrid stack would mean writing 100% of the interesting code in Kotlin *and* carrying a WebView we do not need. It also costs us on the "rich, modern UI/UX" criterion (20% of the score): a WebView shell looks like a website on a phone, which is precisely the thing the brief is complaining about.

### 2.3 Stack decision

**Native Android — Kotlin + Jetpack Compose.** Single module app, no backend, all data from local JSON.

| Layer | Choice | Note |
|---|---|---|
| Language | Kotlin 2.x | |
| UI | Jetpack Compose + Material 3 | Custom `PskTheme`, not stock Material colours |
| Min / target SDK | min 26 · target 36 (Android 16) | 36 is required for Live Updates in Phase 2 |
| Navigation | Navigation Compose, single Activity | |
| State | `ViewModel` + `StateFlow`, `collectAsStateWithLifecycle` | |
| Data | `kotlinx.serialization` reading JSON from `assets/` into an in-memory repository | No Room in Phase 1; Phase 2 adds it for the ledger |
| Images | Coil 3 | Placeholder gradients where we have no art |
| Async | Coroutines + Flow | The simulator in Phase 2 emits on a Flow |
| DI | None — plain constructors, one `AppContainer` | Hilt is not worth the 24-hour budget |
| Build | Android Studio on Windows, physical Pixel over USB | Emulator cannot show Live Updates or run Gemini Nano |

**Explicitly not used:** React Native, Flutter, Capacitor, NativeScript, WebView, any network call, any API key.

---

## 3. Design system (sampled from the screenshots)

### 3.1 Colour

| Token | Hex | Use |
|---|---|---|
| `brandBlue` | `#1852BE` | Top app bar, primary buttons, active tab indicator |
| `brandBlueDark` | `#1647A6` | Secondary nav strip, pressed state |
| `brandBlueDeep` | `#011576` | Promo hero backgrounds |
| `background` | `#0E0E10` | App background (the dominant colour of the whole product) |
| `surface` | `#17171C` | Cards, league section bodies |
| `surfaceVariant` | `#22222A` | Section headers, chips |
| `surfaceRaised` | `#3B3B43` | Match rows, odds buttons, bet slip rows |
| `oddsCell` | `#36363F` | Odds button fill |
| `betslipPanel` | `#2E2E38` | Bet slip container |
| `jackpotYellow` | `#F8C102` | Casino jackpot bars, "TOP" markers |
| `jackpotYellowDim` | `#DDB905` | Jackpot bar gradient end |
| `textPrimary` | `#FFFFFF` | |
| `textSecondary` | `#A9A9B4` | Meta text, kickoff times, counts |
| `positive` | `#3BC66B` | Odds up, won |
| `negative` | `#E5484D` | Odds down, lost |

Light theme is out of scope — the product ships dark by default and the screenshots are dark. The header carries a "DARK" toggle; render it as a disabled control.

### 3.2 Type

Inter (or Roboto as substitute) throughout.

| Style | Size / weight | Use |
|---|---|---|
| `titleLarge` | 20sp SemiBold | Screen titles ("Live betting") |
| `titleMedium` | 16sp SemiBold | Section headers ("PSK Favorites") |
| `bodyMedium` | 14sp Regular | Team names, list content |
| `labelMedium` | 12sp Medium | League names, chips, tabs |
| `labelSmall` | 11sp Regular | Kickoff time, counts, badges |
| `oddsValue` | 14sp Bold, tabular figures | Odds numbers — must not reflow when they change |
| `oddsLabel` | 10sp Regular | The `1` / `X` / `2` above each odd |

### 3.3 Spacing and shape

4dp grid. Screen padding 12dp. Card corner 8dp, chip corner 16dp, odds button corner 6dp. Match row height 72dp (two team lines) or 56dp (single line). Dividers `#22222A` at 1dp.

### 3.4 Core components to build once

1. **`OddsButton`** — label above value, selected state (blue fill), locked state, up/down flash. The single most reused component.
2. **`MatchRow`** — favourite star, kickoff or live minute, two team lines with crest, score column when live, 3 odds buttons, badges (`TOP`, `BB`, `90+`, stream icon, stats icon), chevron to detail.
3. **`LeagueSection`** — collapsible header with country flag, league name, match count, chevron.
4. **`SportChip` / `FilterChip`** — the horizontal scrollers (Football, Tennis, Basketball…).
5. **`TimeTabs`** — LIVE / TODAY / 1H / 3H / TOMORROW / ALL.
6. **`PromoCard`** — 16:9 gradient card with title, subtitle, CTA.
7. **`GameTile`** — casino tile with badge (`NEW`, `JACKPOT`, `EXCLUSIVE`) and a yellow jackpot value bar.
8. **`SectionHeader`** — icon + title + "SEE ALL n" + carousel arrows.
9. **`EmptyState`** — icon, headline, body. Used by the bet slip.

---

## 4. Information architecture

The web has ten primary nav entries plus a secondary strip. A phone cannot carry that, and FEG's real app does not try to. Collapse to a five-item bottom bar plus a "More" sheet.

**Bottom navigation**

| Tab | Icon | Screen |
|---|---|---|
| Sport | football | Home / prematch |
| Live | broadcast dot | Live betting |
| Casino | chips | Casino lobby (tabs: Casino / Live casino / Virtual) |
| Arena | trophy | PSK Arena (tipster feed) |
| My bets | ticket | Bet slip + open/settled bets |

**Top app bar:** PSK wordmark left; search, bet-slip badge (count), profile icon right. On Casino, a balance pill.

**More sheet** (from profile icon): Lotto, Promo, Forum, Results, Statistics, News, Champions Club, Branches, Help, **Responsible gaming**, Settings.

Two things must be reachable in two taps or fewer, because Phase 2 depends on them: **My bets** and **Responsible gaming**.

---

## 5. Screen specifications

Priorities: **P0** is required for the Phase 2 demo. **P1** makes the app feel real to a judge who swipes around. **P2** only if time remains.

### 5.1 Home / Sport — P0

Adapted from the web home page, top to bottom:

1. Promo carousel — 4 cards, auto-advance 5s, page dots (`LOTTO FREE BET`, `WIN THE NEW 4Q PLUS HYBRID`, `TOP OFFER 5+1`, `MATCH MASTER`)
2. Quick-link icon row — horizontally scrollable: Promo, Aviator, MM, Missions, Casino, eFootball, eBasketball, Lotto, PSK Champions, Live Casino, PSK Arena, Forum
3. Time tabs — LIVE / TODAY / 1H / 3H / TOMORROW / ALL (TODAY default)
4. "Starts in 3 hours" highlight cards — horizontal carousel, each with match, a single named outcome and one big odd
5. Country / league chips — Croatia 1, England 1, France 1, Italy 1, Germany 1, Spain 1, US Open (men)…
6. Player-props carousel — cards titled by player (`KYLIAN MBAPPE`), rows of prop + odd, `BOOSTER` badges
7. Sport filter chips — Football, Tennis, Basketball, Handball, Hockey
8. League sections with match rows — the main body
9. "ALL EVENTS IN THE CATEGORY FOOTBALL ›" link
10. Special bets card — a novelty market with two outcomes and a share-of-stake split
11. PSK Arena preview — first 5 tipster rows
12. Footer block — About us, Responsible gaming, Self-exclusion form, licence text, payment logos

Empty/loading: skeleton shimmer on rows for 400ms so the app feels like it is fetching.

### 5.2 Live betting — P0

Screen title "Live betting". Time tabs with LIVE active. Sport accordions with counts (Football 50, eFootball 5, Tennis 31, Basketball 13, eBasketball 5, Ice Hockey 12, Handball 2, Esports Counter Strike 8, Volleyball 4). Live match rows differ from prematch:

- live minute chip — `1. poluvrijeme · 44m` (localised string, keep the Croatian for authenticity)
- score column, per team, right-aligned before the odds
- market label above the odds group (`Match`, `Match · double chance`, `Both teams to score`)
- red pulsing dot on the minute chip
- some leagues collapsed to a single header row with a chevron

Scores and minutes must be driven by a ticking clock in the ViewModel, not static text — Phase 2 replaces that clock with the match simulator, so build it behind a `MatchClock` interface now.

### 5.3 Match detail — P0

Header: competition, teams, score, minute. Market group tabs (Match, Goals, Handicaps, Player props, Statistics). Market accordions, each a list of `OddsButton` rows. Sticky bottom bar showing the bet slip count when non-empty.

### 5.4 Bet slip — P0

Right-hand panel on web; a bottom sheet plus a full screen on mobile.

- Slip tabs `1 2 3 4`, type dropdown (`Plain`), clear-all
- Empty state, reproduced verbatim: *"The ticket is empty. If you want to add a bet to your bet slip, review our odds offer and select the bet of your choice."*
- Populated: selection rows (match, market, pick, odd, remove), stake input with quick-stake chips (€5 / €10 / €20 / €50), total odds, possible payout, PLACE BET button
- On place: success sheet → the bet appears in My bets as **open**. **This is the Phase 2 trigger point** — the moment a live slip is created is where the Ambient engine attaches. Emit `BetPlaced(slipId)` on a shared flow even though nothing listens to it yet.

### 5.5 My bets — P0

Tabs: Open / Settled. Rows show legs with per-leg status icons (pending, won ✓, lost ✗), stake, total odds, possible payout, and for live slips a progress line `2/3 · 61'`. A "Scan a branch ticket" button in the top right — a stub screen in Phase 1, wired to the barcode scanner in Phase 2.

### 5.6 Responsible gaming — P0 (small but non-negotiable)

Not glamorous, but Phase 2's entire compliance story reads from it:

- Deposit / loss / time limits with a used-amount progress bar
- **Panic button** — prominent, red-bordered, "Pause my account for 48 hours"
- Self-exclusion entry point
- Reality check interval
- Quiet hours picker
- Consent toggles: *Match updates* and *Offers* as two separate switches
- A developer-only "Simulate state" row: Normal / At-risk / Self-excluded

That last row is what drives the Calm Mode demo moment.

### 5.7 Casino lobby — P1

Hero banner carousel, category chips (Lobby, Providers, Jackpots, Topics, PSK Favorites, New Games, Popularly, Game Show, Spin Gifts), search field and FILTERS button, then horizontal carousels per section with `GameTile`s and yellow jackpot bars. "Recent wins" list. Tapping a tile opens a stub "Game loading" screen — no game.

### 5.8 PSK Arena — P1

Sort control ("According to attractiveness") and Filters. Rows: avatar, username, "inspiration 2.2 thousand", inspiration badge, event count, Plain type, date/time chips, Stake, Course, Possible payment, and two action icons (info, copy-slip). Copy-slip loads the picks into the bet slip — a nice second path into the Phase 2 trigger.

### 5.9 Promo — P1

Hero with a live countdown chip (`ENDS IN: 4 HOURS`) and a grid of promo cards. **Design note for the pitch:** this screen is exactly where offers legally belong. Keep it, and keep it inside the app — Phase 2's §10 argument is that OS surfaces stay offer-free while this screen exists.

### 5.10 Lotto — P2

Country rail with flag + draw counts, three draw banners, All / Favorites / Popularly tabs, and an "Upcoming draws" table: name, time, columns 1–8 of payouts. A horizontally scrollable table is the only tricky part.

### 5.11 Live casino / Virtual games — P2

Same layout as the casino lobby with different section names (Live Lobby, Only in PSK, Roulette, Blackjack, Baccarat, Maps, All games). One shared screen, different data file.

### 5.12 Forum — P2

Message list with avatar, username, relative time, text, occasional system chip (`System €0.25 | €18.46`), reaction count, and a disabled composer. Static data — no real chat.

---

## 6. Data model

```kotlin
data class Sport(val id: String, val name: String, val icon: String, val liveCount: Int, val totalCount: Int)
data class League(val id: String, val sportId: String, val country: String, val name: String, val flag: String)
data class Team(val id: String, val name: String, val crest: String?)

data class Match(
  val id: String, val leagueId: String,
  val home: Team, val away: Team,
  val kickoff: Instant,
  val state: MatchState,               // PREMATCH, LIVE, FINISHED
  val homeScore: Int?, val awayScore: Int?,
  val minute: Int?, val period: String?,   // "1. poluvrijeme"
  val badges: List<String>,            // TOP, BB, 90+, STREAM, STATS
  val markets: List<Market>
)
data class Market(val id: String, val name: String, val outcomes: List<Outcome>)
data class Outcome(val id: String, val label: String, val odds: Double, val isTop: Boolean, val locked: Boolean)

data class Selection(val matchId: String, val marketId: String, val outcomeId: String, val oddsAtPick: Double)
data class BetSlip(val id: String, val selections: List<Selection>, val stake: Double, val type: SlipType)
data class PlacedBet(
  val id: String, val legs: List<Leg>, val stake: Double,
  val totalOdds: Double, val placedAt: Instant, val status: BetStatus  // OPEN, WON, LOST, VOID
)
data class Leg(val matchId: String, val description: String, val odds: Double, val status: LegStatus)

data class UserState(                  // read by Phase 2's safety gate
  val riskState: RiskState,            // NORMAL, AT_RISK, SELF_EXCLUDED
  val panicUntil: Instant?,
  val limits: Limits,
  val consents: Consents,              // matchUpdates, offers
  val quietHours: ClosedRange<LocalTime>?,
  val market: Market                   // CZ, SK, PL, RO, HR
)
```

`UserState` matters more than it looks. Build it in Phase 1 exactly as written, because Phase 2's safety gate is a pure function of it.

---

## 7. Mock data

`assets/mock/` — `sports.json`, `leagues.json`, `matches.json`, `casino_games.json`, `promos.json`, `arena_tips.json`, `forum.json`, `lotto_draws.json`.

Rules that keep it convincing: real club names from the screenshots (Ipswich–Liverpool, Betis–Real Madrid, Paris SG–Monaco, Varaždin–Istria 1961, Stuttgart–1.FC Cologne, Hoffenheim–Bor.Dortmund, Genoa–Como, Port–Moreirense, Lommel SK–Cl. Bruges); odds in the observed range 1.02–90.00 with two decimals; kickoff times spread across today and tomorrow; 8–12 live matches with plausible scores and minutes; Croatian strings where the screenshots show Croatian. Crests as coloured circles with initials — no scraped logos.

---

## 8. Project structure

```
app/src/main/java/eu/feg/ambient/
├─ MainActivity.kt
├─ ui/
│  ├─ theme/            Color.kt  Type.kt  Shape.kt  PskTheme.kt
│  ├─ components/       OddsButton  MatchRow  LeagueSection  SportChip
│  │                    TimeTabs  PromoCard  GameTile  SectionHeader  EmptyState
│  ├─ home/  live/  match/  betslip/  mybets/  casino/  arena/  promo/  rg/
│  └─ nav/              AppNavHost.kt  BottomBar.kt
├─ data/
│  ├─ model/            the classes in §6
│  ├─ repo/             MatchRepository  BetRepository  UserStateRepository
│  ├─ mock/             MockDataSource.kt  (assets loader)
│  └─ clock/            MatchClock.kt      (interface — Phase 2 swaps the impl)
└─ core/                AppContainer.kt  Formatters.kt
```

`ambient/` — the Phase 2 engine — will be a sibling package that depends on `data/` and nothing in `ui/`.

---

## 9. Build order and acceptance

| # | Step | Done when |
|---|---|---|
| 1 | Project, theme, tokens | A blank screen renders `#0E0E10` with the blue app bar and PSK wordmark |
| 2 | `OddsButton` + `MatchRow` in a preview | Compose preview matches a cropped screenshot side by side |
| 3 | Mock data + repositories | `matches.json` loads and logs 40+ matches |
| 4 | Home screen | Scrolls end to end with all 12 blocks |
| 5 | Live screen + `MatchClock` | Minutes tick; at least one score changes while watching |
| 6 | Match detail | Reachable from any row; markets expand |
| 7 | Bet slip | Add 3 selections, stake, place → `BetPlaced` emitted |
| 8 | My bets | Open bet visible with `2/3 · 61'` progress line |
| 9 | Responsible gaming | Three-state simulator switch and the panic button work |
| 10 | Casino, Arena, Promo | Navigable, no crashes |
| 11 | Polish | No jank scrolling home on the Pixel; no stock-Material purple anywhere |

**Definition of done for Phase 1:** the five bottom tabs all open, a bet can be placed and appears in My bets, the live screen ticks, and `UserState` can be flipped to At-risk or Self-excluded from the RG screen.

**Time budget:** 4.5 hours in the 24-hour plan (hours 1–4.5 build the shell; the Match Simulator at 4.5–6 replaces `MatchClock`). If it runs long, drop Lotto, Live casino, Virtual games and Forum first — they are P2 for exactly this reason.

---

## 10. Out of scope for Phase 1

No login or registration (show the screens, do not implement auth). No real odds, no network, no payments, no game play, no streaming, no localisation framework (hardcode the strings we show), no tablet layout, no light theme, no accessibility audit beyond sensible content descriptions, and none of the Ambient engine.

---

## 11. What Phase 2 will attach to

Four seams, all defined in Phase 1 so nothing needs refactoring later:

1. `BetPlaced` on a shared flow → creates the Live Update and asks for notification permission
2. `MatchClock` interface → replaced by the scripted match simulator
3. `UserState.riskState` / `consents` → read first by the safety gate, before any surface renders
4. `PlacedBet.legs` → become the progress segments of the lock-screen card

Build those four honestly and Phase 2 is additive.
