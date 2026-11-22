package com.tradinginaja.feature.market

import androidx.annotation.StringRes
import com.tradinginaja.domain.model.AssetCategory
import com.tradinginaja.domain.model.AssetSpec
import com.tradinginaja.domain.model.PriceModelType

enum class MarketFilter(@StringRes val labelRes: Int) {
    ALL(R.string.market_filter_all),
    WATCHLIST(R.string.market_filter_watchlist),
    STOCK(R.string.market_filter_stock),
    CRYPTO(R.string.market_filter_crypto),
    COMMODITY(R.string.market_filter_commodity),
    FOREX(R.string.market_filter_forex),
    INDEX(R.string.market_filter_index);

    fun matches(category: AssetCategory, isWatchlisted: Boolean): Boolean = when (this) {
        ALL -> true
        WATCHLIST -> isWatchlisted
        STOCK -> category == AssetCategory.STOCK
        CRYPTO -> category == AssetCategory.CRYPTO
        COMMODITY -> category == AssetCategory.COMMODITY
        FOREX -> category == AssetCategory.FOREX
        INDEX -> category == AssetCategory.INDEX
    }
}

@StringRes
fun AssetSpec.priceModelLabelRes(): Int = when {
    priceModel == PriceModelType.MEAN_REVERSION_OU && jumpParams != null -> R.string.market_model_ou_jump
    priceModel == PriceModelType.MEAN_REVERSION_OU -> R.string.market_model_ou
    jumpParams != null -> R.string.market_model_gbm_jump
    else -> R.string.market_model_gbm
}
