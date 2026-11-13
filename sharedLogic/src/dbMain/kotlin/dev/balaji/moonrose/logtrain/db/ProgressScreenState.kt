package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.insights.BodyPartSlice
import dev.balaji.moonrose.logtrain.insights.InsightCard
import dev.balaji.moonrose.logtrain.insights.InsightMath
import dev.balaji.moonrose.logtrain.model.ProgressionCardConfig
import dev.balaji.moonrose.logtrain.model.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class ProgressUiState(
    val loading: Boolean = true,
    val cards: List<InsightCard> = emptyList(),
    /** Completed sets per primary body part, most trained first; empty with no history. */
    val bodyPartBreakdown: List<BodyPartSlice> = emptyList(),
    /** Unit of the stored weights, from user settings; the UI supplies its localized label. */
    val weightUnit: WeightUnit = WeightUnit.METRIC,
    /** Pre-formatted latest logged body weight; empty when nothing is logged. */
    val latestBodyWeight: String = "",
    val savingWeight: Boolean = false,
    /** Exercises selectable in progression cards, most 12-week volume first. */
    val progressionOptions: List<String> = emptyList(),
    /**
     * Effective configs behind the progression cards, display order — the
     * user's stored configs, or the top-volume defaults when none are stored.
     * Index-aligned with the "progression:slot<index>" cards in [cards].
     */
    val progressionConfigs: List<ProgressionCardConfig> = emptyList(),
)

/**
 * State for the Progress tab: insight cards plus the log-weight entry point.
 * Provider-free by design — platforms with an on-device model construct a
 * separate InsightNarrationState and join it to these cards by id.
 */
class ProgressScreenState {
    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        scope.launch {
            combine(
                InsightsRepository.observeCards(),
                InsightsRepository.observeBodyPartBreakdown(),
                BodyMetricsRepository.entries,
                UserSettingsRepository.settings,
                InsightsRepository.observeProgressionOptions(),
            ) { cards, bodyParts, entries, settings, progressionOptions ->
                ProgressUiState(
                    loading = false,
                    cards = cards,
                    bodyPartBreakdown = bodyParts,
                    weightUnit = settings.weightUnit,
                    latestBodyWeight = entries.firstOrNull()
                        ?.let { InsightMath.formatNumber(it.weightUnit.convertWeight(it.weight, settings.weightUnit)) }
                        .orEmpty(),
                    savingWeight = _uiState.value.savingWeight,
                    progressionOptions = progressionOptions,
                    progressionConfigs = effectiveProgressionConfigs(
                        stored = settings.progressionCards,
                        eligible = progressionOptions,
                    ),
                )
            }.collect { _uiState.value = it }
        }
    }

    /** Replaces the exercises compared on one progression card (1–3 names). */
    fun setProgressionCardExercises(index: Int, exercises: List<String>) =
        updateProgressionConfigs { configs ->
            configs.mapIndexed { i, config ->
                if (i == index) config.copy(exercises = exercises) else config
            }
        }

    /** Flips one card's chart between raw weight and % change. */
    fun toggleProgressionAxis(index: Int) =
        updateProgressionConfigs { configs ->
            configs.mapIndexed { i, config ->
                if (i == index) config.copy(showPctChange = !config.showPctChange) else config
            }
        }

    /** Appends a card charting [exercise]; no-op at the 3-card cap. */
    fun addProgressionCard(exercise: String) =
        updateProgressionConfigs { configs ->
            if (configs.size >= MAX_PROGRESSION_CARDS) configs
            else configs + ProgressionCardConfig(listOf(exercise))
        }

    /**
     * Removes one progression card. The last card cannot be removed: an
     * empty stored config means "never configured" and would resurrect the
     * defaults, so the UI hides the remove action on a lone card.
     */
    fun removeProgressionCard(index: Int) =
        updateProgressionConfigs { configs ->
            if (configs.size <= 1) configs else configs.filterIndexed { i, _ -> i != index }
        }

    /** Persists a transform of the effective configs; cards re-emit reactively. */
    private fun updateProgressionConfigs(
        transform: (List<ProgressionCardConfig>) -> List<ProgressionCardConfig>,
    ) {
        val updated = transform(_uiState.value.progressionConfigs)
        if (updated == _uiState.value.progressionConfigs) return
        scope.launch { UserSettingsRepository.setProgressionCards(updated) }
    }

    /** Parses and logs a weight entry; silently ignores non-positive or unparsable input. */
    fun logWeight(text: String) {
        val weight = text.filterDecimal().toDoubleOrNull() ?: return
        if (weight <= 0.0) return
        _uiState.value = _uiState.value.copy(savingWeight = true)
        scope.launch {
            BodyMetricsRepository.logWeight(weight)
            _uiState.value = _uiState.value.copy(savingWeight = false)
        }
    }
}
