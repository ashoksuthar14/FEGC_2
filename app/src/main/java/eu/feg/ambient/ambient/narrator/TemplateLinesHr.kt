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
                f.legPhrase + " još je u igri. Još " + f.remaining + " minuta."
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

        // N7. Brojevi se slažu s imenicom ("5 znački", "2 značke", "1 značka") preko
        // CroatianWords, a razine su prevedene u TierHr jer MomentFacts nosi englesko ime.
        // Okviri rečenica su birani tako da broj ostaje u nominativu ("ukupno 1 značka",
        // "još 1 značka do…"), jer bi "imaš 1 značka" bio točno onaj strojni hrvatski
        // koji ova značajka postoji da izbjegne.
        MomentType.MISSION_COMPLETE -> when (tone) {
            Tone.PLAIN -> "Značka osvojena · " + f.mission to
                f.badge + ". Ukupno " + badges(f) + ". " + nextTier(f)
            Tone.WITTY -> "Jedna za policu · " + f.badge to
                f.mission + ", gotovo. Ukupno " + badges(f) + " i broji se dalje."
            Tone.STATS -> badges(f) + " · " + TierHr.level(f.tier) to
                f.mission + " dovršeno. Osvojena značka " + f.badge + ". " + nextTier(f)
            Tone.ONE_LINER -> f.badge + " ✓" to f.mission + " gotovo."
        }

        // Ponuda, nikad poziv. Kaze se zadatak, dokle je korisnik stigao i sto nosi -- i
        // nista o roku, jer roka nema.
        // Cinjenica o vremenu i nista drugo. Bez rezultata, bez stanja racuna, bez poticaja
        // da se nastavi i bez naredbe da se prestane.
        MomentType.SESSION_LENGTH -> when (tone) {
            Tone.PLAIN -> "Igras vec " + f.sessionTime to
                (f.game?.let { it + ". " } ?: "") + "Samo da znas."
            Tone.WITTY -> "Proslo je " + f.sessionTime to
                (f.game?.let { it + ". " } ?: "") + "Vrijeme to voli napraviti."
            Tone.STATS -> "Sesija · " + f.sessionTime to
                (f.game?.let { it + ". " } ?: "") + "Vrijeme igre u ovoj sesiji."
            Tone.ONE_LINER -> f.sessionTime + " igre" to "U ovoj sesiji."
        }

        MomentType.MISSION_AVAILABLE -> when (tone) {
            Tone.PLAIN -> f.mission to
                (f.missionStep?.let { it + ". " } ?: "") + "Nosi znacku " + f.badge + "."
            Tone.WITTY -> "Nesto za cilj · " + f.mission to
                (if (f.missionLeft > 0) "Jos " + CroatianWords.badges(f.missionLeft) +
                    " i znacka " + f.badge + " je tvoja." else "Znacka " + f.badge + " te ceka.")
            Tone.STATS -> f.mission + " · " + (f.missionStep ?: "otvoreno") to
                "Nosi znacku " + f.badge + ". Trenutno " + f.tier + ", ukupno " + badges(f) + "."
            Tone.ONE_LINER -> f.mission to (f.missionStep ?: "Otvoreno") + " · " + f.badge
        }

        MomentType.TIER_REACHED -> when (tone) {
            Tone.PLAIN -> TierHr.level(f.tier) to
                "Ukupno " + badges(f) + " i " + TierHr.level(f.tier).lowercase() + " je tvoja. " + nextTier(f)
            Tone.WITTY -> "Gle ti to · " + TierHr.level(f.tier) to
                "Ukupno " + badges(f) + " i stigao si do " + TierHr.toward(f.tier) + ". Stavi vodu za kavu."
            Tone.STATS -> TierHr.level(f.tier) + " · " + badges(f) to
                "Razina dosegnuta, ukupno " + badges(f) + ". " + nextTier(f)
            Tone.ONE_LINER -> TierHr.level(f.tier) + " ✓" to "Ukupno " + badges(f) + "."
        }
    }

    /** "5 znački", "2 značke", "1 značka" — nominativ, uz "ukupno" i "još". */
    private fun badges(f: Facts): String = f.badges + " " + CroatianWords.badges(f.badgeCount)

    /**
     * "Još 2 značke do zlatne razine." ili "Najviša razina." kad iznad nema ničega — a ništa
     * kad činjenice nisu donijele broj, jer je rečenica o sljedećoj razini bez broja poziv,
     * a ne činjenica.
     */
    private fun nextTier(f: Facts): String {
        val next = f.nextTier ?: return "Najviša razina."
        val toNext = f.toNext ?: return ""
        return "Još " + toNext + " " + CroatianWords.badges(toNext) + " do " + TierHr.toward(next) + "."
    }
}
