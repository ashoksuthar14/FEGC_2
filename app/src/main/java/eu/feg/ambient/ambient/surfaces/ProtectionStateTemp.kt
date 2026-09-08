package eu.feg.ambient.ambient.surfaces

import eu.feg.ambient.data.model.Limits
import eu.feg.ambient.data.model.RiskState
import eu.feg.ambient.data.model.UserState
import kotlinx.datetime.Clock

/**
 * TEMPORARY — replaced in step 13A by the register check and age assurance.
 *
 * Renderers must depend on [ProtectionState] only, never on [UserState.riskState]. That is
 * the whole point of this file existing separately: when 13A arrives it changes how the
 * state is derived and nothing that draws a surface has to be touched.
 */
fun UserState.toProtectionStateTemp(ageVerified: Boolean): ProtectionState = when {
    !ageVerified -> ProtectionState.UNVERIFIED
    riskState == RiskState.SELF_EXCLUDED -> ProtectionState.BLOCKED
    riskState == RiskState.AT_RISK -> ProtectionState.CALM
    panicUntil?.let { it > Clock.System.now() } == true -> ProtectionState.CALM
    limits.anyAtOrAbove(0.8) -> ProtectionState.CALM
    else -> ProtectionState.NORMAL
}

/** True when any limit is at or past the given fraction of its cap. */
fun Limits.anyAtOrAbove(fraction: Double): Boolean {
    fun ratio(used: Double, cap: Double) = if (cap <= 0.0) 0.0 else used / cap
    return ratio(depositUsed, depositLimit) >= fraction ||
        ratio(lossUsed, lossLimit) >= fraction ||
        ratio(timeUsed.toDouble(), timeLimit.toDouble()) >= fraction
}

/**
 * Whether a Live Update may exist at all in this state.
 *
 * A pure predicate rather than an `if` inside the renderer, so the compliance property —
 * that an unverified or self-excluded user gets no lock-screen surface — can be tested
 * without an Android runtime, and so the controller and the renderer cannot disagree
 * about it.
 */
fun ProtectionState.allowsLiveUpdate(): Boolean = when (this) {
    ProtectionState.NORMAL, ProtectionState.CALM -> true
    ProtectionState.UNVERIFIED, ProtectionState.BLOCKED -> false
}

/** CALM shows the match, never the bet: no leg progress and no narrated line about picks. */
fun ProtectionState.allowsLegDetail(): Boolean = this == ProtectionState.NORMAL
