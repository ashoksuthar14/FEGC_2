# What competitors do, what we are missing, and what is legal to copy

Competitors in FEG's markets: SuperSport and Germania (HR), Sazka, Tipsport and Betano (CZ),
Superbet and Betano (RO), STS and Betclic (PL), Niké (SK). Sky Bet is the reference case for
the free-to-play mechanic.

---

## 1. The line that decides everything

Every mechanic below is the same psychology. What makes it legal or illegal is one thing only:

> **What is the reward, and does earning it require wagering?**

| Reward | Status |
|---|---|
| Bonus funds, free bets, free spins, cashback, odds boosts | **Inducement.** Restricted in RO (email to active opted-in players only), PL (no casino at all), banned outright in NL and BE, never to at-risk or self-excluded players anywhere |
| Status, progress, identity, access, information, non-gambling prizes | **Not an inducement.** Legal in every FEG market, and reachable by every customer |

Most operators reward with money because it is easy. That is also the part of their playbook
that is being legislated away. Everything we take from them, we take from the second row.

**Second rule, equally important:** a mechanic must never require a bet to progress. "Wager €50
to complete this mission" turns a harmless progress bar into an inducement to spend. "Follow
three teams" does not.

---

## 2. Techniques worth taking — the three we are missing

### M1 · Season Recap — the "Wrapped" moment ★ (recommended, ~40 min)

**What competitors do:** almost nothing. Spotify Wrapped is the template and clubs have copied
it (Southampton FC ran a personalised decade recap), but sportsbooks have not — they run
bonus-led reactivation campaigns instead.

**The psychology:** identity and self-narrative. People share things that say something about
who they are, and a recap is reactivation that arrives as a gift instead of a nag.

**Our version:** the narrator already writes summaries — this is the digest's big brother.
At the end of a season or a month: *"38 matches followed. 21 predictions right. Sparta, every
single week."* Rendered as a widget state and a shareable card.

**The hard rule:** it counts matches, follows, predictions and streaks. **Never** stake,
winnings, losses or balance. That keeps it out of inducement territory, keeps it safe for a
customer who is cutting back, and it is what makes it shareable — nobody shares their P&L.

**Why it fits our problem statement:** it is a surface, not a campaign. It gives the widget and
the digest something to say on days when there is no live match, which is otherwise our weakest
moment.

### M2 · Club identity — "your club" on the glass (recommended, ~30 min)

**What competitors do:** SuperSport in Croatia leans heavily on club sponsorship and local
affinity; every operator sells team-following. But it stops at a filter in a list.

**The psychology:** identity beats incentive. A customer who has themed their phone around their
club has made the app part of who they are, which no bonus can buy.

**Our version:** following a club themes the widget and the Live Update — the crest, the club's
colour as the accent, the club's fixtures in the idle state. Purely cosmetic, zero compliance
surface, and it makes the widget something a fan keeps on the home screen. Widget adoption at
90 days is one of the brief's metrics, and this is the cheapest lever on it.

### M3 · Non-monetary missions and status (recommended, ~45 min)

**What competitors do:** Betano and Superbet run missions, XP and tiers. Industry write-ups are
explicit that the rewards are "bonus funds, free spins, mystery boxes" and that players
"continue wagering just to earn loyalty points, even when losing money". That is precisely the
harm pattern regulators are looking for.

**Our version:** keep the mechanic, change the currency.

| Competitor mission | Ours |
|---|---|
| "Wager €50 on football this week" | "Follow three teams" |
| "Place 10 bets to reach Gold" | "Check in on five live matches" |
| Reward: €10 free bet | Reward: a badge, a streak, a tier name, early access to a feature |

Progress renders on the widget as a status line. **No mission may require a bet to progress**,
and no reward may be money or a free bet. PSK already runs a Loyalty Club, so the tier language
exists — the change is decoupling points from spend.

---

## 3. Worth naming on a roadmap slide, not building in 24 hours

### Free-to-play prediction game (the Super 6 model)

Sky Bet's Super 6 is the strongest example in the industry: predict six scorelines, **completely
free**, jackpots up to £1m, over £12m paid out historically. It is a separate app that feeds the
sportsbook, and it reaches people who will never open a betting slip.

Why it is powerful: no stake means it is not a wagering product, so it can be marketed far more
freely than a bonus. Why it is not a 24-hour build: in Croatia a prize competition of this kind
runs through the promotional-prize-game approval route rather than the gambling licence, needs
prize funding, rules and a regulator filing — a product decision for FEG, not a hackathon
feature. Note that Sky still rates Super 6 18+ and carries responsible-gambling messaging on it.

**What we can do cheaply instead:** a free prediction *streak* — one call per match day, no
prize, points and a friends leaderboard only. Same variable-reward loop, no prize-competition
paperwork. Fold it into M3 if there is time.

### Cash Out reframed as control

Competitors sell cash out as adrenaline. Nobody sells it as **control** — "take what you have
and stop". That framing is genuinely differentiated and genuinely responsible.

**The catch for us:** a cash-out value is money, and money never goes on an OS surface. The most
we can do is a neutral "cash out available" status that deep-links in-app, with the number shown
only inside Zone B. Worth one line in the deck; do not put a figure on the lock screen.

### Community and social proof

PSK Arena already exists — tipsters, copyable slips, follower counts. The surface-level version
is aggregate only: *"12,400 Fortuna fans are following this match"*. Never a named individual,
never "people like you are betting on…", which crosses into pressure.

---

## 4. What we will not copy, and why — the compliance slide

| Competitor mechanic | Why not |
|---|---|
| Free bet / bonus / free spins as a mission reward | Inducement. Restricted per market, banned in NL and BE, never to at-risk users. It is the part of the playbook with the shortest remaining life |
| Missions that require wagering to progress | Turns a progress bar into pressure to spend — the exact harm pattern in the RG literature |
| Cashback and acca insurance | Loss-chasing by design: it rewards you for having lost |
| Odds boosts pushed to a phone | An inducement on an OS surface. Banned in that renderer by Android policy as well as by us |
| "We miss you" + bonus reactivation | Targets the people most likely to have stopped for a reason |
| Countdown timers on offers | Manufactured urgency — an unfair commercial practice under the UCPD and a named target of the Digital Fairness Act |
| VIP account managers | The single most criticised practice in the industry; incompatible with the brief's guardrail |

One line for the slide: *"We took the mechanics and left the money. Everything a competitor
rewards with a free bet, we reward with status, identity or information — which is legal in
every market we operate in, works for customers who are cutting back, and does not need
rebuilding when the next bonus ban lands."*

---

## 5. If you build the three, this is what changes in the demo

- **Idle widget stops being dead.** Today the idle state shows the next fixture. With M1 and M2
  it shows your club's colours, your streak, and your recap when a month closes.
- **A new demo beat:** flip to a followed club and the whole widget re-themes. It takes three
  seconds and it lands, because it is visibly *theirs*, not ours.
- **A stronger answer to "how do you engage people between matches"** — which is the weakest
  point in the current demo, because everything we show is live-match-driven.

Total ~2 hours for all three. If only one, build **M2 club identity** — it is the cheapest and
it moves widget adoption, which is a metric the brief names.
