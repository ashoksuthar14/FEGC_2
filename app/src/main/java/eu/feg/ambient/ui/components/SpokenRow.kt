package eu.feg.ambient.ui.components

/**
 * Screen-reader sentences for the offer rows.
 *
 * TalkBack reading a match row as laid out gives "1. poluvrijeme - 44m star NK Lucko Solin
 * 0 1 NK Lucko 6.00 Draw 3.40" — a soup. These helpers turn the same facts into one sentence,
 * with the numbers a person would say rather than the glyphs the row draws.
 *
 * The number words live here rather than borrowing `ambient/narrator`'s EnglishWords because
 * CLAUDE.md rule 7 keeps `ui/` from importing `ambient/`; twenty lines of duplication is the
 * cheaper side of that trade.
 */
internal object SpokenRow {

    private val ones = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen",
    )
    private val tens = listOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
    )

    /** Trailing live minute in the chip text, e.g. "1. poluvrijeme - 44m" → 44. */
    private val minuteSuffix = Regex("""(\d+)m$""")

    fun number(n: Int): String = when {
        n < 0 || n > 99 -> n.toString()
        n < 20 -> ones[n]
        n % 10 == 0 -> tens[n / 10]
        else -> tens[n / 10] + "-" + ones[n % 10]
    }

    /** Football scores say "nil", not "zero". */
    fun score(n: Int): String = if (n == 0) "nil" else number(n)

    /**
     * "Liverpool win, 1.85" — what an odds cell must read as (Compliance_Conformance §3
     * Gap C). The row's short labels 1 / X / 2 / 1X / 12 / X2 mean nothing spoken, so they
     * are expanded from the team names; anything else (a team name, "Draw", "That") is
     * already a label a person would say.
     */
    fun selectionLabel(label: String, home: String, away: String): String = when (label) {
        "1" -> home + " win"
        "X" -> "Draw"
        "2" -> away + " win"
        "1X" -> home + " or draw"
        "12" -> home + " or " + away
        "X2" -> "Draw or " + away
        else -> label
    }

    /**
     * One sentence for the whole row: "Liverpool versus Ipswich, live, one nil, sixty-one
     * minutes, two of your three legs won". Prematch rows say the kickoff text instead of a
     * score. Only facts the row actually has are spoken — no "nil nil" for a prematch row.
     */
    fun sentence(match: MatchRowUi): String {
        val parts = mutableListOf(match.homeName + " versus " + match.awayName)
        if (match.isLive) {
            parts += "live"
            if (match.homeScore != null && match.awayScore != null) {
                parts += score(match.homeScore) + " " + score(match.awayScore)
            }
            parts += minuteWords(match.timeText)
        } else {
            parts += match.timeText
        }
        if (match.legsWon != null && match.legsTotal != null) {
            parts += number(match.legsWon) + " of your " + number(match.legsTotal) +
                " legs won"
        }
        match.badges.forEach { badge ->
            when (badge) {
                "BB" -> parts += "bet builder"
                "90+" -> parts += "ninety plus"
            }
        }
        return parts.joinToString(", ")
    }

    /** "Score one nil" — the live-region text, so a goal announces itself and a tick does not. */
    fun scoreAnnouncement(home: Int, away: Int): String =
        "Score " + score(home) + " " + score(away)

    /** "44m" → "forty-four minutes"; "Pauza" and other period text pass through as written. */
    private fun minuteWords(timeText: String): String {
        val minute = minuteSuffix.find(timeText)?.groupValues?.get(1)?.toIntOrNull()
            ?: return timeText
        return number(minute) + " " + (if (minute == 1) "minute" else "minutes")
    }
}
