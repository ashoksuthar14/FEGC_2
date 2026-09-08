package eu.feg.ambient.ambient.narrator

/**
 * The Croatian voice for the ear, matching the register of [TemplateLinesHr] — the same
 * informal second person, the same vocabulary, and the same absence of kvota, ulog, isplata
 * and dobitak.
 *
 * Counts arrive already agreed with their noun ([CroatianWords]), because "3 izbora" read
 * aloud as "tri izbor" is exactly the machine-made sound this feature exists to avoid.
 */
internal object SpokenLinesHr : SpokenLines {

    override fun line(f: SpokenFacts, tone: Tone): String = when (f.type) {

        MomentType.GOAL_ON_SLIP -> when (tone) {
            Tone.PLAIN ->
                "${f.home} vodi ${f.score}, odigrano je ${f.minutesPlayed}. " +
                    "Prošlo je ${f.won} od ${f.legsTotal}, ostaje još ${f.minutesLeft}."
            Tone.WITTY ->
                "To je to. ${f.score} nakon ${f.minutesPlayed}. " +
                    "${f.won} gotovo, još ${f.left}."
            Tone.STATS ->
                "${f.score} nakon ${f.minutesPlayed}. " +
                    "${f.won} od ${f.legsTotal} je prošlo, još ${f.minutesLeft} regularnog dijela."
            Tone.ONE_LINER -> "${f.score}, još ${f.minutesLeft}."
        }

        MomentType.LEG_DECIDED -> when (tone) {
            Tone.PLAIN ->
                "${f.leg} je prošao. To je ${f.won} od ${f.legsTotal}, " +
                    "a ${f.home} vodi ${f.score} nakon ${f.minutesPlayed}."
            Tone.WITTY ->
                "Jedna briga manje. ${f.leg} je sjeo, " +
                    "a još ${f.legsLeft} treba izdržati."
            Tone.STATS ->
                "${f.won} od ${f.legsTotal} je riješeno. " +
                    "${f.leg} je prošao, još ${f.legsLeft} je u tijeku."
            Tone.ONE_LINER -> "${f.leg} je prošao. ${f.won} od ${f.legsTotal}."
        }

        MomentType.LEG_LOST -> when (tone) {
            Tone.PLAIN ->
                "${f.leg} nije prošao. ${f.home} ${f.score} nakon ${f.minutesPlayed}, " +
                    "a još ${f.legsLeft} je u tijeku."
            Tone.WITTY ->
                "E pa, dogodi se. ${f.leg} je otišao, " +
                    "ali još ${f.legsLeft} je u igri."
            Tone.STATS ->
                "${f.lost} palo, ${f.won} prošlo. " +
                    "${f.leg} je riješen protiv tebe, ostaje još ${f.legsLeft}."
            Tone.ONE_LINER -> "${f.leg} je otišao. Još ${f.legsLeft} u tijeku."
        }

        MomentType.SLIP_SETTLED -> when (tone) {
            Tone.PLAIN ->
                "Gotovo je. Prošlo je ${f.won} od ${f.legsTotal}. Ništa više nije u tijeku."
            Tone.WITTY ->
                "I to bi bilo to. ${f.won} od ${f.legsTotal} je sjelo. Stavi vodu za kavu."
            Tone.STATS ->
                "Riješeno. ${f.won} prošlo, ${f.lost} palo, ništa nije ostalo."
            Tone.ONE_LINER -> "Sve je riješeno. ${f.won} od ${f.legsTotal}."
        }

        // Činjenica, ne poziv — vidi TemplateLinesEn. Imena klubova ostaju u nominativu
        // ("igraju Hajduk i Rijeka"), jer se "protiv Rijeke" ne može sklanjati iz teksta.
        MomentType.KICKOFF_FOLLOWED -> when (tone) {
            Tone.PLAIN ->
                "${f.followed} počinje za ${f.kickoffMinutes}. Igraju ${f.home} i ${f.away}."
            Tone.WITTY ->
                "${f.followed} kreće za ${f.kickoffMinutes}, igraju ${f.home} i ${f.away}. " +
                    "Kava se stigne skuhati."
            Tone.STATS ->
                "Početak za ${f.kickoffMinutes}. ${f.home} kod kuće, gost je ${f.away}."
            Tone.ONE_LINER -> "${f.followed} počinje za ${f.kickoffMinutes}."
        }

        MomentType.HALFTIME -> when (tone) {
            Tone.PLAIN ->
                "Poluvrijeme. ${f.home} ${f.score} ${f.away}. Ostaje još četrdeset pet minuta."
            Tone.WITTY ->
                "Predah na poluvremenu, ${f.score}. Ima toga još u ovoj utakmici."
            Tone.STATS ->
                "Poluvrijeme, ${f.score}. ${f.won} od ${f.legsTotal} je prošlo, " +
                    "još četrdeset pet minuta igre."
            Tone.ONE_LINER -> "Poluvrijeme, ${f.score}."
        }

        MomentType.MINUTES_REMAINING -> when (tone) {
            Tone.PLAIN ->
                "Ostaje još ${f.minutesLeft}. ${f.home} ${f.score} ${f.away}, " +
                    "a ${f.leg} još mora izdržati."
            Tone.WITTY ->
                "Još ${f.minutesLeft} ovoga. ${f.score} i broji se, " +
                    "a ${f.leg} se drži."
            Tone.STATS ->
                "Odigrano ${f.minutesPlayed}, ostaje ${f.minutesLeft}. " +
                    "Rezultat je ${f.score}, a ${f.leg} je još otvoren."
            Tone.ONE_LINER -> "Još ${f.minutesLeft}, ${f.score}."
        }

        MomentType.AWAY_DIGEST -> when (tone) {
            Tone.PLAIN -> "Dok te nije bilo. ${f.digest}"
            Tone.WITTY -> "Propustio si ponešto. ${f.digest}"
            Tone.STATS -> "${f.won} prošlo, ${f.lost} palo dok te nije bilo. ${f.digest}"
            Tone.ONE_LINER -> "Ukratko. ${f.digest}"
        }

        // N7. "pet znački", "dvije značke", "jednu značku" dolaze iz CroatianWords već u
        // ženskom rodu i u akuzativu, pa je svaki okvir ovdje "imaš …" ili "trebaš …" —
        // rečenica u kojoj taj padež sjeda. Razine su prevedene u [TierHr].
        MomentType.MISSION_COMPLETE -> when (tone) {
            Tone.PLAIN ->
                "Značka osvojena: ${f.mission}. Sad imaš ${f.badges}. ${nextTier(f)}"
            Tone.WITTY ->
                "Jedna za policu. ${f.mission}, gotovo, i sad imaš ${f.badges}."
            Tone.STATS ->
                "${f.mission} dovršeno. Osvojena značka ${f.badge}, ukupno imaš ${f.badges}. ${nextTier(f)}"
            Tone.ONE_LINER -> "Značka ${f.badge} osvojena. ${f.mission} gotovo."
        }

        MomentType.MISSION_AVAILABLE -> when (tone) {
            Tone.PLAIN ->
                f.mission + ". " + (f.step?.let { it + ". " } ?: "") +
                    "Nosi znacku " + f.badge + "."
            Tone.WITTY ->
                "Nesto za cilj. " + f.mission + ", a na kraju te ceka znacka " + f.badge + "."
            Tone.STATS ->
                f.mission + ", " + (f.step ?: "jos nije poceto") + ". Nosi znacku " +
                    f.badge + ", a trenutno si na razini " + f.tier + "."
            Tone.ONE_LINER -> f.mission + ". Znacka " + f.badge + "."
        }

        MomentType.TIER_REACHED -> when (tone) {
            Tone.PLAIN ->
                "${TierHr.level(f.tier)} dosegnuta. Imaš ${f.badges}. ${nextTier(f)}"
            Tone.WITTY ->
                "Gle ti to. Imaš ${f.badges} i stigao si do ${TierHr.toward(f.tier)}. Stavi vodu za kavu."
            Tone.STATS ->
                "${TierHr.level(f.tier)} dosegnuta, imaš ${f.badges}. ${nextTier(f)}"
            Tone.ONE_LINER -> "${TierHr.level(f.tier)}, imaš ${f.badges}."
        }
    }

    /** "Do zlatne razine trebaš još dvije značke.", "To je najviša razina.", ili ništa. */
    private fun nextTier(f: SpokenFacts): String {
        val next = f.nextTier ?: return "To je najviša razina."
        val toNext = f.toNext ?: return ""
        return "Do ${TierHr.toward(next)} trebaš još $toNext."
    }
}

/**
 * The tier names in Croatian.
 *
 * MomentFacts carries the enum's English name ("Silver") because that is what the loyalty
 * model is written in, and translating it here rather than in the facts keeps the facts
 * language-neutral. Two forms are needed: the level as a subject ("Srebrna razina") and the
 * level as a destination after "do" ("do zlatne razine", genitive). An unknown name falls
 * through unchanged, so a tier added to the enum reads oddly rather than crashing.
 */
internal object TierHr {
    fun level(tier: String): String = when (tier.lowercase()) {
        "bronze" -> "Brončana razina"
        "silver" -> "Srebrna razina"
        "gold" -> "Zlatna razina"
        "platinum" -> "Platinasta razina"
        else -> tier
    }

    fun toward(tier: String): String = when (tier.lowercase()) {
        "bronze" -> "brončane razine"
        "silver" -> "srebrne razine"
        "gold" -> "zlatne razine"
        "platinum" -> "platinaste razine"
        else -> tier
    }
}
