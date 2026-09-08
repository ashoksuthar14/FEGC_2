package eu.feg.ambient.ambient.digest

import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.Narrator
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.Tone
import eu.feg.ambient.ambient.surfaces.ProtectionState
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Reads the ledger, decides whether the customer missed anything worth a card, and asks the
 * narrator to write it.
 *
 * Everything is constructor-injected because this class has to be driven three ways: by the
 * widget on a refresh, by the Surface Lab on demand, and by a JVM test with a fake narrator.
 * There is no DI framework in this project and this is the reason there does not need to be.
 *
 * The ranking lives in [DigestRanker] rather than here, so the judgement is testable without
 * a Ledger file or a coroutine. What is left in this class is only the safety gate and the
 * decision to speak at all.
 */
class DefaultDigestBuilder(
    private val ledger: Ledger,
    private val narrator: Narrator,
    private val protection: suspend () -> ProtectionState,
    private val now: () -> Instant = { Clock.System.now() },
    /** Same shape as SpokenMoments: the surfaces pick the language, this class does not. */
    private val language: () -> NarratorLanguage = { NarratorLanguage.EN },
) : DigestBuilder {

    override suspend fun build(since: Instant): Digest? {
        val state = protection()

        // BLOCKED and UNVERIFIED end it here. A digest is still a betting surface, and the
        // whole point of the protection layer is that it is consulted before the content is
        // built rather than after it is written.
        if (state == ProtectionState.BLOCKED || state == ProtectionState.UNVERIFIED) return null

        val calm = state == ProtectionState.CALM
        val items = DigestRanker.rank(ledger.entries.value, since, matchOutcomesOnly = calm)

        // NULL IS THE POINT. A card reading "nothing happened" spends the customer's attention
        // to tell them their attention was not needed, and a single trivial item is the same
        // transaction at a smaller scale. Silence has to be a real output of this method or
        // the product's central claim is decoration.
        if (items.isEmpty()) return null
        if (items.size == 1 && !items.first().important) return null

        // PLAIN in every state, including calm. The other tones are written to be enjoyable,
        // which is not what a catch-up card is for.
        val narrated = runCatching {
            narrator.narrate(
                MomentFacts(type = MomentType.AWAY_DIGEST, digestItems = items.map { it.text }),
                Tone.PLAIN,
                language(),
            )
        }.getOrNull()

        // TemplateNarrator cannot fail, but the narrator is supplied by the caller and a model
        // on the ladder can time out. A widget redraw is not a place to throw: no card is a
        // correct outcome here, a crashed widget host is not.
        if (narrated == null) return null

        return Digest(
            items = items,
            headline = narrated.headline,
            detail = narrated.detail,
            spokenText = narrated.spokenText,
            since = since,
            generatedAt = now(),
        )
    }
}
