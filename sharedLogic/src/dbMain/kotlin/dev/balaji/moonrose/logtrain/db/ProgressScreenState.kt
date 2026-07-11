package dev.balaji.moonrose.logtrain.db

import dev.balaji.moonrose.logtrain.insights.InsightCard
import dev.balaji.moonrose.logtrain.insights.InsightMath
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
    /** Unit of the stored weights, from user settings; the UI supplies its localized label. */
    val weightUnit: WeightUnit = WeightUnit.METRIC,
    /** Pre-formatted latest logged body weight; empty when nothing is logged. */
    val latestBodyWeight: String = "",
    val savingWeight: Boolean = false,
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
                BodyMetricsRepository.entries,
                UserSettingsRepository.settings,
            ) { cards, entries, settings ->
                ProgressUiState(
                    loading = false,
                    cards = cards,
                    weightUnit = settings.weightUnit,
                    latestBodyWeight = entries.firstOrNull()
                        ?.let { InsightMath.formatNumber(it.weight) }
                        .orEmpty(),
                    savingWeight = _uiState.value.savingWeight,
                )
            }.collect { _uiState.value = it }
        }
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
