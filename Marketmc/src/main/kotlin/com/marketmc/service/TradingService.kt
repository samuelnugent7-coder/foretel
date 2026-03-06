package com.marketmc.service

import com.marketmc.config.PluginConfig
import com.marketmc.data.DataRepository
import com.marketmc.economy.VaultHook
import com.marketmc.model.Company
import com.marketmc.model.Shareholder
import com.marketmc.model.TransactionRecord
import com.marketmc.service.AchievementService
import org.bukkit.entity.Player

class TradingService(
    private val config: PluginConfig,
    private val repository: DataRepository,
    private val companyService: CompanyService,
    private val antiManipulationService: AntiManipulationService,
    private val marketEngine: MarketEngine,
    private val complianceService: MarketComplianceService,
    private val vaultHook: VaultHook,
    private val achievementService: AchievementService
) {

    fun buyShares(player: Player, company: Company, shares: Long) {
        if (shares <= 0) {
            player.sendMessage("Enter a positive share amount.")
            return
        }
        if (company.availableShares < shares) {
            player.sendMessage("Not enough available shares.")
            return
        }
        val existingHolder = repository.fetchShareholder(player.uniqueId, company.id)
        val owned = existingHolder?.sharesOwned ?: 0L
        val blockReason = antiManipulationService.validateBuy(player.uniqueId, company, shares)
        if (blockReason != null) {
            player.sendMessage(blockReason)
            return
        }

        val pricePerShare = company.sharePrice
        val gross = pricePerShare * shares
        val tax = calculateTax(company, shares, gross)
        val totalCost = gross + tax

        if (!vaultHook.withdraw(player, totalCost)) {
            player.sendMessage("You do not have enough funds. Cost: $totalCost")
            return
        }

        val shareholder = existingHolder ?: Shareholder(
            playerId = player.uniqueId,
            companyId = company.id,
            sharesOwned = 0,
            avgBuyPrice = pricePerShare,
            lastTradeAt = 0
        )
        val newAvg = if (shareholder.sharesOwned + shares > 0) {
            ((shareholder.sharesOwned * shareholder.avgBuyPrice) + gross) /
                (shareholder.sharesOwned + shares)
        } else {
            pricePerShare
        }
        shareholder.sharesOwned += shares
        shareholder.avgBuyPrice = newAvg
        shareholder.lastTradeAt = System.currentTimeMillis()
        repository.upsertShareholder(shareholder)

        company.availableShares -= shares
        companyService.updateCompany(company)

        val ownershipBefore = company.getOwnershipPercentage(owned)
        val weighted = antiManipulationService.weightDemand(shares, ownershipBefore)
        antiManipulationService.recordBuy(player.uniqueId, company, shares)
        marketEngine.recordBuy(company.id, shares, weighted)
        complianceService.recordBuy(player.uniqueId, company, shares, pricePerShare)

        repository.insertTransaction(
            TransactionRecord(
                companyId = company.id,
                buyer = player.uniqueId,
                seller = null,
                shares = shares,
                price = pricePerShare,
                taxPaid = tax,
                timestamp = System.currentTimeMillis()
            )
        )

        achievementService.recordSharesTraded(player.uniqueId, shares)
        player.sendMessage("Bought $shares shares of ${company.name} at \$${pricePerShare}")
    }

    fun sellShares(player: Player, company: Company, shares: Long) {
        if (shares <= 0) {
            player.sendMessage("Enter a positive share amount.")
            return
        }
        val shareholder = repository.fetchShareholder(player.uniqueId, company.id)
        if (shareholder == null) {
            player.sendMessage("You do not own shares in this company.")
            return
        }
        val blockReason = antiManipulationService.validateSell(player.uniqueId, company, shareholder, shares)
        if (blockReason != null) {
            player.sendMessage(blockReason)
            return
        }
        if (shareholder.sharesOwned < shares) {
            player.sendMessage("Insufficient shares.")
            return
        }

        val pricePerShare = company.sharePrice
        val gross = pricePerShare * shares
        val tax = calculateTax(company, shares, gross)
        val payout = (gross - tax).coerceAtLeast(0.0)

        val avgCost = shareholder.avgBuyPrice
        shareholder.sharesOwned -= shares
        shareholder.lastTradeAt = System.currentTimeMillis()
        if (shareholder.sharesOwned <= 0) {
            repository.deleteShareholder(player.uniqueId, company.id)
        } else {
            repository.upsertShareholder(shareholder)
        }

        company.availableShares += shares
        companyService.updateCompany(company)

        vaultHook.deposit(player, payout)
        antiManipulationService.recordSell(player.uniqueId, company, shares)
        marketEngine.recordSell(company.id, shares)
        complianceService.recordSell(player.uniqueId, company, shares, pricePerShare, avgCost)

        repository.insertTransaction(
            TransactionRecord(
                companyId = company.id,
                buyer = null,
                seller = player.uniqueId,
                shares = shares,
                price = pricePerShare,
                taxPaid = tax,
                timestamp = System.currentTimeMillis()
            )
        )

        achievementService.recordSharesTraded(player.uniqueId, shares)
        player.sendMessage("Sold $shares shares of ${company.name} for \$${payout}")
    }

    private fun calculateTax(company: Company, shares: Long, gross: Double): Double {
        var tax = gross * config.baseTaxPercent
        val percent = if (company.totalShares > 0) {
            shares.toDouble() / company.totalShares.toDouble()
        } else {
            0.0
        }
        if (percent >= config.progressiveTaxThresholdPercent) {
            tax += gross * config.progressiveTaxExtraPercent
        }
        return tax
    }
}
