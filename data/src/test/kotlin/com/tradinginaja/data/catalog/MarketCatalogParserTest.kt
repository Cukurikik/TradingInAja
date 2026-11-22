package com.tradinginaja.data.catalog

import com.tradinginaja.domain.engine.CholeskyDecomposition
import com.tradinginaja.domain.engine.MarketEngine
import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.PriceModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketCatalogParserTest {

    @Test
    fun `versioned json catalog parses all 10 fictional assets and passes cholesky decomposition`() {
        val stream = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("market_catalog_v1.json")
        ) {
            "market_catalog_v1.json harus tersedia di test resources."
        }
        val rawJson = stream.bufferedReader().use { it.readText() }
        val catalog = AssetCatalogParser.parse(rawJson)

        assertEquals(1, catalog.schemaVersion)
        assertEquals(10, catalog.assets.size)

        val symbols = catalog.assets.map { it.symbol }.toSet()
        assertEquals(
            setOf("NSKP", "BPAT", "PSRY", "SDKT", "GRDC", "KPSW", "XVMG", "MBSO", "VUD/VRP", "IKN30"),
            symbols
        )

        val mbso = catalog.assets.first { it.symbol == "MBSO" }
        assertEquals(PriceModelType.MEAN_REVERSION_OU, mbso.priceModel)
        assertNotNull(mbso.meanReversionParams)

        val vud = catalog.assets.first { it.symbol == "VUD/VRP" }
        assertEquals(AssetCategory.FOREX, vud.category)
        assertEquals(PriceModelType.MEAN_REVERSION_OU, vud.priceModel)

        val grdc = catalog.assets.first { it.symbol == "GRDC" }
        assertNotNull(grdc.jumpParams)

        val lower = CholeskyDecomposition.decompose(catalog.correlationMatrix)
        assertEquals(10, lower.size)

        val multiState = MarketEngine.createMultiAssetInitialState(catalog, initialCandles = 20)
        assertEquals(10, multiState.assetStates.size)
        assertTrue(multiState.assetStates.all { it.currentPrice > 0.0 && it.baseCandles.size == 20 })
    }
}
