package com.marketmc.model

import java.util.UUID

data class TransactionRecord(
    val companyId: UUID,
    val buyer: UUID?,
    val seller: UUID?,
    val shares: Long,
    val price: Double,
    val taxPaid: Double,
    val timestamp: Long
)
