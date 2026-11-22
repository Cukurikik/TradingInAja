package com.tradinginaja.domain.model

import com.tradinginaja.core.common.GameBalance
import kotlin.math.abs

enum class MarketRegime {
    BULL,
    BEAR,
    SIDEWAYS,
    CRISIS
}

/**
 * Matriks transisi rantai Markov 4x4 untuk rezim pasar [BULL, BEAR, SIDEWAYS, CRISIS].
 * Setiap baris harus bernilai non-negatif dan berjumlah 1.0.
 */
data class RegimeTransitionMatrix(val matrix: Array<DoubleArray>) {

    init {
        val expectedSize = MarketRegime.entries.size
        require(matrix.size == expectedSize) {
            "Matriks transisi rezim harus berukuran ${expectedSize}x$expectedSize."
        }
        matrix.forEachIndexed { rowIdx, row ->
            require(row.size == expectedSize) {
                "Baris $rowIdx matriks transisi harus memiliki $expectedSize kolom."
            }
            var sum = 0.0
            for (prob in row) {
                require(prob.isFinite() && prob in 0.0..1.0) {
                    "Probabilitas transisi tidak valid pada baris $rowIdx: $prob"
                }
                sum += prob
            }
            require(abs(sum - 1.0) <= ROW_SUM_TOLERANCE) {
                "Jumlah probabilitas baris $rowIdx harus 1.0 (aktual: $sum)."
            }
        }
    }

    fun rowFor(regime: MarketRegime): DoubleArray = matrix[regime.ordinal]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RegimeTransitionMatrix) return false
        return matrix.contentDeepEquals(other.matrix)
    }

    override fun hashCode(): Int = matrix.contentDeepHashCode()

    companion object {
        private const val ROW_SUM_TOLERANCE = 1e-6

        fun default(): RegimeTransitionMatrix = RegimeTransitionMatrix(
            arrayOf(
                GameBalance.REGIME_ROW_BULL.copyOf(),
                GameBalance.REGIME_ROW_BEAR.copyOf(),
                GameBalance.REGIME_ROW_SIDEWAYS.copyOf(),
                GameBalance.REGIME_ROW_CRISIS.copyOf()
            )
        )
    }
}

enum class EventTargetScope {
    ALL,
    CATEGORY,
    SECTOR,
    SYMBOL
}

data class EventSchedule(
    val minTick: Long = 0L,
    val maxTick: Long? = null,
    val fixedTick: Long? = null,
    val intervalTicks: Long? = null,
    val intervalOffsetTicks: Long = 0L,
    val triggerProbabilityPerTick: Double = 0.0,
    val durationTicks: Int = 1,
    val cooldownTicks: Int = 0,
    val allowedRegimes: Set<MarketRegime> = MarketRegime.entries.toSet()
)

data class EventEffect(
    val annualDriftDelta: Double = 0.0,
    val volatilityMultiplier: Double = 1.0,
    val jumpIntensityMultiplier: Double = 1.0,
    val jumpMeanLogSizeDelta: Double = 0.0
)

/**
 * Definisi event pasar yang dimuat sepenuhnya dari JSON ter-versi.
 * Menambah event baru cukup mengedit JSON tanpa mengubah kode Kotlin.
 */
data class MarketEventDefinition(
    val eventId: String,
    val eventType: String,
    val targetScope: EventTargetScope,
    val targetValue: String? = null,
    val schedule: EventSchedule,
    val effect: EventEffect,
    val headlineId: String,
    val headlineEn: String,
    val summaryId: String,
    val summaryEn: String,
    val bullCaseId: String,
    val bullCaseEn: String,
    val bearCaseId: String,
    val bearCaseEn: String
)

data class EventCatalog(
    val schemaVersion: Int,
    val events: List<MarketEventDefinition>,
    val transitionMatrix: RegimeTransitionMatrix = RegimeTransitionMatrix.default()
) {
    companion object {
        val EMPTY = EventCatalog(
            schemaVersion = 1,
            events = emptyList(),
            transitionMatrix = RegimeTransitionMatrix.default()
        )
    }
}

data class ActiveMarketEvent(val definition: MarketEventDefinition, val startedAtTick: Long, val expiresAtTick: Long)

/**
 * Item berita untuk layar News.
 * Sengaja TIDAK menyimpan besaran drift/volatilitas numerik agar interpretasi tetap menantang bagi pemain.
 */
data class NewsItem(
    val newsId: String,
    val eventId: String,
    val tickIndex: Long,
    val eventType: String,
    val targetScope: EventTargetScope,
    val targetValue: String?,
    val affectedSymbols: List<String>,
    val headlineId: String,
    val headlineEn: String,
    val summaryId: String,
    val summaryEn: String,
    val bullCaseId: String,
    val bullCaseEn: String,
    val bearCaseId: String,
    val bearCaseEn: String,
    val durationTicks: Int
)
