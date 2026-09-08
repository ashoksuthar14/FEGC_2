package eu.feg.ambient.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** PRD section 6. Wire format and domain model are the same types in Phase 1. */

@Serializable
data class Sport(
    val id: String,
    val name: String,
    val icon: String,
    val liveCount: Int,
    val totalCount: Int,
)

@Serializable
data class League(
    val id: String,
    val sportId: String,
    val country: String,
    val name: String,
    val flag: String,
)

@Serializable
data class Team(
    val id: String,
    val name: String,
    val crest: String? = null,
)

@Serializable
enum class MatchState {
    @SerialName("PREMATCH") PREMATCH,
    @SerialName("LIVE") LIVE,
    @SerialName("FINISHED") FINISHED,
}

@Serializable
data class Outcome(
    val id: String,
    val label: String,
    val odds: Double,
    val isTop: Boolean = false,
    val locked: Boolean = false,
)

@Serializable
data class Market(
    val id: String,
    val name: String,
    val outcomes: List<Outcome>,
)

@Serializable
data class Match(
    val id: String,
    val leagueId: String,
    val home: Team,
    val away: Team,
    /** Serialised as ISO-8601 by kotlinx-datetime's default Instant serializer. */
    val kickoff: Instant,
    val state: MatchState,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val minute: Int? = null,
    /** "1. poluvrijeme", "2. poluvrijeme", "Pauza". */
    val period: String? = null,
    val badges: List<String> = emptyList(),
    val markets: List<Market> = emptyList(),
)
