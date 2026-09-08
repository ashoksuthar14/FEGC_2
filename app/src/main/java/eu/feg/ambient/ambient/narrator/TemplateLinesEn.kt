package eu.feg.ambient.ambient.narrator

/**
 * English voice for all eight moments in all four tones.
 *
 * Nothing here may name a price, an amount or a currency: the guard would reject it, but
 * the point is that the sentence never wanted to say it in the first place.
 */
internal object TemplateLinesEn : TemplateLines {

    override fun lines(f: Facts, tone: Tone): Pair<String, String> = when (f.type) {
        MomentType.GOAL_ON_SLIP -> when (tone) {
            Tone.PLAIN -> f.home + " " + f.score + " · " + f.min to
                "Your " + f.leg + " pick is still alive. " + f.remaining + " minutes left."
            Tone.WITTY -> "That'll do · " + f.score to
                f.won + " down, " + f.left + " to go. " + f.home + " just needs to hold on."
            Tone.STATS -> f.score + " · " + f.min + " · " + f.legs + " legs" to
                f.leg + " needed. " + f.remaining + " minutes of normal time remain."
            Tone.ONE_LINER -> f.score + " · " + f.legs + " ✓" to f.remaining + " min left."
        }

        MomentType.LEG_DECIDED -> when (tone) {
            Tone.PLAIN -> f.leg + " landed" to
                f.legs + " legs home. " + f.home + " " + f.score + " at " + f.min + "."
            Tone.WITTY -> "One less thing · " + f.legs to
                f.leg + " came good. " + f.left + " still to sweat over."
            Tone.STATS -> f.legs + " legs · " + f.score to
                f.leg + " settled. " + f.left + " still running."
            Tone.ONE_LINER -> f.legs + " ✓" to f.leg + " landed."
        }

        MomentType.LEG_LOST -> when (tone) {
            Tone.PLAIN -> f.leg + " didn't land" to
                f.home + " " + f.score + " at " + f.min + ". " + f.left + " legs still running."
            Tone.WITTY -> "Well, that happened" to
                f.leg + " slipped away. " + f.left + " still in play."
            Tone.STATS -> f.lost + " lost · " + f.legs to
                f.leg + " settled against. " + f.left + " remain."
            Tone.ONE_LINER -> f.lost + " ✗" to f.left + " still running."
        }

        MomentType.SLIP_SETTLED -> when (tone) {
            Tone.PLAIN -> "All done · " + f.legs to
                f.won + " of " + f.total + " landed. That's the lot."
            Tone.WITTY -> "That's your lot" to
                f.won + " of " + f.total + " came in. Kettle on."
            Tone.STATS -> f.legs + " settled" to
                f.won + " home, " + f.lost + " gone. Nothing left running."
            Tone.ONE_LINER -> f.legs to "Settled."
        }

        // A statement of fact about a club the customer chose to follow — never a reason to
        // open the app. "You're missing your team's game" is an inducement, which needs
        // marketing consent, is restricted in some markets, and must never reach an at-risk
        // customer. So nothing here implies the customer should do anything about it.
        MomentType.KICKOFF_FOLLOWED -> when (tone) {
            Tone.PLAIN -> f.followed + " kick off in " + f.kickoff + " minutes" to
                f.home + " – " + f.away + ". You follow " + f.followed + "."
            Tone.WITTY -> f.followed + " are up in " + f.kickoff + " minutes" to
                f.home + " – " + f.away + ". The kettle has time to boil."
            Tone.STATS -> f.followed + " · KO in " + f.kickoff + " min" to
                f.home + " – " + f.away + " · kick-off in " + f.kickoff + " minutes."
            Tone.ONE_LINER -> f.followed + " · " + f.kickoff + " min" to f.home + " – " + f.away + "."
        }

        MomentType.HALFTIME -> when (tone) {
            Tone.PLAIN -> "Half time · " + f.score to
                f.home + " " + f.score + " " + f.away + ". First half done."
            Tone.WITTY -> "Breather · " + f.score to
                f.home + " " + f.score + " " + f.away + " at the break. Plenty left in this."
            Tone.STATS -> "HT " + f.score + " · " + f.legs + " legs" to
                f.home + " " + f.score + " " + f.away + ". 45 minutes to play."
            Tone.ONE_LINER -> "HT " + f.score to "45 to play."
        }

        MomentType.MINUTES_REMAINING -> when (tone) {
            Tone.PLAIN -> f.remaining + " minutes left · " + f.score to
                f.home + " " + f.score + " " + f.away + ". " + f.leg + " still needs to hold."
            Tone.WITTY -> f.remaining + " minutes of this" to
                f.score + " and counting. " + f.leg + " is holding on."
            Tone.STATS -> f.min + " · " + f.remaining + " to play · " + f.legs to
                f.home + " " + f.score + " " + f.away + ". " + f.leg + " outstanding."
            Tone.ONE_LINER -> f.remaining + " min · " + f.score to f.leg + " holding."
        }

        MomentType.AWAY_DIGEST -> when (tone) {
            Tone.PLAIN -> "While you were away" to f.digest
            Tone.WITTY -> "You missed a bit" to f.digest
            Tone.STATS -> f.won + " won · " + f.lost + " lost" to f.digest
            Tone.ONE_LINER -> "Catch up" to f.digest
        }
    }
}
