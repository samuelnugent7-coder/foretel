package com.marketmc.model

import java.util.UUID

data class StoreSale(
    val companyId: UUID,
    val listingId: UUID,
    val buyer: UUID,
    val units: Int,
    val revenue: Double,
    val profit: Double,
    val timestamp: Long
)
