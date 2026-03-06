package com.marketmc.model

import java.util.UUID

class Company(
    val id: UUID,
    var owner: UUID,
    val name: String,
    val sector: String,
    val totalShares: Long,
    var availableShares: Long,
    var sharePrice: Double,
    var marketCap: Double,
    var treasury: Double,
    var storeRevenue: Double,
    var demandScore: Double,
    var volatility: Double,
    var unitsSold: Long,
    val createdAt: Long,
    var revenueGrowth: Double,
    var buyoutPrice: Double = 0.0,
    var storeSalePercent: Double = 0.0,
    var lastSaleAnnouncementAt: Long = 0L
) {
    fun getOwnershipPercentage(sharesOwned: Long): Double {
        if (totalShares <= 0) {
            return 0.0
        }
        return sharesOwned.toDouble() / totalShares.toDouble()
    }
}
