package com.tradinginaja.core.common

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Aturan pembulatan deterministik untuk kalkulasi moneter.
 */
enum class MoneyRoundingMode {
    /** Membulatkan ke angka terdekat; tepat .5 menjauh dari nol (standar ritel). */
    HALF_UP,

    /** Banker's rounding: tepat .5 ke angka genap terdekat (minim bias kumulatif). */
    HALF_EVEN,

    /** Selalu membulatkan mendekati nol (truncate). */
    DOWN,

    /** Selalu membulatkan menjauh dari nol. */
    UP
    ;

    internal fun toJavaRoundingMode(): RoundingMode = when (this) {
        HALF_UP -> RoundingMode.HALF_UP
        HALF_EVEN -> RoundingMode.HALF_EVEN
        DOWN -> RoundingMode.DOWN
        UP -> RoundingMode.UP
    }
}

/**
 * Value class moneter berbasis bilangan bulat [Long] dalam satuan terkecil (sen).
 * Tidak memakai Double agar bebas galat floating-point pada saldo, harga, dan fee.
 *
 * Skala tetap: 1 unit utama = [GameBalance.MINOR_UNITS_PER_MAJOR] (100) satuan terkecil.
 */
@JvmInline
value class Money(val minorUnits: Long) : Comparable<Money> {

    val isZero: Boolean get() = minorUnits == 0L
    val isPositive: Boolean get() = minorUnits > 0L
    val isNegative: Boolean get() = minorUnits < 0L

    operator fun plus(other: Money): Money = Money(Math.addExact(minorUnits, other.minorUnits))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(minorUnits, other.minorUnits))

    operator fun unaryMinus(): Money = Money(Math.negateExact(minorUnits))

    operator fun times(multiplier: Long): Money = Money(Math.multiplyExact(minorUnits, multiplier))

    fun abs(): Money = Money(if (minorUnits < 0L) Math.negateExact(minorUnits) else minorUnits)

    override fun compareTo(other: Money): Int = minorUnits.compareTo(other.minorUnits)

    /**
     * Mengalikan nominal dengan rasio basis points ([bps] / 10_000).
     * Contoh: 15 bps = 0,15%.
     */
    fun multiplyByBps(bps: Long, roundingMode: MoneyRoundingMode = MoneyRoundingMode.HALF_EVEN): Money {
        val exact = BigDecimal.valueOf(minorUnits)
            .multiply(BigDecimal.valueOf(bps))
            .divide(
                BigDecimal.valueOf(GameBalance.BASIS_POINTS_DIVISOR),
                0,
                roundingMode.toJavaRoundingMode()
            )
        return Money(exact.longValueExact())
    }

    /**
     * Mengalikan nominal dengan faktor desimal berbentuk string (mis. "1.025")
     * dengan aturan pembulatan yang eksplisit.
     */
    fun multiplyByDecimal(decimalFactor: String, roundingMode: MoneyRoundingMode = MoneyRoundingMode.HALF_EVEN): Money {
        val factor = BigDecimal(decimalFactor.trim())
        val result = BigDecimal.valueOf(minorUnits)
            .multiply(factor)
            .setScale(0, roundingMode.toJavaRoundingMode())
        return Money(result.longValueExact())
    }

    /**
     * Membagi nominal dengan pembagi bilangan bulat [divisor] dengan aturan pembulatan eksplisit.
     */
    fun divideBy(divisor: Long, roundingMode: MoneyRoundingMode = MoneyRoundingMode.HALF_EVEN): Money {
        require(divisor != 0L) { "Pembagi moneter tidak boleh nol." }
        val result = BigDecimal.valueOf(minorUnits)
            .divide(BigDecimal.valueOf(divisor), 0, roundingMode.toJavaRoundingMode())
        return Money(result.longValueExact())
    }

    /**
     * Membagi nominal secara proporsional sesuai [weights] tanpa menghilangkan sisa 1 sen.
     * Sisa pembulatan didistribusikan satu per satu ke elemen awal (largest-remainder/sequential).
     */
    fun allocate(weights: List<Long>): List<Money> {
        require(weights.isNotEmpty()) { "Daftar bobot alokasi tidak boleh kosong." }
        require(weights.all { it >= 0L }) { "Bobot alokasi tidak boleh negatif." }
        val totalWeight = weights.sum()
        require(totalWeight > 0L) { "Total bobot alokasi harus lebih besar dari nol." }

        val bigTotal = BigDecimal.valueOf(minorUnits)
        val bigWeightSum = BigDecimal.valueOf(totalWeight)
        val results = LongArray(weights.size)
        var allocated = 0L

        for (index in weights.indices) {
            val share = bigTotal
                .multiply(BigDecimal.valueOf(weights[index]))
                .divideToIntegralValue(bigWeightSum)
                .longValueExact()
            results[index] = share
            allocated = Math.addExact(allocated, share)
        }

        var remainder = Math.subtractExact(minorUnits, allocated)
        val step = if (remainder >= 0L) 1L else -1L
        var cursor = 0
        while (remainder != 0L) {
            if (weights[cursor] > 0L) {
                results[cursor] = Math.addExact(results[cursor], step)
                remainder -= step
            }
            cursor = (cursor + 1) % weights.size
        }

        return results.map { Money(it) }
    }

    /**
     * Format deterministik bergaya Indonesia (pemisah ribuan titik, desimal koma)
     * tanpa ketergantungan Locale perangkat.
     */
    fun formatDisplay(
        currencySymbol: String = GameBalance.VIRTUAL_CURRENCY_SYMBOL,
        includeFraction: Boolean = true
    ): String {
        val sign = if (minorUnits < 0L) "-" else ""
        val absUnits = if (minorUnits == Long.MIN_VALUE) {
            error("Nilai Long.MIN_VALUE tidak didukung untuk format tampilan.")
        } else {
            abs(minorUnits)
        }
        val major = absUnits / GameBalance.MINOR_UNITS_PER_MAJOR
        val minor = absUnits % GameBalance.MINOR_UNITS_PER_MAJOR
        val majorText = formatThousands(major)
        return if (includeFraction) {
            val minorText = minor.toString().padStart(GameBalance.CURRENCY_SCALE_DIGITS, '0')
            "$sign$currencySymbol $majorText,$minorText"
        } else {
            "$sign$currencySymbol $majorText"
        }
    }

    companion object {
        val ZERO: Money = Money(0L)

        fun fromMajor(major: Long, minor: Int = 0): Money {
            require(minor in 0 until GameBalance.MINOR_UNITS_PER_MAJOR.toInt()) {
                "Nilai sen harus berada di rentang 0..99."
            }
            val scaledMajor = Math.multiplyExact(major, GameBalance.MINOR_UNITS_PER_MAJOR)
            val signedMinor = if (major < 0L) -minor.toLong() else minor.toLong()
            return Money(Math.addExact(scaledMajor, signedMinor))
        }

        fun fromDecimalString(value: String, roundingMode: MoneyRoundingMode = MoneyRoundingMode.HALF_EVEN): Money {
            val normalized = value.trim()
            require(normalized.isNotEmpty()) { "String nominal tidak boleh kosong." }
            val scaled = BigDecimal(normalized)
                .movePointRight(GameBalance.CURRENCY_SCALE_DIGITS)
                .setScale(0, roundingMode.toJavaRoundingMode())
            return Money(scaled.longValueExact())
        }

        private fun formatThousands(value: Long): String {
            val raw = value.toString()
            if (raw.length <= THOUSANDS_GROUP_SIZE) return raw
            val builder = StringBuilder(raw.length + raw.length / THOUSANDS_GROUP_SIZE)
            var count = 0
            for (index in raw.length - 1 downTo 0) {
                builder.append(raw[index])
                count++
                if (count == THOUSANDS_GROUP_SIZE && index > 0) {
                    builder.append('.')
                    count = 0
                }
            }
            return builder.reverse().toString()
        }

        private const val THOUSANDS_GROUP_SIZE = 3
    }
}
