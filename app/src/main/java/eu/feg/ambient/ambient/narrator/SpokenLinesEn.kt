package eu.feg.ambient.ambient.narrator

/**
 * The English voice for the ear: eight moments, four tones.
 *
 * These are written, not derived. Stripping the symbols out of a headline gives you a
 * sentence with the rhythm of a scoreboard, and the whole point of N1 is that a customer who
 * cannot see the card gets prose rather than a read-aloud table.
 *
 * The tones stay recognisably the tones they are on screen — a WITTY card should not become
 * a solemn announcement the moment it is spoken — but every one of them is a full sentence
 * with no abbreviation a screen reader would mangle.
 */
internal object SpokenLinesEn : SpokenLines {

    override fun line(f: SpokenFacts, tone: Tone): String = when (f.type) {

        MomentType.GOAL_ON_SLIP -> when (tone) {
            Tone.PLAIN ->
                "${f.home} are ${f.score} up, ${f.minutesPlayed} played. " +
                    "${f.won} of your ${f.legsTotal} have won, with ${f.minutesLeft} to go."
            Tone.WITTY ->
                "That will do. It is ${f.score} after ${f.minutesPlayed}. " +
                    "${f.won} down, ${f.left} to go."
            Tone.STATS ->
                "${f.score} after ${f.minutesPlayed}. " +
                    "${f.won} of your ${f.legsTotal} are home, ${f.minutesLeft} of normal time remain."
            Tone.ONE_LINER -> "${f.score}, ${f.minutesLeft} left."
        }

        MomentType.LEG_DECIDED -> when (tone) {
            Tone.PLAIN ->
                "${f.leg} landed. That is ${f.won} of your ${f.legsTotal} home, " +
                    "with ${f.home} ${f.score} after ${f.minutesPlayed}."
            Tone.WITTY ->
                "One less thing to worry about. ${f.leg} came good, " +
                    "and ${f.legsLeft} are still to sweat over."
            Tone.STATS ->
                "${f.won} of ${f.legsTotal} settled home. " +
                    "${f.leg} is in, ${f.legsLeft} still running."
            Tone.ONE_LINER -> "${f.leg} landed. ${f.won} of ${f.legsTotal} home."
        }

        MomentType.LEG_LOST -> when (tone) {
            Tone.PLAIN ->
                "${f.leg} did not land. ${f.home} ${f.score} after ${f.minutesPlayed}, " +
                    "and ${f.legsLeft} are still running."
            Tone.WITTY ->
                "Well, that happened. ${f.leg} slipped away, " +
                    "but ${f.legsLeft} are still in play."
            Tone.STATS ->
                "${f.lost} lost, ${f.won} won. " +
                    "${f.leg} settled against you, ${f.legsLeft} remain."
            Tone.ONE_LINER -> "${f.leg} is gone. ${f.legsLeft} still running."
        }

        MomentType.SLIP_SETTLED -> when (tone) {
            Tone.PLAIN ->
                "All done. ${f.won} of your ${f.legsTotal} landed. Nothing is still running."
            Tone.WITTY ->
                "That is your lot. ${f.won} of ${f.legsTotal} came in. Kettle on."
            Tone.STATS ->
                "Settled. ${f.won} home, ${f.lost} gone, nothing outstanding."
            Tone.ONE_LINER -> "All settled. ${f.won} of ${f.legsTotal} home."
        }

        // A fact about a followed club, not an invitation — see TemplateLinesEn. The ear
        // version is held to the same rule: nothing that tells the customer to act.
        MomentType.KICKOFF_FOLLOWED -> when (tone) {
            Tone.PLAIN ->
                "${f.followed} kick off against ${opponent(f)} in ${f.kickoffMinutes}."
            Tone.WITTY ->
                "${f.followed} are up against ${opponent(f)} in ${f.kickoffMinutes}. " +
                    "The kettle has time to boil."
            Tone.STATS ->
                "Kick-off in ${f.kickoffMinutes}. ${f.followed} ${venue(f)} ${opponent(f)}."
            Tone.ONE_LINER -> "${f.followed} kick off in ${f.kickoffMinutes}."
        }

        MomentType.HALFTIME -> when (tone) {
            Tone.PLAIN ->
                "Half time. ${f.home} ${f.score} ${f.away}. Forty-five minutes still to play."
            Tone.WITTY ->
                "A breather at half time, ${f.score}. There is plenty left in this one."
            Tone.STATS ->
                "Half time, ${f.score}. ${f.won} of your ${f.legsTotal} are home, " +
                    "forty-five minutes to play."
            Tone.ONE_LINER -> "Half time, ${f.score}."
        }

        MomentType.MINUTES_REMAINING -> when (tone) {
            Tone.PLAIN ->
                "${f.minutesLeft} left. ${f.home} ${f.score} ${f.away}, " +
                    "and ${f.leg} still needs to hold."
            Tone.WITTY ->
                "${f.minutesLeft} of this to go. ${f.score} and counting, " +
                    "with ${f.leg} holding on."
            Tone.STATS ->
                "${f.minutesPlayed} played, ${f.minutesLeft} to go. " +
                    "It is ${f.score}, and ${f.leg} is outstanding."
            Tone.ONE_LINER -> "${f.minutesLeft} left, ${f.score}."
        }

        MomentType.AWAY_DIGEST -> when (tone) {
            Tone.PLAIN -> "While you were away. ${f.digest}"
            Tone.WITTY -> "You missed a bit. ${f.digest}"
            Tone.STATS -> "${f.won} won, ${f.lost} lost while you were away. ${f.digest}"
            Tone.ONE_LINER -> "Catching up. ${f.digest}"
        }

        // N7. The count is already "five badges" from EnglishWords, so nothing here has a
        // digit in it. The next tier is a distance, never an instruction — see TemplateLinesEn.
        MomentType.MISSION_COMPLETE -> when (tone) {
            Tone.PLAIN ->
                "Badge earned: ${f.mission}. That is ${f.badges}. ${nextTier(f)}"
            Tone.WITTY ->
                "One for the shelf. ${f.mission}, done, and ${f.badges} on it now."
            Tone.STATS ->
                "${f.mission} complete. ${f.badge} earned, ${f.badges} in total. ${nextTier(f)}"
            Tone.ONE_LINER -> "${f.badge} earned. ${f.mission} done."
        }

        MomentType.TIER_REACHED -> when (tone) {
            Tone.PLAIN ->
                "${f.tier} reached. ${f.badges} got you there. ${nextTier(f)}"
            Tone.WITTY ->
                "Well, look at that. ${f.badges} in and you have made ${f.tier}. Kettle on."
            Tone.STATS ->
                "${f.tier} reached with ${f.badges}. ${nextTier(f)}"
            Tone.ONE_LINER -> "${f.tier} reached, ${f.badges}."
        }
    }

    /**
     * "Two badges to go for Gold." or "That is the top." when nothing sits above — and nothing
     * when the distance is unknown, for the reason TemplateLinesEn gives.
     */
    private fun nextTier(f: SpokenFacts): String {
        val next = f.nextTier ?: return "That is the top."
        val toNext = f.toNext ?: return ""
        return "$toNext to go for $next."
    }

    /**
     * The followed club's opponent. The facts carry home and away, and which of the two is
     * followed; "Hajduk kick off against Rijeka" needs the other one.
     */
    private fun opponent(f: SpokenFacts): String = if (f.followed == f.home) f.away else f.home

    private fun venue(f: SpokenFacts): String = if (f.followed == f.home) "at home to" else "away at"
}
