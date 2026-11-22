package com.tradinginaja.data.catalog

import kotlinx.serialization.Serializable

@Serializable
data class MarketCatalogDto(
    val schemaVersion: Int,
    val assets: List<AssetSpecDto>,
    val correlationPairs: List<CorrelationPairDto> = emptyList()
)

@Serializable
data class AssetSpecDto(
    val symbol: String,
    val name: String,
    val sector: String,
    val category: String,
    val initialPrice: Double,
    val annualVolatility: Double,
    val annualDrift: Double,
    val liquidityTier: String,
    val baseVolumePerTick: Long,
    val priceModel: String = "GBM",
    val meanReversionParams: MeanReversionParamsDto? = null,
    val jumpParams: JumpDiffusionParamsDto? = null,
    val garchParams: GarchParamsDto? = null,
    val isSafeHaven: Boolean = false
)

@Serializable
data class MeanReversionParamsDto(val reversionSpeedPerYear: Double, val longTermMeanPrice: Double)

@Serializable
data class JumpDiffusionParamsDto(
    val jumpIntensityPerYear: Double,
    val jumpMeanLogSize: Double,
    val jumpStdDevLogSize: Double
)

@Serializable
data class GarchParamsDto(val omegaWeight: Double, val alpha: Double, val beta: Double)

@Serializable
data class CorrelationPairDto(val assetA: String, val assetB: String, val correlation: Double)
