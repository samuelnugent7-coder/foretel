package com.marketmc.model

class MarketEvent(
    val key: String,
    val description: String,
    val multiplier: Double,
    val sectors: Set<String>,
    val expiresAt: Long
) {
    fun isExpired(now: Long): Boolean = now >= expiresAt
}
