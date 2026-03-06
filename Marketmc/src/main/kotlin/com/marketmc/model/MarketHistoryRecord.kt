package com.marketmc.model

import java.util.UUID

data class MarketHistoryRecord(
    val companyId: UUID,
    val price: Double,
    val netDemand: Double,
    val cause: String?,
    val timestamp: Long
)
