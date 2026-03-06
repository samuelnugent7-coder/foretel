package com.marketmc.service

import com.marketmc.config.PluginConfig
import com.marketmc.model.Company
import com.marketmc.model.Shareholder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class AntiManipulationService(
    private val config: PluginConfig,
    private val securitySettings: SecuritySettings
) {
    private val profiles = ConcurrentHashMap<UUID, PlayerTradeProfile>()
    private val holdingUnlocks = ConcurrentHashMap<UUID, MutableMap<UUID, Long>>()

    fun validateBuy(playerId: UUID, company: Company, sharesToBuy: Long): String? {
        val profile = profiles.computeIfAbsent(playerId) { PlayerTradeProfile() }
        profile.resetIfNeeded()

        val now = System.currentTimeMillis()
        val tradeCooldown = securitySettings.tradeCooldownSeconds()
        if (!profile.canTrade(now, tradeCooldown)) {
            val remaining = profile.remainingCooldown(now, tradeCooldown) / 1000
            return "You must wait ${remaining}s before trading again."
        }
        val buySellCooldown = securitySettings.buySellCooldownSeconds()
        if (!profile.canBuyAfterSell(now, buySellCooldown)) {
            val remaining = profile.remainingSellCooldown(now, buySellCooldown) / 1000
            return "You must wait ${remaining}s before buying after a sale."
        }
        if (company.totalShares <= 0) {
            return "Trading is unavailable for this company."
        }
        val deltaPercent = sharesToBuy.toDouble() / company.totalShares.toDouble()
        val percentLimitEnabled = config.dailyLimitPercent > 0.0
        val shareLimitEnabled = config.dailyLimitShares > 0
        val percentExceeded = percentLimitEnabled && profile.getDailyPercent(company.id) + deltaPercent > config.dailyLimitPercent
        val shareExceeded = shareLimitEnabled && profile.getDailyShareCount(company.id) + sharesToBuy > config.dailyLimitShares
        if (percentExceeded || shareExceeded) {
            return "Daily purchase cap reached for this company."
        }
        return null
    }

    fun validateSell(playerId: UUID, company: Company, shareholder: Shareholder, sharesToSell: Long): String? {
        val profile = profiles.computeIfAbsent(playerId) { PlayerTradeProfile() }
        profile.resetIfNeeded()

        val now = System.currentTimeMillis()
        val tradeCooldown = securitySettings.tradeCooldownSeconds()
        if (!profile.canTrade(now, tradeCooldown)) {
            val remaining = profile.remainingCooldown(now, tradeCooldown) / 1000
            return "You must wait ${remaining}s before trading again."
        }
        val holdings = holdingUnlocks[playerId]
        if (holdings != null) {
            val unlock = holdings[company.id] ?: 0L
            if (unlock > now) {
                val remaining = (unlock - now) / 1000
                return "Shares are in lockup for ${remaining}s."
            }
        }
        if (sharesToSell > shareholder.sharesOwned) {
            return "You do not own that many shares."
        }
        return null
    }

    fun recordBuy(playerId: UUID, company: Company, shares: Long) {
        if (company.totalShares <= 0) {
            return
        }
        val deltaPercent = shares.toDouble() / company.totalShares.toDouble()
        val now = System.currentTimeMillis()
        val profile = profiles.computeIfAbsent(playerId) { PlayerTradeProfile() }
        profile.resetIfNeeded()
        profile.recordTrade(now, deltaPercent, true)
        profile.addCompanyTrade(company.id, deltaPercent, shares)
        holdingUnlocks.computeIfAbsent(playerId) { ConcurrentHashMap() }[company.id] =
            now + securitySettings.holdingPeriodSeconds().coerceAtLeast(0) * 1000L
    }

    fun recordSell(playerId: UUID, company: Company, shares: Long) {
        if (company.totalShares <= 0) {
            return
        }
        val deltaPercent = shares.toDouble() / company.totalShares.toDouble()
        val now = System.currentTimeMillis()
        val profile = profiles.computeIfAbsent(playerId) { PlayerTradeProfile() }
        profile.resetIfNeeded()
        profile.recordTrade(now, deltaPercent, false)
    }

    fun weightDemand(sharesBought: Long, ownershipPercentage: Double): Double {
        val cappedOwnership = ownershipPercentage.coerceAtMost(0.99)
        return sharesBought * (1.0 - cappedOwnership)
    }

    private class PlayerTradeProfile {
        private var dailyPercent = 0.0
        private var lastDayOfYear = 0
        private var lastTradeAt = 0L
        private var lastSellAt = 0L
        private val perCompanyPercent = ConcurrentHashMap<UUID, Double>()
        private val perCompanyShares = ConcurrentHashMap<UUID, Long>()

        fun resetIfNeeded() {
            val day = ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).dayOfYear
            if (day != lastDayOfYear) {
                lastDayOfYear = day
                dailyPercent = 0.0
                perCompanyPercent.clear()
                perCompanyShares.clear()
            }
        }

        fun canTrade(now: Long, cooldownSeconds: Int): Boolean {
            return now - lastTradeAt >= cooldownSeconds * 1000L
        }

        fun remainingCooldown(now: Long, cooldownSeconds: Int): Long {
            return max(0L, cooldownSeconds * 1000L - (now - lastTradeAt))
        }

        fun canBuyAfterSell(now: Long, cooldownSeconds: Int): Boolean {
            return now - lastSellAt >= cooldownSeconds * 1000L
        }

        fun remainingSellCooldown(now: Long, cooldownSeconds: Int): Long {
            return max(0L, cooldownSeconds * 1000L - (now - lastSellAt))
        }

        fun getDailyPercent(companyId: UUID): Double {
            return perCompanyPercent[companyId] ?: 0.0
        }

        fun getDailyShareCount(companyId: UUID): Long {
            return perCompanyShares[companyId] ?: 0L
        }

        fun recordTrade(now: Long, percent: Double, buy: Boolean) {
            lastTradeAt = now
            if (!buy) {
                lastSellAt = now
            }
            dailyPercent += percent
        }

        fun addCompanyTrade(companyId: UUID, percent: Double, shares: Long) {
            perCompanyPercent.merge(companyId, percent) { existing, addition -> existing + addition }
            perCompanyShares.merge(companyId, shares) { existing, addition -> existing + addition }
        }
    }
}
