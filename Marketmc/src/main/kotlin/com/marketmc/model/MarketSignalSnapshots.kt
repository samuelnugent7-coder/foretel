package com.marketmc.model

data class MacroSnapshot(
    val inflation: Double,
    val liquidity: Double,
    val growth: Double,
    val sentiment: Double
)

data class CompanySignalSnapshot(
    val changePercent: Double,
    val cause: String,
    val updatedAt: Long
)

data class ShockSnapshot(
    val sector: String,
    val label: String,
    val magnitude: Double,
    val expiresAt: Long
)

data class MarketSignalResult(
    val adjustedDemand: Double,
    val volatilityFactor: Double,
    val cause: String,
    val macroSnapshot: MacroSnapshot
)
