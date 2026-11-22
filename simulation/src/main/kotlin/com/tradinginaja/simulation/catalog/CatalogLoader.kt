package com.tradinginaja.simulation.catalog

import com.tradinginaja.domain.engine.CholeskyDecomposition
import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.GarchParams
import com.tradinginaja.domain.model.JumpDiffusionParams
import com.tradinginaja.domain.model.LiquidityTier
import com.tradinginaja.domain.model.MarketCatalog
import com.tradinginaja.domain.model.MeanReversionParams
import com.tradinginaja.domain.model.PriceModelType
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Pemuat katalog aset untuk skrip simulasi headless.
 *
 * Memakai `Json.parseToJsonElement` (runtime kotlinx.serialization) agar modul QA ini tidak perlu
 * plugin compiler serialization, sementara tetap membaca berkas JSON produksi yang sama dengan
 * `data/src/main/assets/market_catalog_v1.json`.
 */
object CatalogLoader {

    private val json = Json { ignoreUnknownKeys = true }
    private const val IDENTITY_DIAGONAL = 1.0

    fun loadMarketCatalog(file: File): MarketCatalog {
        require(file.isFile) { "Berkas katalog tidak ditemukan: ${file.absolutePath}" }
        val root = json.parseToJsonElement(file.readText()).jsonObject
        val assets = root.array("assets").map { it.jsonObject.toAssetSpec() }
        require(assets.isNotEmpty()) { "Katalog simulasi tidak boleh kosong." }

        val indexBySymbol = HashMap<String, Int>(assets.size)
        assets.forEachIndexed { index, spec ->
            val previous = indexBySymbol.put(spec.symbol, index)
            require(previous == null) { "Simbol aset duplikat: ${spec.symbol}" }
        }

        val matrix = Array(assets.size) { row ->
            DoubleArray(assets.size) { col -> if (row == col) IDENTITY_DIAGONAL else 0.0 }
        }
        for (pair in root.array("correlationPairs")) {
            val pairObject = pair.jsonObject
            val idxA = indexBySymbol.getValue(pairObject.str("assetA"))
            val idxB = indexBySymbol.getValue(pairObject.str("assetB"))
            val correlation = pairObject.double("correlation")
            if (idxA != idxB) {
                matrix[idxA][idxB] = correlation
                matrix[idxB][idxA] = correlation
            }
        }
        CholeskyDecomposition.decompose(matrix)

        return MarketCatalog(
            schemaVersion = root.int("schemaVersion"),
            assets = assets,
            correlationMatrix = matrix
        )
    }

    private fun JsonObject.toAssetSpec(): AssetSpec = AssetSpec(
        symbol = str("symbol"),
        name = str("name"),
        sector = str("sector"),
        category = AssetCategory.valueOf(str("category")),
        initialPrice = double("initialPrice"),
        annualVolatility = double("annualVolatility"),
        annualDrift = double("annualDrift"),
        liquidityTier = LiquidityTier.valueOf(str("liquidityTier")),
        baseVolumePerTick = long("baseVolumePerTick"),
        priceModel = PriceModelType.valueOf(stringOr("priceModel", "GBM")),
        meanReversionParams = optionalObject("meanReversionParams")?.let {
            MeanReversionParams(
                reversionSpeedPerYear = it.double("reversionSpeedPerYear"),
                longTermMeanPrice = it.double("longTermMeanPrice")
            )
        },
        jumpParams = optionalObject("jumpParams")?.let {
            JumpDiffusionParams(
                jumpIntensityPerYear = it.double("jumpIntensityPerYear"),
                jumpMeanLogSize = it.double("jumpMeanLogSize"),
                jumpStdDevLogSize = it.double("jumpStdDevLogSize")
            )
        },
        garchParams = optionalObject("garchParams")?.let {
            GarchParams(
                omegaWeight = it.double("omegaWeight"),
                alpha = it.double("alpha"),
                beta = it.double("beta")
            )
        },
        isSafeHaven = booleanOr("isSafeHaven", false)
    )
}
