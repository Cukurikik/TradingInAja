package com.tradinginaja.data.catalog

import com.tradinginaja.domain.engine.CholeskyDecomposition
import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.GarchParams
import com.tradinginaja.domain.model.JumpDiffusionParams
import com.tradinginaja.domain.model.LiquidityTier
import com.tradinginaja.domain.model.MarketCatalog
import com.tradinginaja.domain.model.MeanReversionParams
import com.tradinginaja.domain.model.PriceModelType
import kotlinx.serialization.json.Json

object AssetCatalogParser {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(rawJson: String): MarketCatalog {
        val dto = jsonParser.decodeFromString<MarketCatalogDto>(rawJson)
        require(dto.schemaVersion >= 1) { "Versi skema katalog tidak valid: ${dto.schemaVersion}" }
        require(dto.assets.isNotEmpty()) { "Daftar aset katalog tidak boleh kosong." }

        val assets = dto.assets.map { it.toDomain() }
        val indexBySymbol = HashMap<String, Int>(assets.size)
        assets.forEachIndexed { index, spec ->
            val duplicate = indexBySymbol.put(spec.symbol, index)
            require(duplicate == null) { "Simbol aset duplikat di katalog: ${spec.symbol}" }
        }

        val matrix = CholeskyDecomposition.identityMatrix(assets.size)
        for (pair in dto.correlationPairs) {
            val idxA = requireNotNull(indexBySymbol[pair.assetA]) {
                "Simbol korelasi tidak dikenal: ${pair.assetA}"
            }
            val idxB = requireNotNull(indexBySymbol[pair.assetB]) {
                "Simbol korelasi tidak dikenal: ${pair.assetB}"
            }
            if (idxA != idxB) {
                matrix[idxA][idxB] = pair.correlation
                matrix[idxB][idxA] = pair.correlation
            }
        }

        // Validasi positif-definit lebih awal saat memuat katalog.
        CholeskyDecomposition.decompose(matrix)

        return MarketCatalog(
            schemaVersion = dto.schemaVersion,
            assets = assets,
            correlationMatrix = matrix
        )
    }

    private fun AssetSpecDto.toDomain(): AssetSpec = AssetSpec(
        symbol = symbol,
        name = name,
        sector = sector,
        category = AssetCategory.valueOf(category),
        initialPrice = initialPrice,
        annualVolatility = annualVolatility,
        annualDrift = annualDrift,
        liquidityTier = LiquidityTier.valueOf(liquidityTier),
        baseVolumePerTick = baseVolumePerTick,
        priceModel = PriceModelType.valueOf(priceModel),
        meanReversionParams = meanReversionParams?.let {
            MeanReversionParams(
                reversionSpeedPerYear = it.reversionSpeedPerYear,
                longTermMeanPrice = it.longTermMeanPrice
            )
        },
        jumpParams = jumpParams?.let {
            JumpDiffusionParams(
                jumpIntensityPerYear = it.jumpIntensityPerYear,
                jumpMeanLogSize = it.jumpMeanLogSize,
                jumpStdDevLogSize = it.jumpStdDevLogSize
            )
        },
        garchParams = garchParams?.let {
            GarchParams(
                omegaWeight = it.omegaWeight,
                alpha = it.alpha,
                beta = it.beta
            )
        },
        isSafeHaven = isSafeHaven
    )
}
