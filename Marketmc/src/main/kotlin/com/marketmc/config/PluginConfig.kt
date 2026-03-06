package com.marketmc.config

import com.marketmc.model.AchievementDefinition
import com.marketmc.model.AchievementTrigger
import org.bukkit.ChatColor
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import java.util.Collections
import java.util.Locale
import java.util.logging.Logger

class PluginConfig(
    private val config: FileConfiguration,
    private val logger: Logger? = null
) {
    val databaseSection: ConfigurationSection = config.getConfigurationSection("database")
        ?: logAndThrow("config.yml is missing the required 'database' section")
    val storageMode: StorageMode = StorageMode.from(config.getString("database.type", "MYSQL"))
    val fileStorageDirectory: String = config.getString("database.file.directory", "storage") ?: "storage"

    val companyCreationCost: Double = config.getDouble("companies.creationCost", 100000.0)
    val ipoFeePercent: Double = config.getDouble("companies.ipoFeePercent", 0.03)
    val listingCooldownMinutes: Int = config.getInt("companies.listingCooldownMinutes", 60)
    val defaultShares: Long = config.getLong("companies.defaultShares", 10000L)
    val defaultSharePrice: Double = config.getDouble("companies.defaultSharePrice", 10.0)
    val defaultBuyoutPrice: Double = config.getDouble("companies.defaultBuyoutPrice", 0.0)
    private val allowedSectorsInternal: List<String> = config.getStringList("companies.allowedSectors")

    val tradingTickIntervalSeconds: Int = config.getInt("trading.tickIntervalSeconds", 30)
    val volatilityMultiplier: Double = config.getDouble("trading.volatilityMultiplier", 0.5)
    val maxPriceMovePercent: Double = config.getDouble("trading.maxPriceMovePercent", 0.05)
    val baseTaxPercent: Double = config.getDouble("trading.baseTaxPercent", 0.02)
    val progressiveTaxThresholdPercent: Double = config.getDouble("trading.progressiveTaxThresholdPercent", 0.05)
    val progressiveTaxExtraPercent: Double = config.getDouble("trading.progressiveTaxExtraPercent", 0.02)
    val dailyLimitPercent: Double = config.getDouble("trading.perPlayerDailyLimitPercent", 0.05)
    val dailyLimitShares: Long = config.getLong("trading.perPlayerDailyLimitShares", 10000L)
    val holdingPeriodSeconds: Int = config.getInt("trading.holdingPeriodSeconds", 600)
    val tradeCooldownSeconds: Int = config.getInt("trading.tradeCooldownSeconds", 30)
    val buySellCooldownSeconds: Int = config.getInt("trading.buySellCooldownSeconds", 120)

    val macroInflationBase: Double = config.getDouble("market.macro.inflationBase", 0.012)
    val macroLiquidityBase: Double = config.getDouble("market.macro.liquidityBase", 0.018)
    val macroGrowthBase: Double = config.getDouble("market.macro.growthBase", 0.01)
    val macroVolatilityFloor: Double = config.getDouble("market.macro.volatilityFloor", 0.15)
    val macroNoiseStrength: Double = config.getDouble("market.macro.noiseStrength", 0.2)
    val supplyShockChance: Double = config.getDouble("market.shocks.supplyShockChance", 0.12)
    val demandShockChance: Double = config.getDouble("market.shocks.demandShockChance", 0.1)
    val maxShockPercent: Double = config.getDouble("market.shocks.maxShockPercent", 0.06)
    val sectorCorrelation: Double = config.getDouble("market.shocks.sectorCorrelation", 0.45)
    val priceHistoryWindow: Int = config.getInt("market.priceHistoryWindow", 12)
    val sentimentMin: Double = config.getDouble("market.sentiment.min", -0.5)
    val sentimentMax: Double = config.getDouble("market.sentiment.max", 0.6)
    val sentimentWobbleSeconds: Int = config.getInt("market.sentiment.wobbleSeconds", 120)

    val rapidTradeThresholdSeconds: Int = config.getInt("antiExploit.rapidTradeThresholdSeconds", 10)
    val rapidTradeMaxPerWindow: Int = config.getInt("antiExploit.rapidTradeMaxPerWindow", 4)
    val maxSharesPerMinutePercent: Double = config.getDouble("antiExploit.maxSharesPerMinutePercent", 0.12)
    val suspiciousProfitPercent: Double = config.getDouble("antiExploit.suspiciousProfitPercent", 0.25)
    val flagDecayMinutes: Int = config.getInt("antiExploit.flagDecayMinutes", 60)
    val severityWeightRapid: Double = config.getDouble("antiExploit.severityWeights.rapid", 0.4)
    val severityWeightVolume: Double = config.getDouble("antiExploit.severityWeights.volume", 0.35)
    val severityWeightProfit: Double = config.getDouble("antiExploit.severityWeights.profit", 0.25)

    val startingEconomyBalance: Double = config.getDouble("economy.startingBalance", 500000.0)
    val maxBoostPercent: Double = config.getDouble("economy.maxBoostPercent", 0.04)
    val boostCooldownSeconds: Int = config.getInt("economy.boostCooldownSeconds", 900)
    val treasurySupportPercent: Double = config.getDouble("economy.treasurySupportPercent", 0.02)
    val storeKickbackPercent: Double = config.getDouble("economy.storeKickbackPercent", 0.015)

    val revenueWeight: Double = config.getDouble("store.revenueWeight", 0.5)
    val listingFee: Double = config.getDouble("store.listingFee", 500.0)
    val upkeepPerDay: Double = config.getDouble("store.upkeepPerDay", 100.0)
    val saleAnnouncementCost: Double = config.getDouble("store.saleAnnouncementCost", 10_000.0)
    val saleMaxPercent: Double = config.getDouble("store.saleMaxPercent", 0.75)

    val marketingEnabled: Boolean = config.getBoolean("marketing.enabled", true)
    val marketingMinSpend: Double = config.getDouble("marketing.minSpend", 5000.0)
    val marketingMaxSpend: Double = config.getDouble("marketing.maxSpend", 250000.0)
    val marketingDurationMinutes: Int = config.getInt("marketing.durationMinutes", 45)
    val marketingBoostPerThousand: Double = config.getDouble("marketing.boostPerThousand", 0.0015)

    val analyticsEnabled: Boolean = config.getBoolean("analytics.enabled", false)
    val analyticsBindAddress: String = config.getString("analytics.bindAddress", "127.0.0.1") ?: "127.0.0.1"
    val analyticsPort: Int = config.getInt("analytics.port", 8913)
    val analyticsRequireKey: Boolean = config.getBoolean("analytics.requireKey", true)

    val sandboxEnabled: Boolean = config.getBoolean("sandbox.enabled", true)
    val sandboxMaxDurationMinutes: Int = config.getInt("sandbox.maxDurationMinutes", 15)
    val sandboxTickAcceleration: Int = config.getInt("sandbox.tickAcceleration", 4).coerceAtLeast(1)

    val achievementsEnabled: Boolean = config.getBoolean("achievements.enabled", true)
    private val achievementDefinitionsInternal: List<AchievementDefinition> = loadAchievements(
        config.getConfigurationSection("achievements.definitions")
    )

    val sinkEventPenaltyPercent: Double = config.getDouble("sinks.eventPenaltyPercent", 0.01)
    val dividendTaxPercent: Double = config.getDouble("sinks.dividendTaxPercent", 0.05)

    val eventMinIntervalMinutes: Int = config.getInt("events.minIntervalMinutes", 180)
    val eventMaxIntervalMinutes: Int = config.getInt("events.maxIntervalMinutes", 240).coerceAtLeast(eventMinIntervalMinutes)
    val eventDurationMinutes: Int = config.getInt("events.durationMinutes", 45)
    private val eventDefinitionsInternal: Map<String, EventDefinition> = loadEvents(config.getConfigurationSection("events.definitions"))

    val allowedSectors: List<String>
        get() = Collections.unmodifiableList(allowedSectorsInternal)

    val eventDefinitions: Map<String, EventDefinition>
        get() = Collections.unmodifiableMap(eventDefinitionsInternal)

    val achievementDefinitions: List<AchievementDefinition>
        get() = Collections.unmodifiableList(achievementDefinitionsInternal)

    val guiMarketOverviewTitle: String = colorize(config.getString("gui.marketOverviewTitle", "&6Market Pulse"))
    val guiCompanyDetailTitle: String = colorize(config.getString("gui.companyDetailTitle", "&9Company Brief"))
    val guiStoreTitle: String = colorize(config.getString("gui.storeTitle", "&eCompany Storefront"))
    val guiAdminTitle: String = colorize(config.getString("gui.adminTitle", "&cEconomy Controls"))
    val guiSusTitle: String = colorize(config.getString("gui.susTitle", "&4Market SUS Board"))
    val guiPortfolioTitle: String = colorize(config.getString("gui.portfolioTitle", "&aHoldings"))

    init {
        validateConfig()
    }

    private fun loadEvents(section: ConfigurationSection?): Map<String, EventDefinition> {
        if (section == null) {
            return emptyMap()
        }
        val map = mutableMapOf<String, EventDefinition>()
        for (key in section.getKeys(false)) {
            val defSection = section.getConfigurationSection(key) ?: continue
            val definition = EventDefinition(
                key = key,
                description = defSection.getString("description", "Event") ?: "Event",
                multiplier = defSection.getDouble("multiplier", 1.0),
                sectors = defSection.getStringList("sectors")
            )
            map[key] = definition
        }
        return map
    }

    private fun loadAchievements(section: ConfigurationSection?): List<AchievementDefinition> {
        if (section == null) {
            return emptyList()
        }
        val list = mutableListOf<AchievementDefinition>()
        for (key in section.getKeys(false)) {
            val defSection = section.getConfigurationSection(key) ?: continue
            val triggerRaw = defSection.getString("trigger")?.uppercase(Locale.ROOT) ?: continue
            val trigger = runCatching { AchievementTrigger.valueOf(triggerRaw) }.getOrNull()
            if (trigger == null) {
                logger?.warning("config.yml warning: achievements.definitions.$key has invalid trigger '$triggerRaw'")
                continue
            }
            val threshold = defSection.getDouble("threshold", 0.0)
            val name = defSection.getString("name", key) ?: key
            val description = defSection.getString("description", "") ?: ""
            list += AchievementDefinition(
                id = key,
                name = colorize(name),
                description = colorize(description),
                trigger = trigger,
                threshold = threshold
            )
        }
        return list
    }

    private fun colorize(input: String?): String {
        val raw = input ?: ""
        return ChatColor.translateAlternateColorCodes('&', raw)
    }

    private fun validateConfig() {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (tradingTickIntervalSeconds <= 0) {
            errors += "trading.tickIntervalSeconds must be greater than zero."
        }
        if (marketingMinSpend <= 0) {
            errors += "marketing.minSpend must be greater than zero."
        }
        if (marketingMaxSpend <= 0) {
            errors += "marketing.maxSpend must be greater than zero."
        }
        if (marketingMinSpend > marketingMaxSpend) {
            errors += "marketing.minSpend ($marketingMinSpend) cannot exceed marketing.maxSpend ($marketingMaxSpend)."
        }
        if (analyticsPort !in 1..65535) {
            errors += "analytics.port must be between 1 and 65535 (was $analyticsPort)."
        }
        if (sandboxMaxDurationMinutes <= 0) {
            errors += "sandbox.maxDurationMinutes must be greater than zero."
        }
        if (allowedSectorsInternal.isEmpty()) {
            warnings += "companies.allowedSectors is empty; players must type sectors manually."
        }
        if (dailyLimitPercent <= 0.0 && dailyLimitShares <= 0) {
            warnings += "Both trading.perPlayerDailyLimitPercent and trading.perPlayerDailyLimitShares are <= 0; daily cap effectively disabled."
        }
        if (dailyLimitShares < 0) {
            errors += "trading.perPlayerDailyLimitShares must be zero or positive."
        }
        if (saleAnnouncementCost < 0.0) {
            errors += "store.saleAnnouncementCost must be zero or positive."
        }
        if (saleMaxPercent <= 0.0 || saleMaxPercent >= 1.0) {
            errors += "store.saleMaxPercent must be between 0 and 1 (exclusive)."
        }

        when (storageMode) {
            StorageMode.MYSQL -> {
                val mysql = databaseSection.getConfigurationSection("mysql")
                if (mysql == null) {
                    errors += "database.mysql section is required when database.type is MYSQL."
                } else {
                    if (mysql.getString("host").isNullOrBlank()) {
                        errors += "database.mysql.host must be provided."
                    }
                    if (mysql.getString("database").isNullOrBlank()) {
                        errors += "database.mysql.database must be provided."
                    }
                    if (mysql.getString("username").isNullOrBlank()) {
                        warnings += "database.mysql.username is blank; defaulting to 'root'."
                    }
                }
            }
            StorageMode.MONGODB -> {
                val mongo = databaseSection.getConfigurationSection("mongodb")
                if (mongo == null) {
                    errors += "database.mongodb section is required when database.type is MONGODB."
                } else if (mongo.getString("connectionString").isNullOrBlank()) {
                    errors += "database.mongodb.connectionString must be provided."
                }
            }
            StorageMode.FILE -> {
                if (fileStorageDirectory.isBlank()) {
                    errors += "database.file.directory cannot be blank when using FILE storage."
                }
            }
        }

        warnings.forEach { logger?.warning("config.yml warning: $it") }
        if (errors.isNotEmpty()) {
            errors.forEach { logger?.severe("config.yml error: $it") }
            throw IllegalStateException("Invalid MarketMC configuration; see errors above.")
        }
    }

    private fun logAndThrow(message: String): Nothing {
        logger?.severe(message)
        throw IllegalStateException(message)
    }
}

enum class StorageMode {
    MYSQL,
    MONGODB,
    FILE;

    companion object {
        fun from(raw: String?): StorageMode {
            val normalized = raw?.uppercase() ?: "MYSQL"
            return entries.firstOrNull { it.name == normalized } ?: MYSQL
        }
    }
}
