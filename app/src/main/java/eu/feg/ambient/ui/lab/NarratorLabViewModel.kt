package eu.feg.ambient.ui.lab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.feg.ambient.ambient.narrator.MomentFacts
import eu.feg.ambient.ambient.narrator.MomentType
import eu.feg.ambient.ambient.narrator.NanoPrompt
import eu.feg.ambient.ambient.narrator.NarratedText
import eu.feg.ambient.ambient.narrator.NarratorGuard
import eu.feg.ambient.ambient.narrator.NarratorLanguage
import eu.feg.ambient.ambient.narrator.Tone
import eu.feg.ambient.core.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SweepRow(val label: String, val passed: Boolean, val reason: String?, val latencyMs: Long)

data class LabUiState(
    val type: MomentType = MomentType.GOAL_ON_SLIP,
    val tone: Tone = Tone.PLAIN,
    val language: NarratorLanguage = NarratorLanguage.EN,
    val facts: MomentFacts = sampleFacts(MomentType.GOAL_ON_SLIP),
    val templateResult: NarratedText? = null,
    val nanoResult: NarratedText? = null,
    val nanoNotice: String? = null,
    val running: Boolean = false,
    val sweep: List<SweepRow> = emptyList(),
    val sweepMedianMs: Long? = null,
) {
    /** Exactly the JSON the model is handed — the demo's proof that no price reaches it. */
    val factsJson: String get() = NanoPrompt.factsJson(facts)
}

class NarratorLabViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(LabUiState())
    val state: StateFlow<LabUiState> = _state.asStateFlow()

    fun setType(type: MomentType) {
        _state.value = _state.value.copy(type = type)
    }

    fun setTone(tone: Tone) {
        _state.value = _state.value.copy(tone = tone)
    }

    fun setLanguage(language: NarratorLanguage) {
        _state.value = _state.value.copy(language = language)
    }

    fun loadSample() {
        _state.value = _state.value.copy(facts = sampleFacts(_state.value.type))
    }

    fun generate() {
        val current = _state.value
        viewModelScope.launch {
            _state.value = current.copy(running = true)
            val template = container.templateOnly.narrate(current.facts, current.tone, current.language)
            val nano = container.nanoOrNull?.narrate(current.facts, current.tone, current.language)
            _state.value = _state.value.copy(
                running = false,
                templateResult = template,
                nanoResult = nano,
                nanoNotice = if (nano == null) "Nano unavailable on this device" else null,
            )
        }
    }

    /** Every MomentType x Tone x Language through the live ladder, checked against the guard. */
    fun runAll64() {
        viewModelScope.launch {
            _state.value = _state.value.copy(running = true, sweep = emptyList())
            val rows = mutableListOf<SweepRow>()
            val latencies = mutableListOf<Long>()

            MomentType.entries.forEach { type ->
                Tone.entries.forEach { tone ->
                    NarratorLanguage.entries.forEach { language ->
                        val facts = sampleFacts(type)
                        val started = System.nanoTime()
                        val result = container.narrator.narrate(facts, tone, language)
                        val elapsed = (System.nanoTime() - started) / 1_000_000
                        latencies += elapsed
                        val reason = NarratorGuard.violation(result)
                        rows += SweepRow(
                            label = type.name + " · " + tone.name + " · " + language.name,
                            passed = reason == null,
                            reason = reason,
                            latencyMs = elapsed,
                        )
                    }
                }
            }

            _state.value = _state.value.copy(
                running = false,
                sweep = rows,
                sweepMedianMs = latencies.sorted().let {
                    if (it.isEmpty()) null else it[it.size / 2]
                },
            )
        }
    }
}

/** Realistic facts per moment, using clubs already present in the mock data. */
fun sampleFacts(type: MomentType): MomentFacts = when (type) {
    MomentType.GOAL_ON_SLIP -> MomentFacts(
        type = type, homeTeam = "Liverpool", awayTeam = "Ipswich",
        homeScore = 1, awayScore = 0, minute = 61, period = "2. poluvrijeme",
        scorer = "Salah", legsTotal = 3, legsWon = 2, legsLost = 0,
        myLegDescription = "Liverpool win", minutesRemaining = 29,
    )
    MomentType.LEG_DECIDED -> MomentFacts(
        type = type, homeTeam = "Betis", awayTeam = "Real Madrid",
        homeScore = 2, awayScore = 1, minute = 88,
        legsTotal = 3, legsWon = 2, legsLost = 0, myLegDescription = "Betis win",
        minutesRemaining = 2,
    )
    MomentType.LEG_LOST -> MomentFacts(
        type = type, homeTeam = "Varaždin", awayTeam = "Istria 1961",
        homeScore = 0, awayScore = 2, minute = 79,
        legsTotal = 3, legsWon = 1, legsLost = 1, myLegDescription = "Varaždin win",
        minutesRemaining = 11,
    )
    MomentType.SLIP_SETTLED -> MomentFacts(
        type = type, legsTotal = 3, legsWon = 2, legsLost = 1,
        myLegDescription = "Liverpool win",
    )
    MomentType.KICKOFF_FOLLOWED -> MomentFacts(
        type = type, homeTeam = "Sparta", awayTeam = "Plzeň",
        followedTeam = "Sparta", kickoffInMinutes = 40,
        habitHints = listOf("Followed for six weeks."),
    )
    MomentType.HALFTIME -> MomentFacts(
        type = type, homeTeam = "Stuttgart", awayTeam = "1.FC Cologne",
        homeScore = 1, awayScore = 1, minute = 45, period = "1. poluvrijeme",
        legsTotal = 3, legsWon = 1, legsLost = 0,
    )
    MomentType.MINUTES_REMAINING -> MomentFacts(
        type = type, homeTeam = "Genoa", awayTeam = "Como",
        homeScore = 2, awayScore = 2, minute = 87, minutesRemaining = 3,
        legsTotal = 3, legsWon = 2, legsLost = 0, myLegDescription = "Genoa win",
    )
    MomentType.AWAY_DIGEST -> MomentFacts(
        type = type, legsTotal = 3, legsWon = 2, legsLost = 0,
        digestItems = listOf("2 legs won", "Betis drew", "Sparta kick off in 40 min"),
    )
}
