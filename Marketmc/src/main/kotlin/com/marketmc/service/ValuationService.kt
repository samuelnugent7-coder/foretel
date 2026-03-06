package com.marketmc.service

class ValuationService {
    fun calculateRevenueImpact(revenueDelta: Double, totalShares: Long, weight: Double): Double {
        if (totalShares <= 0) {
            return 0.0
        }
        val impact = (revenueDelta / totalShares) * weight
        return impact.coerceAtLeast(-Double.MAX_VALUE)
    }
}
