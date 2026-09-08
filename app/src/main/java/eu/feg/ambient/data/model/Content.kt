package eu.feg.ambient.data.model

import kotlinx.serialization.Serializable

/** Everything that is content rather than offer: promos, casino, the Arena feed. */

@Serializable
data class Promo(
    val id: String,
    val title: String,
    val subtitle: String,
    val cta: String,
    /** "ENDS IN: 4 HOURS" on the Promo screen hero. */
    val endsIn: String? = null,
    val category: String = "PROMO",
)

@Serializable
data class CasinoGame(
    val id: String,
    val name: String,
    val provider: String,
    val section: String,
    val badge: String? = null,
    val jackpot: String? = null,
)

@Serializable
data class RecentWin(
    val id: String,
    val player: String,
    val game: String,
    val amount: String,
)

/** One tipster row on PSK Arena (PRD section 5.8). */
@Serializable
data class ArenaTip(
    val id: String,
    val username: String,
    val inspiration: Int,
    val inspirationLabel: String,
    val eventCount: Int,
    val type: String,
    val day: String,
    val time: String,
    val stake: Double,
    val course: Double,
    val possiblePayment: Double,
    val badges: List<String> = emptyList(),
    /** Match ids this tipster backed — "copy slip" loads these into the bet slip. */
    val matchIds: List<String> = emptyList(),
)
