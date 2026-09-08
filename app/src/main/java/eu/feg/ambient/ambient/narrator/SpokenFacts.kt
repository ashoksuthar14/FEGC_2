package eu.feg.ambient.ambient.narrator

/**
 * The same facts, prepared for the ear instead of the eye.
 *
 * WHY THIS EXISTS: `"2/3 ✓ · 61'"` is a good headline and a terrible sentence. A screen reader
 * says "two slash three tick sixty-one apostrophe", which is the exact soup N1 sets out to
 * remove. So the spoken variant is not the visual line with the symbols stripped out — it is
 * written separately, from the same facts, with every number expanded and every symbol either
 * spoken or dropped.
 *
 * COMPLIANCE — this reads [MomentFacts] and nothing else, so it cannot name a price or an
 * amount for the same structural reason the visual lines cannot: there is no field to read.
 */
internal class SpokenFacts(private val f: MomentFacts, private val language: NarratorLanguage) {

    private val words: NumberWords = when (language) {
        NarratorLanguage.EN -> EnglishWords
        NarratorLanguage.HR -> CroatianWords
    }

    val type: MomentType get() = f.type
    val home: String = f.homeTeam ?: if (language == NarratorLanguage.EN) "the home side" else "domaći"
    val away: String = f.awayTeam ?: if (language == NarratorLanguage.EN) "the away side" else "gosti"

    /** "one nil", "two two" — never "1–0", which reads as "one dash zero". */
    val score: String = words.score(f.homeScore ?: 0) + " " + words.score(f.awayScore ?: 0)

    private val minuteValue: Int = f.minute ?: 0
    private val remainingValue: Int =
        f.minutesRemaining ?: (MATCH_MINUTES - minuteValue).coerceAtLeast(0)

    val minute: String = words.of(minuteValue)
    val remaining: String = words.of(remainingValue)

    /** "eighteen minutes" / "osamnaest minuta" — the noun agrees with the number in Croatian. */
    val minutesPlayed: String = minute + " " + words.minutes(minuteValue)
    val minutesLeft: String = remaining + " " + words.minutes(remainingValue)

    private val totalValue: Int = f.legsTotal ?: 0
    private val wonValue: Int = f.legsWon ?: 0
    private val lostValue: Int = f.legsLost ?: 0
    private val leftValue: Int = (totalValue - wonValue - lostValue).coerceAtLeast(0)

    val total: String = words.of(totalValue)
    val won: String = words.of(wonValue)
    val lost: String = words.of(lostValue)
    val left: String = words.of(leftValue)

    /** "three legs" / "tri izbora" — again agreeing, so a count never sounds machine-made. */
    val legsLeft: String = left + " " + words.legs(leftValue)
    val legsTotal: String = total + " " + words.legs(totalValue)

    val leg: String = f.myLegDescription
        ?: if (language == NarratorLanguage.EN) "your pick" else "tvoj izbor"
    /** The session in words for the ear: "forty five minutes". */
    val sessionTime: String = (f.sessionMinutes ?: 0).let { m ->
        if (m < 60) words.of(m) + " " + words.minutes(m)
        else {
            val h = m / 60
            val rest = m % 60
            val hourWord = if (language == NarratorLanguage.EN) {
                if (h == 1) "hour" else "hours"
            } else {
                if (h == 1) "sat" else "sata"
            }
            words.of(h) + " " + hourWord +
                (if (rest == 0) "" else " " + words.of(rest) + " " + words.minutes(rest))
        }
    }

    val game: String? = f.gameName

    val followed: String = f.followedTeam ?: home
    val kickoff: String = words.of(f.kickoffInMinutes ?: 0)
    val kickoffMinutes: String = kickoff + " " + words.minutes(f.kickoffInMinutes ?: 0)
    val period: String = f.period ?: ""

    /** Already prose in the fixtures, so it is passed through rather than rewritten. */
    val digest: String = f.digestItems.takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
        ?.let { if (it.endsWith(".")) it else "$it." }
        ?: if (language == NarratorLanguage.EN) {
            "Nothing new since you left."
        } else {
            "Ništa novo otkad si otišao."
        }

    // N7. The badge count arrives as one phrase, numeral and noun together, because in
    // Croatian the numeral has a gender ("dvije značke", not "dva značke") and a case, and
    // only the words object knows both.
    val mission: String = f.missionTitle ?: if (language == NarratorLanguage.EN) "a mission" else "misija"
    val badge: String = f.badgeName ?: if (language == NarratorLanguage.EN) "a badge" else "značka"
    val badges: String = words.countBadges((f.badgeCount ?: 0).coerceAtLeast(0))
    val tier: String = f.tierName ?: "Bronze"

    /**
     * "one of three", in words.
     *
     * Spelled out because this is written for the ear: "1 of 3" read aloud by a screen reader
     * is fine, but the spoken variant exists precisely so the sentence does not depend on the
     * reader to expand it.
     */
    val step: String? =
        if (f.missionProgress != null && f.missionTarget != null) {
            words.of(f.missionProgress) + " of " + words.of(f.missionTarget)
        } else null
    val nextTier: String? = nextTierName(tier)
    /** Null at the top tier. */
    val toNext: String? = f.badgesToNextTier?.takeIf { it > 0 }?.let { words.countBadges(it) }

    private companion object {
        const val MATCH_MINUTES = 90
    }
}

/**
 * Numbers as words, in the two languages the app speaks.
 *
 * Only 0–99 is covered because nothing a moment can carry goes higher: minutes stop at ninety,
 * legs at a handful, goals at a handful. Anything larger falls back to the digits, which the
 * TTS engine will read acceptably even if the sentence is a shade less natural.
 */
internal interface NumberWords {
    fun of(n: Int): String

    /** Zero in a score is not "zero". */
    fun score(n: Int): String

    /** The noun that follows a count of minutes, agreeing with it. */
    fun minutes(n: Int): String

    /** The noun that follows a count of legs on a slip. */
    fun legs(n: Int): String

    /** The noun that follows a count of badges, in the form a headline can use after digits. */
    fun badges(n: Int): String

    /**
     * Numeral and noun together — "five badges". The default is the pattern every other count
     * uses; Croatian overrides it because the numeral itself changes with the noun.
     */
    fun countBadges(n: Int): String = of(n) + " " + badges(n)
}

internal object EnglishWords : NumberWords {

    private val ones = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen",
    )

    private val tens = listOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
    )

    override fun of(n: Int): String = when {
        n < 0 || n > 99 -> n.toString()
        n < 20 -> ones[n]
        n % 10 == 0 -> tens[n / 10]
        else -> tens[n / 10] + "-" + ones[n % 10]
    }

    override fun score(n: Int): String = if (n == 0) "nil" else of(n)

    override fun minutes(n: Int): String = if (n == 1) "minute" else "minutes"

    override fun legs(n: Int): String = if (n == 1) "leg" else "legs"

    override fun badges(n: Int): String = if (n == 1) "badge" else "badges"
}

/**
 * Croatian numerals plus the case agreement that makes them sound spoken rather than
 * generated: one minuta, two to four minute, five and above minuta — and the rule resets
 * above twenty, so twenty-one takes the singular again.
 */
internal object CroatianWords : NumberWords {

    private val ones = listOf(
        "nula", "jedan", "dva", "tri", "četiri", "pet", "šest", "sedam", "osam", "devet",
        "deset", "jedanaest", "dvanaest", "trinaest", "četrnaest", "petnaest", "šesnaest",
        "sedamnaest", "osamnaest", "devetnaest",
    )

    private val tens = listOf(
        "", "", "dvadeset", "trideset", "četrdeset", "pedeset", "šezdeset", "sedamdeset",
        "osamdeset", "devedeset",
    )

    override fun of(n: Int): String = when {
        n < 0 || n > 99 -> n.toString()
        n < 20 -> ones[n]
        n % 10 == 0 -> tens[n / 10]
        else -> tens[n / 10] + " " + ones[n % 10]
    }

    override fun score(n: Int): String = of(n)

    override fun minutes(n: Int): String = when (form(n)) {
        Form.ONE -> "minuta"
        Form.FEW -> "minute"
        Form.MANY -> "minuta"
    }

    override fun legs(n: Int): String = when (form(n)) {
        Form.ONE -> "izbor"
        Form.FEW -> "izbora"
        Form.MANY -> "izbora"
    }

    /** Nominative: "1 značka", "2 značke", "5 znački" — the form the visual lines use. */
    override fun badges(n: Int): String = when (form(n)) {
        Form.ONE -> "značka"
        Form.FEW -> "značke"
        Form.MANY -> "znački"
    }

    /**
     * "Značka" is feminine, so the numeral agrees too: "jednu značku", "dvije značke", "pet
     * znački" — never "jedan značka" or "dva značke". The ONE form is accusative because every
     * spoken frame it lands in is "imaš …" or "trebaš …", and the plural forms are the same
     * in both cases so no other frame needs a second variant.
     */
    override fun countBadges(n: Int): String {
        val noun = when (form(n)) {
            Form.ONE -> "značku"
            Form.FEW -> "značke"
            Form.MANY -> "znački"
        }
        return feminine(n) + " " + noun
    }

    /** The numeral with a feminine head noun: "jednu", "dvije", "dvadeset dvije". */
    private fun feminine(n: Int): String {
        if (n < 0 || n > 99) return n.toString()
        val last = n % 10
        val lastTwo = n % 100
        val feminineOnes = when {
            lastTwo in 11..14 -> null
            last == 1 -> "jednu"
            last == 2 -> "dvije"
            else -> null
        } ?: return of(n)
        return if (n < 10) feminineOnes else tens[n / 10] + " " + feminineOnes
    }

    private enum class Form { ONE, FEW, MANY }

    private fun form(n: Int): Form {
        val last = n % 10
        val lastTwo = n % 100
        return when {
            lastTwo in 11..14 -> Form.MANY
            last == 1 -> Form.ONE
            last in 2..4 -> Form.FEW
            else -> Form.MANY
        }
    }
}

/** One spoken sentence per moment and tone. Implemented once per language. */
internal interface SpokenLines {
    fun line(f: SpokenFacts, tone: Tone): String

    companion object {
        fun of(language: NarratorLanguage): SpokenLines = when (language) {
            NarratorLanguage.EN -> SpokenLinesEn
            NarratorLanguage.HR -> SpokenLinesHr
        }

        /**
         * The spoken variant from facts alone, for the callers that have no narrator to hand
         * — chiefly a model narrator whose output arrived without a SPOKEN line.
         */
        fun compose(facts: MomentFacts, tone: Tone, language: NarratorLanguage): String =
            sentenceCase(of(language).line(SpokenFacts(facts, language), tone))

        /**
         * Capitalises the start of every sentence.
         *
         * The lines are assembled from parts, and a part that begins with a spelled-out
         * number begins lower-case: "sixty-one minutes played. two of your three legs have
         * won." Capitalising inside the templates would mean a separate cased copy of every
         * number word, so it is done once, here, over the finished sentence.
         */
        fun sentenceCase(text: String): String {
            val out = StringBuilder(text.length)
            var startOfSentence = true
            text.forEach { ch ->
                out.append(if (startOfSentence && ch.isLetter()) ch.uppercaseChar() else ch)
                startOfSentence = when {
                    ch == '.' || ch == '!' || ch == '?' -> true
                    ch == ' ' -> startOfSentence
                    else -> false
                }
            }
            return out.toString()
        }
    }
}
