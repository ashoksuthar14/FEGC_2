package eu.feg.ambient.ambient.surfaces

import eu.feg.ambient.ambient.engine.Router
import eu.feg.ambient.ambient.engine.Surface
import eu.feg.ambient.ambient.engine.ledger.Ledger
import eu.feg.ambient.ambient.engine.ledger.LedgerEntry
import eu.feg.ambient.ambient.engine.router.RewardTable
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import kotlinx.datetime.Clock

/**
 * The one way a moment is spoken, and the reason speaking is not invisible to the rest of
 * the system.
 *
 * [MomentSpeaker] is only a mouth: it knows about TTS and nothing else. This is what makes a
 * speak an event — a row in the ledger and a reward to the router — so that "every surface
 * we offered, and what the customer did with it, is on the record" stays true for the one
 * surface that leaves no pixels behind.
 */
class SpokenMoments(
    private val speaker: MomentSpeaker,
    private val ledger: Ledger,
    private val router: Router,
    private val protection: suspend () -> ProtectionState,
    private val language: () -> NarratorLanguage = { NarratorLanguage.EN },
) {

    /**
     * Speaks [text] on behalf of [surface], recording the request either way.
     *
     * Protection is re-checked here even though every caller draws its button behind the
     * same rule. A stored widget snapshot or an undismissed notification can outlive the
     * decision that allowed it, and audio is the surface where that would matter most.
     */
    suspend fun speak(text: String?, surface: Surface, momentType: String = "SPOKEN"): SpeakResult {
        val state = protection()
        if (!SpokenSurface.canSpeak(state)) {
            return SpeakResult.Unavailable("protection is " + state.name)
        }
        if (text.isNullOrBlank()) return SpeakResult.Unavailable("nothing to say")

        val result = speaker.speak(text, language())
        record(result, state, surface, momentType)
        return result
    }

    /** Convenience for the surfaces that hold a slip rather than a sentence. */
    suspend fun speakSlip(slip: SlipSurfaceState, surface: Surface): SpeakResult =
        speak(SpokenSurface.forSlip(slip), surface, slip.spokenMomentType())

    private fun SlipSurfaceState.spokenMomentType(): String =
        if (settled) "SLIP_SETTLED" else "MINUTES_REMAINING"

    // ---- the record --------------------------------------------------------------------

    private suspend fun record(
        result: SpeakResult,
        state: ProtectionState,
        surface: Surface,
        momentType: String,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()

        // Attribution: the most recent decision that actually reached a surface is the one
        // the customer is responding to. Without a decision to attribute it to the row still
        // stands on its own — the audit question is "was this said, and when", which does
        // not depend on a bandit arm existing.
        val origin = ledger.entries.value.firstOrNull { it.shownAt != null }

        ledger.record(
            LedgerEntry(
                id = "spk-" + now,
                momentId = origin?.momentId ?: "none",
                momentType = origin?.momentType ?: momentType,
                contextBucket = origin?.contextBucket ?: "SPOKEN",
                protection = state.name,
                score = origin?.score ?: 0.0,
                surface = surface.name,
                tone = origin?.tone ?: "-",
                armId = origin?.armId ?: "-",
                sampled = 0.0,
                reason = reasonFor(result, surface),
                // A Live Update read aloud was shown, in the only sense audio can be.
                shownAt = if (result is SpeakResult.Silenced) null else now,
                tappedAt = now,
                createdAt = now,
            ),
        )

        // The reward follows the tap, not the audio. Reaching for the speaker button is the
        // customer telling us this moment was worth their attention; whether their phone
        // happened to be on silent says nothing about the moment.
        if (origin != null && origin.armId != "-") {
            router.reward(origin.armId, origin.contextBucket, RewardTable.TAP)
            ledger.markRewarded(origin.id, RewardTable.TAP)
        }
    }

    private fun reasonFor(result: SpeakResult, surface: Surface): String = when (result) {
        is SpeakResult.Spoken ->
            "Read aloud from the " + surface.label() + ", on request."
        is SpeakResult.Silenced ->
            "Asked to read aloud from the " + surface.label() +
                ", withheld: the phone is on silent."
        is SpeakResult.LanguageFallback ->
            if (result.usedEnglish) {
                "Read aloud from the " + surface.label() + " in English — no Croatian voice."
            } else {
                "Read aloud from the " + surface.label() + " in the device's default voice."
            }
        is SpeakResult.Unavailable ->
            "Could not read aloud from the " + surface.label() + ": " + result.reason + "."
    }

    private fun Surface.label(): String = when (this) {
        Surface.LIVE_UPDATE -> "lock screen"
        Surface.WIDGET -> "widget"
        Surface.IN_APP -> "app"
        Surface.ALERT -> "alert"
        Surface.NOTHING -> "app"
    }
}
