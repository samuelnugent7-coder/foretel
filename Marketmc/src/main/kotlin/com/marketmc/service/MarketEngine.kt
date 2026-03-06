package com.marketmc.service

import com.marketmc.MarketMCPlugin
import com.marketmc.config.PluginConfig
import com.marketmc.data.DataRepository
import com.marketmc.model.Company
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MarketEngine(
    private val plugin: MarketMCPlugin,
    private val config: PluginConfig,
    private val repository: DataRepository,
    private val companyService: CompanyService,
    private val marketSignalService: MarketSignalService,
    private val marketingService: MarketingService,
    private val achievementService: AchievementService
) {
    private val demandMap = ConcurrentHashMap<UUID, CompanyDemand>()

    fun recordBuy(companyId: UUID, rawShares: Long, weightedShares: Double) {
        demandMap.computeIfAbsent(companyId) { CompanyDemand() }
            .addBuy(rawShares, weightedShares)
    }

    fun recordSell(companyId: UUID, rawShares: Long) {
        demandMap.computeIfAbsent(companyId) { CompanyDemand() }
            .addSell(rawShares)
    }

    fun processTick() {
        marketSignalService.tick()
        marketingService.tick()
        companyService.getCompanies().forEach { company ->
            val demand = demandMap[company.id] ?: CompanyDemand.EMPTY
            val netDemand = computeNetDemand(company, demand) + marketingService.getBoost(company.id)
            if (netDemand != 0.0) {
                val signal = marketSignalService.composeSignal(company, netDemand)
                val effectiveDemand = signal.adjustedDemand
                val volatilityFactor = signal.volatilityFactor
                val priceChange = company.sharePrice * effectiveDemand * config.volatilityMultiplier * volatilityFactor
                val maxMove = company.sharePrice * config.maxPriceMovePercent * volatilityFactor
                val boundedChange = max(-maxMove, min(maxMove, priceChange))
                val newPrice = (company.sharePrice + boundedChange).coerceAtLeast(0.01)
                val changePercent = if (company.sharePrice > 0) boundedChange / company.sharePrice else 0.0
                companyService.updateMarketMetrics(company, newPrice, effectiveDemand)
                repository.insertMarketHistory(company.id, newPrice, effectiveDemand, signal.cause)
                marketSignalService.recordPriceSignal(company.id, changePercent, signal.cause)
                achievementService.evaluateCompany(company)
                if (abs(effectiveDemand) > 0.01) {
                    plugin.logger.fine("Price updated for ${company.name} -> $newPrice (${String.format("%.2f", changePercent * 100)}%)")
                }
            }
        }
        demandMap.clear()
    }

    fun flushSnapshots() {
        demandMap.clear()
    }

    private fun computeNetDemand(company: Company, demand: CompanyDemand): Double {
        if (company.totalShares <= 0) {
            return 0.0
        }
        return (demand.weightedBuy - demand.sellVolume) / company.totalShares.toDouble()
    }

    private class CompanyDemand {
        var buyVolume: Long = 0
            private set
        var sellVolume: Long = 0
            private set
        var weightedBuy: Double = 0.0
            private set

        fun addBuy(rawShares: Long, weightedShares: Double) {
            buyVolume += rawShares
            weightedBuy += weightedShares
        }

        fun addSell(rawShares: Long) {
            sellVolume += rawShares
        }

        companion object {
            val EMPTY = CompanyDemand()
        }
    }
}
