package com.tradinginaja.domain.model

enum class AssetCategory {
    STOCK,
    CRYPTO,
    COMMODITY,
    FOREX,
    INDEX
}

enum class LiquidityTier(val baseSpreadBps: Long, val baseVolumeMultiplier: Double) {
    VERY_HIGH(baseSpreadBps = 4L, baseVolumeMultiplier = 2.0),
    HIGH(baseSpreadBps = 10L, baseVolumeMultiplier = 1.25),
    MEDIUM(baseSpreadBps = 18L, baseVolumeMultiplier = 0.8),
    LOW_MEDIUM(baseSpreadBps = 35L, baseVolumeMultiplier = 0.45)
}

enum class PriceModelType {
    GBM,
    MEAN_REVERSION_OU
}

/**
 * Parameter model Ornstein-Uhlenbeck pada log-harga.
 */
data class MeanReversionParams(val reversionSpeedPerYear: Double, val longTermMeanPrice: Double)

/**
 * Parameter komponen Jump Diffusion (Merton) untuk kejutan harga diskrit.
 */
data class JumpDiffusionParams(
    val jumpIntensityPerYear: Double,
    val jumpMeanLogSize: Double,
    val jumpStdDevLogSize: Double
)

/**
 * Parameter model volatilitas GARCH(1,1) sederhana untuk efek volatility clustering.
 */
data class GarchParams(val omegaWeight: Double, val alpha: Double, val beta: Double)

/**
 * Spesifikasi instrumen fiktif untuk mesin simulasi pasar.
 */
data class AssetSpec(
    val symbol: String,
    val name: String,
    val sector: String,
    val category: AssetCategory,
    val initialPrice: Double,
    val annualVolatility: Double,
    val annualDrift: Double,
    val liquidityTier: LiquidityTier,
    val baseVolumePerTick: Long,
    val priceModel: PriceModelType = PriceModelType.GBM,
    val meanReversionParams: MeanReversionParams? = null,
    val jumpParams: JumpDiffusionParams? = null,
    val garchParams: GarchParams? = null,
    val isSafeHaven: Boolean = false
)

/**
 * Katalog ter-versi yang dimuat dari aset JSON beserta matriks korelasinya.
 */
data class MarketCatalog(
    val schemaVersion: Int,
    val assets: List<AssetSpec>,
    val correlationMatrix: Array<DoubleArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MarketCatalog) return false
        return schemaVersion == other.schemaVersion &&
            assets == other.assets &&
            correlationMatrix.contentDeepEquals(other.correlationMatrix)
    }

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + assets.hashCode()
        result = 31 * result + correlationMatrix.contentDeepHashCode()
        return result
    }
}
