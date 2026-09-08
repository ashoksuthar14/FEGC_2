package eu.feg.ambient.data.repo

import android.content.SharedPreferences
import eu.feg.ambient.data.model.Consents
import eu.feg.ambient.data.model.Limits
import eu.feg.ambient.data.model.QuietHours
import eu.feg.ambient.data.model.RiskState
import eu.feg.ambient.data.model.UserState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json

/**
 * Phase 2 seam 3 (PRD section 11): the safety gate reads [state] and nothing else, so this
 * is the single writer. Persisted through SharedPreferences — Room arrives in Phase 2 with
 * the ledger.
 */
class UserStateRepository(private val prefs: SharedPreferences?) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserState> = _state.asStateFlow()

    fun setRiskState(risk: RiskState) = update { it.copy(riskState = risk) }

    fun setConsents(consents: Consents) = update { it.copy(consents = consents) }

    fun setLimits(limits: Limits) = update { it.copy(limits = limits) }

    fun setRealityCheck(minutes: Int) = update { it.copy(realityCheckMinutes = minutes) }

    fun setQuietHours(quietHours: QuietHours?) = update { it.copy(quietHours = quietHours) }

    fun panicFor(until: Instant) = update { it.copy(panicUntil = until) }

    fun clearPanic() = update { it.copy(panicUntil = null) }

    private fun update(block: (UserState) -> UserState) {
        val next = block(_state.value)
        _state.value = next
        prefs?.edit()?.putString(KEY, json.encodeToString(next))?.apply()
    }

    private fun load(): UserState {
        val raw = prefs?.getString(KEY, null) ?: return UserState()
        return runCatching { json.decodeFromString<UserState>(raw) }.getOrElse { UserState() }
    }

    private companion object {
        const val KEY = "user_state"
    }
}
