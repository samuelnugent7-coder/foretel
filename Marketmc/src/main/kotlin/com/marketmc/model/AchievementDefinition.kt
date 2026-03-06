package com.marketmc.model

data class AchievementDefinition(
    val id: String,
    val name: String,
    val description: String,
    val trigger: AchievementTrigger,
    val threshold: Double
)

enum class AchievementTrigger {
    SHARES_TRADED,
    MARKETING_SPEND,
    STORE_PURCHASES,
    COMPANY_MARKET_CAP
}