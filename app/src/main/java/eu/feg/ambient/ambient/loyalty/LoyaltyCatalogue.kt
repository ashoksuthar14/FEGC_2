package eu.feg.ambient.ambient.loyalty

import eu.feg.ambient.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The mission and perk catalogues, as they are written in `assets/mock`.
 *
 * The JSON carries an icon KEY, never a resource id: a number in a fixture file is meaningless
 * to read, breaks the moment R is regenerated, and would put an Android type in a file the
 * catalogue tests parse on the JVM. [iconFor] is the one place the two vocabularies meet.
 */
@Serializable
data class MissionEntry(
    val id: String,
    val title: String,
    val description: String,
    val type: MissionType,
    val target: Int,
    val badgeId: String,
    val badgeName: String,
    val icon: String,
    val weight: Int = 1,
) {
    fun toMission(progress: Int): Mission = Mission(
        id = id,
        title = title,
        description = description,
        type = type,
        target = target,
        progress = progress,
        expiresAt = null,
        badgeId = badgeId,
    )
}

/**
 * Reads both catalogues.
 *
 * Takes a plain reader rather than a Context so the compliance tests can parse the shipped
 * fixtures on the JVM. Those tests are the whole point of this feature having a catalogue at
 * all -- a rule nothing checks is a comment.
 */
class LoyaltyCatalogue(private val read: (String) -> String) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val missions: List<MissionEntry> by lazy {
        json.decodeFromString(read("mock/missions.json"))
    }

    val perks: List<Perk> by lazy {
        json.decodeFromString(read("mock/perks.json"))
    }

    fun mission(id: String): MissionEntry? = missions.firstOrNull { it.id == id }

    fun perk(id: String): Perk? = perks.firstOrNull { it.id == id }

    /** The badge a mission awards, before it has been earned. */
    fun badgeFor(entry: MissionEntry): Badge = Badge(
        id = entry.badgeId,
        name = entry.badgeName,
        iconRes = iconFor(entry.icon),
        earnedAt = null,
        weight = entry.weight,
    )

    companion object {
        /**
         * Unknown keys fall back to the star rather than to zero.
         *
         * A zero resource id crashes at inflation time, and a badge is exactly the kind of
         * content someone adds to a JSON file long after they have stopped reading this class.
         */
        fun iconFor(key: String): Int = when (key) {
            "shield" -> R.drawable.ic_badge_shield
            "crest" -> R.drawable.ic_badge_crest
            "ticket" -> R.drawable.ic_badge_ticket
            "widget" -> R.drawable.ic_badge_widget
            "ball" -> R.drawable.ic_ball
            else -> R.drawable.ic_badge_star
        }
    }
}
