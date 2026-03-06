package com.marketmc.model

import java.util.UUID

data class ApiKeyInfo(
    val token: String,
    val label: String,
    val scopes: Set<ApiScope>,
    val createdBy: UUID?,
    val createdAt: Long,
    var lastUsed: Long = 0L
)

enum class ApiScope {
    READ_ANALYTICS,
    ADMIN
}