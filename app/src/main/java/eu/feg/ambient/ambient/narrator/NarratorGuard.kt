package eu.feg.ambient.ambient.narrator

/**
 * The last thing every narrator output passes through, template output included.
 *
 * This is deliberately a pure function over the finished text rather than a filter applied
 * to the model: it catches prompt drift, a bad template edit and a hallucinated price with
 * the same rule, and it can be unit-tested without a model.
 */
object NarratorGuard {

    const val MAX_HEADLINE = 60
    const val MAX_DETAIL = 120

    /**
     * The spoken variant is prose, so it gets more room than the two visual lines — but not
     * unlimited room. Past roughly this length a spoken moment stops being a glance and
     * becomes something the customer has to wait out, which is the opposite of the point.
     */
    const val MAX_SPOKEN = 240

    /** Money words, in both languages the app speaks. */
    private val BLOCKED_WORDS = listOf(
        // English
        "bet", "bets", "betting", "odds", "stake", "wager", "bonus",
        "cashout", "payout", "deposit", "jackpot", "guaranteed",
        // Croatian
        "kvota", "kvote", "oklada", "okladi", "ulog", "uloži", "isplata", "dobitak",
    )

    /**
     * Phrases that are only a problem as a phrase.
     *
     * The first three are money. The rest are inducement — the "you're missing your team's
     * game" framing that turns a fact about a followed club into a nudge to act. An
     * inducement needs marketing consent, is restricted in some markets, and must never
     * reach an at-risk or self-excluded customer, so it is not a matter of tone: it is
     * rejected outright, whatever narrator produced it.
     *
     * "missing" lives here in its inducing forms rather than in [BLOCKED_WORDS], because the
     * bare word is also ordinary English — "the missing leg", "a missing voice" — and a
     * word-boundary match would reject those legitimate sentences. What is forbidden is
     * telling the customer *they* are missing something, so that is what is matched.
     *
     * "bet now" is already caught by the "bet" word rule; it is listed anyway so the reason
     * the guard reports names the inducement, not merely the noun.
     *
     * The urgency group exists for N7. A mission with a countdown is a pressure mechanic, and
     * the copy that sells the countdown — "expires soon", "hurry" — is the tell. Missions
     * never expire today (Mission.isUrgent is always false), so this group is the rule that
     * keeps that true in the text even if the model grows an expiry. NOT in the list, on
     * purpose: "soon", "today", "now", "last" and "expires" on their own. "Kick-off in 40
     * min, soon" and "three days in a row" are streaks and schedules, and a guard that
     * rejects a legitimate line is worse than one that lets a mild one through.
     */
    private val BLOCKED_PHRASES = listOf(
        // Money
        "free bet", "cash out", "win money",
        // Inducement, English. Both apostrophes: models and keyboards disagree about them.
        "you're missing", "you’re missing", "you are missing", "missing your", "missing out",
        "don't miss", "don’t miss", "do not miss", "miss out",
        "last chance", "bet now", "back them",
        // Inducement, Croatian. "ne propusti" is "don't miss"; "propuštaš" is "you're missing"
        // (second person, so the bare stem — "propustio si", he missed — still passes and the
        // digest's "Propustio si ponešto" stays legal); "zadnja/posljednja prilika" is "last
        // chance"; "kladi se"/"kladite se" is the imperative "place a bet", which is both
        // "bet now" and "back them" in one verb.
        "ne propusti", "propuštaš", "zadnja prilika", "posljednja prilika", "kladi se", "kladite se",
        // Urgency, English. "hurry" is a bare word here because it has no innocent use in a
        // moment; the one it does have, "no hurry", is listed in [SAFE_PHRASES].
        "expires soon", "hurry", "act now", "don't wait", "don’t wait", "do not wait",
        "ends today", "only today", "while it lasts",
        // Urgency, Croatian. "požuri"/"požurite" is the imperative "hurry" (tu and vi forms —
        // the app speaks informally, but a model might not). "istječe" is "is expiring", the
        // verb every countdown is built from ("ponuda istječe", "vrijeme istječe"); the stem
        // is matched alone because there is no moment in which a thing running out is a fact
        // rather than a push. The colloquial spelling "ističe" is only matched with "uskoro"
        // (soon) beside it, because on its own it is also "stands out" and "points out" —
        // "Salah se ističe" is commentary, not a countdown. "samo danas" is "only today".
        // "ne čekaj"/"ne čekajte" is "don't wait". "odmah" (right away) is deliberately
        // absent: "odmah nakon poluvremena" is a schedule, not a nudge.
        "požuri", "požurite", "istječe", "ističe uskoro", "uskoro ističe",
        "samo danas", "ne čekaj", "ne čekajte",
    )

    /**
     * Phrases lifted out of the text before the word and phrase rules run.
     *
     * Each one contains a blocked word and is the opposite of what that word is blocked for.
     * "deposit limit" is the responsible-gambling tool the SET_A_LIMIT mission is named
     * after, and its title has to be narratable when the badge is earned; "deposit" alone
     * stays blocked because "make a deposit" is the inducement. "no hurry" is the same shape
     * for the urgency group. The list is short on purpose: every entry here is a hole in the
     * guard, so it has to be a phrase that cannot be read as the thing the rule forbids.
     */
    private val SAFE_PHRASES = listOf(
        "deposit limit", "deposit limits", "no hurry",
    )

    private val CURRENCY = Regex("[€$£]|EUR", RegexOption.IGNORE_CASE)

    /**
     * A number with exactly two decimals is an odd or an amount. A score ("1–0"), a minute
     * ("61'") and a period ("1. poluvrijeme") must all still pass, which is why this looks
     * for a digit on both sides of the separator.
     */
    private val TWO_DECIMALS = Regex("(?<![0-9])[0-9]+[.,][0-9]{2}(?![0-9])")

    /**
     * Word boundaries are written out rather than using \\b so that Croatian diacritics
     * behave, and so "Betis" and "Bettega" never trip the "bet" rule.
     */
    private val wordPatterns: List<Pair<String, Regex>> = BLOCKED_WORDS.map { word ->
        word to Regex("(?<![\\p{L}])" + Regex.escape(word) + "(?![\\p{L}])", RegexOption.IGNORE_CASE)
    }

    private val phrasePatterns: List<Pair<String, Regex>> = BLOCKED_PHRASES.map { phrase ->
        phrase to Regex("(?<![\\p{L}])" + Regex.escape(phrase) + "(?![\\p{L}])", RegexOption.IGNORE_CASE)
    }

    private val safePatterns: List<Regex> = SAFE_PHRASES.map { phrase ->
        Regex("(?<![\\p{L}])" + Regex.escape(phrase) + "(?![\\p{L}])", RegexOption.IGNORE_CASE)
    }

    fun check(text: NarratedText): Boolean = violation(text) == null

    /** The rule that tripped, for debug logging. Null when the text is clean. */
    fun violation(text: NarratedText): String? {
        if (text.headline.isBlank()) return "headline is blank"
        if (text.detail.isBlank()) return "detail is blank"
        if (text.headline.length > MAX_HEADLINE) {
            return "headline is " + text.headline.length + " chars, max " + MAX_HEADLINE
        }
        if (text.detail.length > MAX_DETAIL) {
            return "detail is " + text.detail.length + " chars, max " + MAX_DETAIL
        }
        // The spoken line is checked exactly like the other two. It reaches the customer as
        // audio rather than pixels, which makes it more exposed, not less: a price read out
        // loud in a room is a disclosure the screen never made.
        if (text.spokenText.isBlank()) return "spokenText is blank"
        if (text.spokenText.length > MAX_SPOKEN) {
            return "spokenText is " + text.spokenText.length + " chars, max " + MAX_SPOKEN
        }

        val body = text.headline + " " + text.detail + " " + text.spokenText

        CURRENCY.find(body)?.let { return "currency symbol: " + it.value }
        TWO_DECIMALS.find(body)?.let { return "two-decimal number: " + it.value }

        // Safe phrases are removed only for the vocabulary rules, after the currency and
        // decimal rules have seen the whole text: "deposit limit €50" is still a price.
        val vocabulary = safePatterns.fold(body) { acc, pattern -> pattern.replace(acc, " ") }
        phrasePatterns.forEach { (phrase, pattern) ->
            if (pattern.containsMatchIn(vocabulary)) return "blocked phrase: " + phrase
        }
        wordPatterns.forEach { (word, pattern) ->
            if (pattern.containsMatchIn(vocabulary)) return "blocked word: " + word
        }
        return null
    }
}
