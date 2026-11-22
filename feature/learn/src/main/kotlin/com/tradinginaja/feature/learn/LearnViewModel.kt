package com.tradinginaja.feature.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradinginaja.core.common.GameBalance
import com.tradinginaja.core.common.Money
import com.tradinginaja.core.common.MoneyRoundingMode
import com.tradinginaja.domain.engine.LearnPracticeEngine
import com.tradinginaja.domain.engine.MultiAssetMarketState
import com.tradinginaja.domain.engine.OrderEngine
import com.tradinginaja.domain.engine.OrderFormulas
import com.tradinginaja.domain.engine.PostMortemAnalyzer
import com.tradinginaja.domain.engine.ProgressionEngine
import com.tradinginaja.domain.engine.RiskEngine
import com.tradinginaja.domain.model.CandleDrillQuestion
import com.tradinginaja.domain.model.CandlePatternType
import com.tradinginaja.domain.model.CareerProgressSnapshot
import com.tradinginaja.domain.model.LearnLesson
import com.tradinginaja.domain.model.LearningProgress
import com.tradinginaja.domain.model.PortfolioState
import com.tradinginaja.domain.model.PostMortemSummary
import com.tradinginaja.domain.model.RiskSizingSimulationResult
import com.tradinginaja.domain.model.TradingTerm
import com.tradinginaja.domain.repository.MarketSimulationRepository
import com.tradinginaja.domain.repository.PortfolioRepository
import com.tradinginaja.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LearnSectionTab {
    MISSIONS,
    RISK_FREE_LAB,
    LESSONS_GLOSSARY
}

private data class LearnPracticeDraft(
    val selectedTab: LearnSectionTab = LearnSectionTab.MISSIONS,
    val drillIndex: Int = 0,
    val selectedPatternAnswer: CandlePatternType? = null,
    val isDrillCorrect: Boolean? = null,
    val sizingRiskPct: Double = GameBalance.LEARN_SANDBOX_DEFAULT_RISK_PCT,
    val sizingEntryInput: String = "4500",
    val sizingStopLossInput: String = "4320",
    val activeGlossaryTerm: TradingTerm? = null
)

sealed interface LearnUiState {
    data object Loading : LearnUiState

    data class Ready(
        val careerProgress: CareerProgressSnapshot,
        val learningProgress: LearningProgress,
        val postMortemSummary: PostMortemSummary,
        val lessons: List<LearnLesson>,
        val selectedTab: LearnSectionTab,
        val candleDrill: CandleDrillQuestion,
        val selectedPatternAnswer: CandlePatternType?,
        val isDrillCorrect: Boolean?,
        val sizingRiskPct: Double,
        val sizingEntryInput: String,
        val sizingStopLossInput: String,
        val sizingResult: RiskSizingSimulationResult?,
        val activeGlossaryTerm: TradingTerm?
    ) : LearnUiState
}

sealed interface LearnUiEvent {
    data class SelectTab(val tab: LearnSectionTab) : LearnUiEvent
    data class MarkLessonCompleted(val lessonId: String) : LearnUiEvent
    data class AnswerCandleDrill(val pattern: CandlePatternType) : LearnUiEvent
    data object NextCandleDrill : LearnUiEvent
    data class UpdateRiskPct(val riskPct: Double) : LearnUiEvent
    data class UpdateEntryPrice(val raw: String) : LearnUiEvent
    data class UpdateStopLossPrice(val raw: String) : LearnUiEvent
    data class OpenGlossaryTerm(val term: TradingTerm) : LearnUiEvent
    data object DismissGlossaryTerm : LearnUiEvent
}

@HiltViewModel
class LearnViewModel @Inject constructor(
    private val simulationRepository: MarketSimulationRepository,
    private val portfolioRepository: PortfolioRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val draft = MutableStateFlow(LearnPracticeDraft())
    private val lessons = LearnPracticeEngine.defaultLessons()

    val uiState: StateFlow<LearnUiState> = combine(
        simulationRepository.marketState,
        portfolioRepository.portfolioState,
        preferencesRepository.learningProgress,
        draft
    ) { marketState, portfolioState, learningProgress, currentDraft ->
        if (marketState == null || portfolioState == null) {
            LearnUiState.Loading
        } else {
            buildReadyState(marketState, portfolioState, learningProgress, currentDraft)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LearnUiState.Loading
    )

    init {
        viewModelScope.launch {
            simulationRepository.ensureInitialized()
            portfolioRepository.ensureInitialized()
        }
    }

    fun onEvent(event: LearnUiEvent) {
        when (event) {
            is LearnUiEvent.SelectTab -> {
                draft.value = draft.value.copy(selectedTab = event.tab)
            }
            is LearnUiEvent.MarkLessonCompleted -> {
                viewModelScope.launch {
                    preferencesRepository.markLessonCompleted(event.lessonId)
                }
            }
            is LearnUiEvent.AnswerCandleDrill -> handleDrillAnswer(event.pattern)
            LearnUiEvent.NextCandleDrill -> {
                draft.value = draft.value.copy(
                    drillIndex = draft.value.drillIndex + 1,
                    selectedPatternAnswer = null,
                    isDrillCorrect = null
                )
            }
            is LearnUiEvent.UpdateRiskPct -> {
                draft.value = draft.value.copy(
                    sizingRiskPct = event.riskPct.coerceIn(
                        GameBalance.LEARN_SANDBOX_MIN_RISK_PCT,
                        GameBalance.LEARN_SANDBOX_MAX_RISK_PCT
                    )
                )
            }
            is LearnUiEvent.UpdateEntryPrice -> {
                draft.value = draft.value.copy(sizingEntryInput = sanitizeDecimalInput(event.raw))
            }
            is LearnUiEvent.UpdateStopLossPrice -> {
                draft.value = draft.value.copy(sizingStopLossInput = sanitizeDecimalInput(event.raw))
            }
            is LearnUiEvent.OpenGlossaryTerm -> {
                draft.value = draft.value.copy(activeGlossaryTerm = event.term)
            }
            LearnUiEvent.DismissGlossaryTerm -> {
                draft.value = draft.value.copy(activeGlossaryTerm = null)
            }
        }
    }

    private fun handleDrillAnswer(pattern: CandlePatternType) {
        val current = draft.value
        if (current.isDrillCorrect == true) return
        val question = LearnPracticeEngine.generateCandleDrill(current.drillIndex)
        val isCorrect = pattern == question.correctPattern
        draft.value = current.copy(
            selectedPatternAnswer = pattern,
            isDrillCorrect = isCorrect
        )
        if (isCorrect) {
            viewModelScope.launch {
                preferencesRepository.incrementCandleDrillsPassed()
            }
        }
    }

    private fun buildReadyState(
        marketState: MultiAssetMarketState,
        portfolio: PortfolioState,
        learningProgress: LearningProgress,
        currentDraft: LearnPracticeDraft
    ): LearnUiState.Ready {
        val quotesBySymbol = marketState.assetStates.associate {
            it.spec.symbol to OrderFormulas.quoteFromAssetState(it)
        }
        val categoriesBySymbol = marketState.assetStates.associate {
            it.spec.symbol to it.spec.category
        }
        val metrics = RiskEngine.computePortfolioMetrics(portfolio, quotesBySymbol)
        val careerProgress = ProgressionEngine.evaluateCareerProgress(
            portfolio = portfolio,
            categoriesBySymbol = categoriesBySymbol,
            learningProgress = learningProgress,
            metrics = metrics
        )
        val postMortems = PostMortemAnalyzer.analyzePortfolio(portfolio)
        val postMortemSummary = PostMortemAnalyzer.buildSummary(postMortems)

        val currentEquity = (portfolio.cashBalance + OrderEngine.computePositionsNetEquity(portfolio, quotesBySymbol))
            .coerceAtLeast(Money(GameBalance.INITIAL_VIRTUAL_CASH_MINOR))
        val entryMoney = parseMoneyOrNull(currentDraft.sizingEntryInput)
        val stopMoney = parseMoneyOrNull(currentDraft.sizingStopLossInput)
        val sizingResult = if (entryMoney != null && stopMoney != null && entryMoney != stopMoney) {
            LearnPracticeEngine.simulatePositionSizing(
                accountEquity = currentEquity,
                targetRiskPct = currentDraft.sizingRiskPct,
                entryPrice = entryMoney,
                stopLossPrice = stopMoney
            )
        } else {
            null
        }

        return LearnUiState.Ready(
            careerProgress = careerProgress,
            learningProgress = learningProgress,
            postMortemSummary = postMortemSummary,
            lessons = lessons,
            selectedTab = currentDraft.selectedTab,
            candleDrill = LearnPracticeEngine.generateCandleDrill(currentDraft.drillIndex),
            selectedPatternAnswer = currentDraft.selectedPatternAnswer,
            isDrillCorrect = currentDraft.isDrillCorrect,
            sizingRiskPct = currentDraft.sizingRiskPct,
            sizingEntryInput = currentDraft.sizingEntryInput,
            sizingStopLossInput = currentDraft.sizingStopLossInput,
            sizingResult = sizingResult,
            activeGlossaryTerm = currentDraft.activeGlossaryTerm
        )
    }

    private fun parseMoneyOrNull(raw: String): Money? {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        return runCatching {
            Money.fromDecimalString(normalized, MoneyRoundingMode.HALF_EVEN)
        }.getOrNull()?.takeIf { it.isPositive }
    }

    private fun sanitizeDecimalInput(raw: String): String {
        val normalized = raw.replace(',', '.')
        val builder = StringBuilder()
        var hasDot = false
        for (ch in normalized) {
            if (ch.isDigit()) {
                builder.append(ch)
            } else if (ch == '.' && !hasDot && builder.isNotEmpty()) {
                hasDot = true
                builder.append(ch)
            }
        }
        return builder.toString().take(MAX_PRICE_CHARS)
    }

    private companion object {
        const val MAX_PRICE_CHARS = 12
    }
}
