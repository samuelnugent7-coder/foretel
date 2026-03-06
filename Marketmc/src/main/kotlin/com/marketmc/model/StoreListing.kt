package com.marketmc.model

import java.util.UUID

data class StoreListing(
    val id: UUID,
    val companyId: UUID,
    val itemSerialized: String,
    var price: Double,
    var stock: Int,
    val createdAt: Long
)
