package eu.feg.ambient.ambient.recap

import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.identity.ClubThemes
import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.Narrator
import eu.feg.ambient.ambient.narrator.NarratorGuard
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.Tone
import eu.feg.ambient.ambient.surfaces.ProtectionState
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

/**
 * N5. Reads the ledger, counts what the customer did with the app over a period, and asks the
 * narrator to say it as a gift rather than a report.
 *
 * Constructor-injected like DefaultDigestBuilder and for the same reason: this is driven by
 * the widget on a redraw, by the in-app inbox, and by a JVM test with a fake narrator, and
 * there is no DI framework to hide that behind. The counting lives in [RecapCounter] and the
 * wording in [RecapLines], both pure, so what is left here is the safety gate and the
 * decision to say anything at all.
 */
class DefaultRecapBuilder(
    private val ledger: Ledger,
    private val narrator: Narrator,
    private val protection: suspend () -> ProtectionState,
    /** The followed club, from UserState. Read at build time so a change re-themes the next recap. */
    private val myClubId: () -> String? = { null },
    /** Same shape as SpokenMoments: the surfaces pick the language, this class does not. */
    private val language: () -> NarratorLanguage = { NarratorLanguage.EN },
) : RecapBuilder {

    override suspend fun build(period: RecapPeriod, now: Instant): Recap? {
        val state = protection()

        // BLOCKED and UNVERIFIED end it here. A recap of betting activity is still a betting
        // surface, and the protection layer is consulted before anything is counted, not
        // after it is written.
        //
        // CALM is deliberately NOT handled: the recap renders unchanged in Calm Mode. It
        // holds no money and no wagering volume — the Recap class cannot carry either — so
        // there is nothing in it that rewards a customer who is cutting back for checking
        // the phone. Taking their month away at that moment would read as a punishment.
        if (state == ProtectionState.BLOCKED || state == ProtectionState.UNVERIFIED) return null

        val from = now - period.windowDays.days
        val club = myClubId()
            ?.takeIf { it.isNotBlank() }
            ?.let { ClubThemes.byId(it) }
            // byId falls back to the operator's own theme for an unknown id; "PSK" is not a
            // team the customer follows.
            ?.takeIf { it.clubId.isNotBlank() }
            ?.name
        val counts = RecapCounter.count(ledger.entries.value, from, now, club)

        // A thin recap is worse than none. "2 matches followed" is not a month with PSK, it
        // is a reminder of how little the app was used, and a card that does that is the
        // reactivation nag this feature exists to replace. Silence is the honest output.
        if (counts.matchesFollowed < MIN_MATCHES) return null

        val lang = language()
        val headline = RecapLines.headline(period, lang)
        val items = RecapLines.items(counts, period)

        // PLAIN, always. The other tones are written to be enjoyable in the moment; a recap
        // is read once and possibly shared, and it should sound like the facts.
        val narrated = runCatching {
            narrator.narrate(
                MomentFacts(type = MomentType.AWAY_DIGEST, digestItems = items),
                Tone.PLAIN,
                lang,
            )
        }.getOrNull()

        // The narrator is the caller's and a model on the ladder can time out. A widget
        // redraw is not a place to throw: no card is a correct outcome, a crashed host is not.
        if (narrated == null) return null

        // The template's headline is "While you were away", which is wrong for a recap; the
        // frame is ours, the body is the narrator's. See RecapLines for the split.
        val text = narrated.copy(
            headline = headline,
            spokenText = RecapLines.spoken(headline, narrated.spokenText),
        )

        // Belt and braces. The narrator already guards its own output, but the headline and
        // the spoken preamble were rewritten here, and anything rewritten is re-checked.
        if (!NarratorGuard.check(text)) return null

        return Recap(
            period = period,
            from = from,
            to = now,
            matchesFollowed = counts.matchesFollowed,
            teamsFollowed = counts.teamsFollowed,
            topTeam = counts.topTeam,
            topTeamCount = counts.topTeamCount,
            predictionsRight = counts.predictionsRight,
            predictionsTotal = counts.predictionsTotal,
            checkIns = counts.checkIns,
            longestStreak = counts.longestStreak,
            headline = text.headline,
            detail = text.detail,
            spokenText = text.spokenText,
        )
    }

    companion object {
        /** Below this the card is a reminder of absence rather than a recap of presence. */
        const val MIN_MATCHES = 3
    }
}

/**
 * The window each period looks back over, relative to "now" rather than to a calendar.
 *
 * A rolling window rather than "last calendar month" because the ledger keeps at most 500
 * rows and a hackathon demo's history is days old, not months: a calendar month would be
 * empty on stage. Thirty and three hundred are the spec's month and a football season.
 */
val RecapPeriod.windowDays: Int
    get() = when (this) {
        RecapPeriod.MONTH -> 30
        RecapPeriod.SEASON -> 300
    }
