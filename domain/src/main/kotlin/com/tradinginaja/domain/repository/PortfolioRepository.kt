package com.tradinginaja.domain.repository

import com.tradinginaja.domain.model.CareerLevel
import com.tradinginaja.domain.model.OrderRequest
import com.tradinginaja.domain.model.OrderSubmissionResult
import com.tradinginaja.domain.model.PortfolioState
import kotlinx.coroutines.flow.StateFlow

interface PortfolioRepository {
    val portfolioState: StateFlow<PortfolioState?>

    suspend fun ensureInitialized()
    suspend fun submitOrder(request: OrderRequest): OrderSubmissionResult
    suspend fun cancelOrder(orderId: String)
    suspend fun setCareerLevel(level: CareerLevel)
    suspend fun resetPortfolio()
}
