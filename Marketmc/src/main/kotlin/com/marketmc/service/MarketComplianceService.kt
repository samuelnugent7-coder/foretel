package com.marketmc.service

import com.marketmc.config.PluginConfig
import com.marketmc.data.DataRepository
import com.marketmc.model.Company
import com.marketmc.model.ExploitIncident
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayDeque
import kotlin.math.abs
import kotlin.math.min

class MarketComplianceService(
    private val config: PluginConfig,
    private val repository: DataRepository,
    private val securitySettings: SecuritySettings
) {
    private val rapidTradeWindows = ConcurrentHashMap<UUID, ArrayDeque<Long>>()
    private val volumeWindows = ConcurrentHashMap<UUID, VolumeWindow>()
    private val volumeWindowMillis = 60_000L
    private val severityThreshold = 0.45

    fun recordBuy(playerId: UUID, company: Company, shares: Long, pricePerShare: Double) {
        analyzeTrade(playerId, company, shares, pricePerShare, 0.0, "BUY")
    }

    fun recordSell(playerId: UUID, company: Company, shares: Long, pricePerShare: Double, averageCost: Double) {
        val profitPercent = if (averageCost > 0) (pricePerShare - averageCost) / averageCost else 0.0
        analyzeTrade(playerId, company, shares, pricePerShare, profitPercent, "SELL")
    }

    private fun analyzeTrade(
        playerId: UUID,
        company: Company,
        shares: Long,
        pricePerShare: Double,
        profitPercent: Double,
        action: String
    ) {
        val now = System.currentTimeMillis()
        val rapidScore = scoreRapidTrades(playerId, now)
        val volumeScore = scoreVolume(playerId, company, shares, now)
        val profitScore = scoreProfit(profitPercent)
        val severity = rapidScore * config.severityWeightRapid +
            volumeScore * config.severityWeightVolume +
            profitScore * config.severityWeightProfit
        if (severity < severityThreshold) {
            return
        }
        val reasons = mutableListOf<String>()
        if (rapidScore > 0.0) reasons += "Rapid sequencing"
        if (volumeScore > 0.0) reasons += "Excess share flow"
        if (profitScore > 0.0) reasons += "Abnormal profit"
        val detail = "Shares=$shares @ ${"%.2f".format(pricePerShare)}"
        repository.recordExploitIncident(
            ExploitIncident(
                playerId = playerId,
                companyId = company.id,
                action = action,
                reason = reasons.joinToString(" + "),
                severity = min(1.0, severity),
                details = detail,
                createdAt = now
            )
        )
    }

    private fun scoreRapidTrades(playerId: UUID, now: Long): Double {
        val thresholdMillis = rapidThresholdMillis()
        val window = rapidTradeWindows.computeIfAbsent(playerId) { ArrayDeque() }
        window.addLast(now)
        while (window.isNotEmpty() && now - window.first() > thresholdMillis) {
            window.removeFirst()
        }
        val maxPerWindow = securitySettings.rapidTradeMaxPerWindow().coerceAtLeast(1)
        return if (window.size > maxPerWindow) {
            window.size.toDouble() / maxPerWindow.toDouble()
        } else {
            0.0
        }
    }

    private fun scoreVolume(playerId: UUID, company: Company, shares: Long, now: Long): Double {
        if (company.totalShares <= 0) {
            return 0.0
        }
        val percent = shares.toDouble() / company.totalShares.toDouble()
        val window = volumeWindows.computeIfAbsent(playerId) { VolumeWindow(now, 0.0) }
        if (now - window.startedAt > volumeWindowMillis) {
            window.startedAt = now
            window.percent = 0.0
        }
        window.percent += percent
        val maxPercent = securitySettings.maxSharesPerMinutePercent().coerceAtLeast(0.0001)
        return if (window.percent > maxPercent) {
            window.percent / maxPercent
        } else {
            0.0
        }
    }

    private fun scoreProfit(profitPercent: Double): Double {
        if (profitPercent <= config.suspiciousProfitPercent) {
            return 0.0
        }
        val ratio = profitPercent / config.suspiciousProfitPercent
        return min(1.5, abs(ratio))
    }

    private data class VolumeWindow(var startedAt: Long, var percent: Double)

    private fun rapidThresholdMillis(): Long = securitySettings.rapidTradeThresholdSeconds().coerceAtLeast(1) * 1000L

    fun getRecentIncidents(limit: Int = 36) = repository.fetchExploitIncidents(limit)

    fun getIncidentsForPlayer(playerId: UUID, limit: Int = 12) = repository.fetchExploitIncidentsForPlayer(playerId, limit)

    fun clearIncidents(playerId: UUID) = repository.clearExploitIncidents(playerId)
}
