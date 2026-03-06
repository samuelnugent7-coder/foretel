package com.marketmc.model

import java.util.UUID

data class TradeOrder(
    val companyId: UUID,
    val playerId: UUID,
    val shares: Long,
    val ownershipPercentage: Double,
    val side: OrderSide,
    val createdAt: Long
) {
    enum class OrderSide {
        BUY,
        SELL
    }
}
