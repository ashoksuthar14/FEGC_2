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
)

data class NarratedText(
    /** At most 60 characters. */
    val headline: String,
    /** At most 120 characters. */
    val detail: String,
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
