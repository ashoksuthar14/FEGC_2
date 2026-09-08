package eu.feg.ambient.ambient.narrator

/**
 * Croatian voice, reusing the period vocabulary the app already shows on the Live screen
 * ("poluvrijeme"). The money words the guard blocks — kvota, ulog, isplata, dobitak —
 * have no place in any of these sentences.
 */
internal object TemplateLinesHr : TemplateLines {

    override fun lines(f: Facts, tone: Tone): Pair<String, String> = when (f.type) {
        MomentType.GOAL_ON_SLIP -> when (tone) {
            Tone.PLAIN -> f.home + " " + f.score + " · " + f.min to
                "Tvoj izbor " + f.leg + " još je u igri. Još " + f.remaining + " minuta."
            Tone.WITTY -> "To je to · " + f.score to
                f.won + " gotovo, još " + f.left + ". " + f.home + " samo mora izdržati."
            Tone.STATS -> f.score + " · " + f.min + " · " + f.legs to
                f.leg + " je potreban. Još " + f.remaining + " minuta regularnog dijela."
            Tone.ONE_LINER -> f.score + " · " + f.legs + " ✓" to "Još " + f.remaining + " min."
        }

        MomentType.LEG_DECIDED -> when (tone) {
            Tone.PLAIN -> f.leg + " je prošao" to
                f.legs + " gotovo. " + f.home + " " + f.score + " u " + f.min + "."
            Tone.WITTY -> "Jedna briga manje · " + f.legs to
                f.leg + " je sjeo. Još " + f.left + " za izdržati."
            Tone.STATS -> f.legs + " · " + f.score to
                f.leg + " riješen. Još " + f.left + " u tijeku."
            Tone.ONE_LINER -> f.legs + " ✓" to f.leg + " je prošao."
        }

        MomentType.LEG_LOST -> when (tone) {
            Tone.PLAIN -> f.leg + " nije prošao" to
                f.home + " " + f.score + " u " + f.min + ". Još " + f.left + " u tijeku."
            Tone.WITTY -> "E pa, dogodi se" to
                f.leg + " je otišao. Još " + f.left + " je u igri."
            Tone.STATS -> f.lost + " palo · " + f.legs to
                f.leg + " riješen protiv. Još " + f.left + " ostaje."
            Tone.ONE_LINER -> f.lost + " ✗" to "Još " + f.left + " u tijeku."
        }

        MomentType.SLIP_SETTLED -> when (tone) {
            Tone.PLAIN -> "Gotovo · " + f.legs to
                f.won + " od " + f.total + " je prošlo. To je sve."
            Tone.WITTY -> "I to bi bilo to" to
                f.won + " od " + f.total + " je sjelo. Stavi vodu za kavu."
            Tone.STATS -> f.legs + " riješeno" to
                f.won + " prošlo, " + f.lost + " palo. Ništa više nije u tijeku."
            Tone.ONE_LINER -> f.legs to "Riješeno."
        }

        // Činjenica o klubu koji korisnik prati, a ne poziv da nešto učini — vidi
        // TemplateLinesEn za razlog. Nema "ne propusti", nema "propuštaš".
        MomentType.KICKOFF_FOLLOWED -> when (tone) {
            Tone.PLAIN -> f.followed + " počinje za " + f.kickoff + " minuta" to
                f.home + " – " + f.away + ". Pratiš " + f.followed + "."
            Tone.WITTY -> f.followed + " kreće za " + f.kickoff + " minuta" to
                f.home + " – " + f.away + ". Kava se stigne skuhati."
            Tone.STATS -> f.followed + " · početak za " + f.kickoff + " min" to
                f.home + " – " + f.away + " · početak za " + f.kickoff + " minuta."
            Tone.ONE_LINER -> f.followed + " · " + f.kickoff + " min" to f.home + " – " + f.away + "."
        }

        MomentType.HALFTIME -> when (tone) {
            Tone.PLAIN -> "Poluvrijeme · " + f.score to
                f.home + " " + f.score + " " + f.away + ". Prvo poluvrijeme je gotovo."
            Tone.WITTY -> "Predah · " + f.score to
                f.home + " " + f.score + " " + f.away + " na odmoru. Ima još puno toga."
            Tone.STATS -> "PP " + f.score + " · " + f.legs to
                f.home + " " + f.score + " " + f.away + ". Još 45 minuta igre."
            Tone.ONE_LINER -> "PP " + f.score to "Još 45 minuta."
        }

        MomentType.MINUTES_REMAINING -> when (tone) {
            Tone.PLAIN -> "Još " + f.remaining + " minuta · " + f.score to
                f.home + " " + f.score + " " + f.away + ". " + f.leg + " još mora izdržati."
            Tone.WITTY -> "Još " + f.remaining + " minuta ovoga" to
                f.score + " i teče dalje. " + f.leg + " se drži."
            Tone.STATS -> f.min + " · još " + f.remaining + " · " + f.legs to
                f.home + " " + f.score + " " + f.away + ". " + f.leg + " je otvoren."
            Tone.ONE_LINER -> "Još " + f.remaining + " min · " + f.score to f.leg + " se drži."
        }

        MomentType.AWAY_DIGEST -> when (tone) {
            Tone.PLAIN -> "Dok te nije bilo" to f.digest
            Tone.WITTY -> "Propustio si ponešto" to f.digest
            Tone.STATS -> f.won + " prošlo · " + f.lost + " palo" to f.digest
            Tone.ONE_LINER -> "Ukratko" to f.digest
        }
    }
}
