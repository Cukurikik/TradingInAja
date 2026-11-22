package com.tradinginaja.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {

    @Test
    fun `fromMajor and fromDecimalString produce exact minor units`() {
        assertEquals(Money(150_75L), Money.fromMajor(major = 150L, minor = 75))
        assertEquals(Money(-150_75L), Money.fromMajor(major = -150L, minor = 75))
        assertEquals(Money(123_456L), Money.fromDecimalString("1234.56"))
        assertEquals(Money(123_457L), Money.fromDecimalString("1234.565", MoneyRoundingMode.HALF_UP))
        assertEquals(Money(123_456L), Money.fromDecimalString("1234.565", MoneyRoundingMode.HALF_EVEN))
    }

    @Test
    fun `addition subtraction and negation are exact`() {
        val a = Money(10_000L)
        val b = Money(2_550L)

        assertEquals(Money(12_550L), a + b)
        assertEquals(Money(7_450L), a - b)
        assertEquals(Money(-10_000L), -a)
        assertEquals(Money(10_000L), (-a).abs())
    }

    @Test
    fun `multiplyByBps handles HALF_EVEN banker rounding ties accurately`() {
        // 1.000 sen * 5 bps (0.05%) = 0.5 sen -> 0 (genap) pada HALF_EVEN, 1 pada HALF_UP
        val evenTie = Money(1_000L)
        assertEquals(Money(0L), evenTie.multiplyByBps(5L, MoneyRoundingMode.HALF_EVEN))
        assertEquals(Money(1L), evenTie.multiplyByBps(5L, MoneyRoundingMode.HALF_UP))
        assertEquals(Money(0L), evenTie.multiplyByBps(5L, MoneyRoundingMode.DOWN))
        assertEquals(Money(1L), evenTie.multiplyByBps(5L, MoneyRoundingMode.UP))

        // 3.000 sen * 5 bps = 1.5 sen -> 2 (genap) pada HALF_EVEN, 2 pada HALF_UP
        val oddTie = Money(3_000L)
        assertEquals(Money(2L), oddTie.multiplyByBps(5L, MoneyRoundingMode.HALF_EVEN))
        assertEquals(Money(2L), oddTie.multiplyByBps(5L, MoneyRoundingMode.HALF_UP))
    }

    @Test
    fun `multiplyByBps handles negative values consistently across rounding modes`() {
        val negativeTie = Money(-1_000L)
        assertEquals(Money(0L), negativeTie.multiplyByBps(5L, MoneyRoundingMode.HALF_EVEN))
        assertEquals(Money(-1L), negativeTie.multiplyByBps(5L, MoneyRoundingMode.HALF_UP))
        assertEquals(Money(0L), negativeTie.multiplyByBps(5L, MoneyRoundingMode.DOWN))
        assertEquals(Money(-1L), negativeTie.multiplyByBps(5L, MoneyRoundingMode.UP))
    }

    @Test
    fun `divideBy respects explicit rounding modes`() {
        val amount = Money(5L)
        assertEquals(Money(3L), amount.divideBy(2L, MoneyRoundingMode.HALF_UP))
        assertEquals(Money(2L), amount.divideBy(2L, MoneyRoundingMode.HALF_EVEN))
        assertEquals(Money(2L), amount.divideBy(2L, MoneyRoundingMode.DOWN))
        assertEquals(Money(3L), amount.divideBy(2L, MoneyRoundingMode.UP))
    }

    @Test
    fun `divideBy zero throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money(100L).divideBy(0L)
        }
    }

    @Test
    fun `allocate preserves exact sum without losing remainder minor units`() {
        val total = Money(100L)
        val parts = total.allocate(listOf(1L, 1L, 1L))

        assertEquals(listOf(Money(34L), Money(33L), Money(33L)), parts)
        assertEquals(total, Money(parts.sumOf { it.minorUnits }))
    }

    @Test
    fun `formatDisplay uses Indonesian grouping without locale drift`() {
        assertEquals("vRp 1.250.000,50", Money(125_000_050L).formatDisplay())
        assertEquals("-vRp 5.000,05", Money(-500_005L).formatDisplay())
        assertEquals(
            "vRp 100.000.000",
            Money(GameBalance.INITIAL_VIRTUAL_CASH_MINOR).formatDisplay(includeFraction = false)
        )
    }
}
