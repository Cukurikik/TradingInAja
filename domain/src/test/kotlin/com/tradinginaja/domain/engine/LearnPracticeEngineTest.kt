package com.tradinginaja.domain.engine

import com.tradinginaja.core.common.Money
import com.tradinginaja.domain.model.CandlePatternType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnPracticeEngineTest {

    @Test
    fun `generateCandleDrill is deterministic and builds structurally valid pattern candles`() {
        val firstRun = LearnPracticeEngine.generateCandleDrill(drillIndex = 1, seed = 42L)
        val secondRun = LearnPracticeEngine.generateCandleDrill(drillIndex = 1, seed = 42L)

        assertEquals(firstRun, secondRun)
        assertEquals(CandlePatternType.BEARISH_REJECTION_WICK, firstRun.correctPattern)

        val signal = firstRun.candles.last()
        val upperWick = signal.high - maxOf(signal.open, signal.close)
        val body = kotlin.math.abs(signal.close - signal.open)
        assertTrue(upperWick > body)
        assertTrue(firstRun.candles.all { it.low > 0.0 && it.high >= it.low })
    }

    @Test
    fun `simulatePositionSizing calculates exact units within 1-2 percent budget`() {
        val withinRule = LearnPracticeEngine.simulatePositionSizing(
            accountEquity = Money(100_000_000_00L),
            targetRiskPct = 1.5,
            entryPrice = Money(5_000_00L),
            stopLossPrice = Money(4_500_00L)
        )

        assertEquals(Money(1_500_000_00L), withinRule.maxRiskBudget)
        assertEquals(Money(500_00L), withinRule.riskPerUnit)
        assertEquals(3_000L, withinRule.recommendedUnits)
        assertEquals(1.5, withinRule.actualRiskPct, 0.01)
        assertTrue(withinRule.isWithinRule)

        val overRisk = LearnPracticeEngine.simulatePositionSizing(
            accountEquity = Money(100_000_000_00L),
            targetRiskPct = 4.0,
            entryPrice = Money(5_000_00L),
            stopLossPrice = Money(4_500_00L)
        )
        assertFalse(overRisk.isWithinRule)
    }
}
