package eu.feg.ambient.ambient.narrator

import kotlinx.serialization.Serializable

/** The eight things worth saying about a slip in flight. */
enum class MomentType {
    GOAL_ON_SLIP,
    LEG_DECIDED,
    LEG_LOST,
    SLIP_SETTLED,
    KICKOFF_FOLLOWED,
    HALFTIME,
    MINUTES_REMAINING,
    AWAY_DIGEST,

    /**
     * N7. A mission finished and a badge was earned.
     *
     * A MOMENT LIKE ANY OTHER, and that is the design. Missions do not get their own push
     * channel: this goes through AmbientEngine.onEvent, gets scored, and competes for the
     * same one-alert-a-day budget as a goal on the customer's slip. Usually that means the
     * widget, occasionally an alert, and often nothing at all -- which is the correct
     * outcome for a badge and the reason mission nagging cannot happen here.
     */
    MISSION_COMPLETE,

    /** N7. Enough badges for the next tier. Rarer than MISSION_COMPLETE, same rules. */
    TIER_REACHED,

    /**
     * N7. A mission the customer has not finished, offered rather than announced.
     *
     * A SEPARATE TYPE BECAUSE IT IS A SEPARATE CLAIM. MISSION_COMPLETE says "you earned
     * this"; this says "here is one to go for", and using the first to say the second would
     * be the app congratulating someone for something they have not done. It carries progress
     * so the line can be specific -- "1 of 3" is an invitation, "a mission awaits" is spam.
     *
     * It is still not allowed to nag: the copy states the task and what it earns, and
     * NarratorGuard's urgency rules apply to it exactly as they do everywhere else.
     */
    MISSION_AVAILABLE,
}

enum class Tone { PLAIN, WITTY, STATS, ONE_LINER }

enum class NarratorLanguage { EN, HR }

/**
 * Which implementation actually produced the text. The demo displays this, so it must be true.
 *
 * LOCAL_GEMMA is a model we ship ourselves through LiteRT-LM; NANO is the one the OS owns.
 * They are separate values precisely because the badge must not blur them together.
 */
enum class NarratorEngine { NANO, LOCAL_GEMMA, TEMPLATE }

/**
 * Everything the narrator is allowed to know.
 *
 * COMPLIANCE — DO NOT ADD FIELDS FOR: odds, stake, balance, payout, winnings, bonus,
 * account id, user name, or anything else that identifies a person or values a wager.
 * The pitch's compliance argument is that leaking such a value into a lock-screen surface
 * is structurally impossible, not merely filtered. That claim holds only while this class
 * has no field to carry one. A new field here silently breaks it.
 */
@Serializable
data class MomentFacts(
    val type: MomentType,
    val homeTeam: String? = null,
    val awayTeam: String? = null,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val minute: Int? = null,
    /** "1. poluvrijeme" — the app's existing Croatian period strings. */
    val period: String? = null,
    val scorer: String? = null,
    val legsTotal: Int? = null,
    val legsWon: Int? = null,
    val legsLost: Int? = null,
    /** "Sparta to win" — the pick in words, never its price. */
    val myLegDescription: String? = null,
    val minutesRemaining: Int? = null,
    val followedTeam: String? = null,
    val kickoffInMinutes: Int? = null,
    val digestItems: List<String> = emptyList(),
    /** "follows Sparta for 6 weeks" — behaviour, not identity. */
    val habitHints: List<String> = emptyList(),

    // --- N7 loyalty ---------------------------------------------------------------------
    //
    // Counts and names, exactly like the rest of this class, and for the same reason: there
    // is no field here that could carry a stake, a bonus or the value of a perk, so no
    // template and no model can write one into a mission line.

    /** "Follow three teams" — the mission, in the words the customer already read. */
    val missionTitle: String? = null,

    /** "Three clubs" — the badge just earned. */
    val badgeName: String? = null,

    /** Total badges held, by weight. A count, never a currency. */
    val badgeCount: Int? = null,

    /** "Silver". The tier's own name, never a rank against other customers. */
    val tierName: String? = null,

    /** How many more badges the next tier needs, for TIER_REACHED and the widget line. */
    val badgesToNextTier: Int? = null,

    /** Where the customer has got to on an offered mission, for MISSION_AVAILABLE. */
    val missionProgress: Int? = null,

    /** What that mission needs in total. */
    val missionTarget: Int? = null,
)

data class NarratedText(
    /** At most 60 characters. */
    val headline: String,
    /** At most 120 characters. */
    val detail: String,
    /**
     * The same moment written for the ear, at most 240 characters.
     *
     * Not the visual lines with the punctuation removed. "2/3 ✓ · 61'" read aloud is "two
     * slash three tick sixty-one apostrophe", which is precisely the experience a customer
     * using a screen reader gets from a betting notification today. Numbers are expanded,
     * symbols are spoken or dropped, and it stands on its own as one or two sentences.
     *
     * It carries no price and no amount for the same reason the other two do not: the facts
     * it is written from have no field for one.
     */
    val spokenText: String,
    val engine: NarratorEngine,
    val latencyMs: Long,
)

interface Narrator {
    suspend fun narrate(
        facts: MomentFacts,
        tone: Tone,
        language: NarratorLanguage,
    ): NarratedText
}
