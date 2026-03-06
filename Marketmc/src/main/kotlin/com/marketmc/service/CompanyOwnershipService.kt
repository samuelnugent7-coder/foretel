package com.marketmc.service

import com.marketmc.MarketMCPlugin
import com.marketmc.model.Company
import com.marketmc.economy.VaultHook
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.min

class CompanyOwnershipService(
    private val plugin: MarketMCPlugin,
    private val companyService: CompanyService,
    private val vaultHook: VaultHook
) {

    fun setBuyoutPrice(company: Company, price: Double) {
        company.buyoutPrice = price.coerceAtLeast(0.0)
        companyService.updateCompany(company)
    }

    fun clearBuyoutPrice(company: Company) {
        setBuyoutPrice(company, 0.0)
    }

    fun purchaseCompany(buyer: Player, company: Company): Boolean {
        val liveCompany = companyService.getCompany(company.id) ?: run {
            buyer.sendMessage("${ChatColor.RED}Company data is unavailable. Try again.")
            return false
        }
        val price = liveCompany.buyoutPrice
        if (price <= 0.0) {
            buyer.sendMessage("${ChatColor.RED}${liveCompany.name} is not currently for sale.")
            return false
        }
        if (liveCompany.owner == buyer.uniqueId) {
            buyer.sendMessage("${ChatColor.YELLOW}You already own ${liveCompany.name}.")
            return false
        }
        if (!vaultHook.withdraw(buyer, price)) {
            buyer.sendMessage("${ChatColor.RED}You need ${formatCurrency(price)} to acquire this company.")
            return false
        }
        val previousOwnerId: UUID = liveCompany.owner
        val seller = Bukkit.getOfflinePlayer(previousOwnerId)
        val depositSuccess = vaultHook.deposit(seller, price)
        if (!depositSuccess) {
            vaultHook.deposit(buyer, price)
            buyer.sendMessage("${ChatColor.RED}Transfer failed. You have been refunded.")
            plugin.logger.warning("Failed to deposit buyout payment to ${seller.name ?: previousOwnerId}")
            return false
        }
        liveCompany.owner = buyer.uniqueId
        liveCompany.buyoutPrice = 0.0
        companyService.updateCompany(liveCompany)

        buyer.sendMessage("${ChatColor.GREEN}You acquired ${liveCompany.name} for ${formatCurrency(price)}.")
        if (seller.isOnline) {
            seller.player?.sendMessage("${ChatColor.YELLOW}${buyer.name} bought ${liveCompany.name} for ${formatCurrency(price)}.")
        }
        return true
    }

    fun withdrawTreasury(player: Player, company: Company, amount: Double): Boolean {
        val liveCompany = companyService.getCompany(company.id) ?: run {
            player.sendMessage("${ChatColor.RED}Company data is unavailable. Try again.")
            return false
        }
        if (amount <= 0.0) {
            player.sendMessage("${ChatColor.RED}Enter an amount greater than zero.")
            return false
        }
        val isOwner = liveCompany.owner == player.uniqueId
        if (!isOwner && !player.hasPermission("marketmc.company.treasury")) {
            player.sendMessage("${ChatColor.RED}Only the owner (or those with treasury access) may withdraw funds.")
            return false
        }
        if (liveCompany.treasury <= 0.0) {
            player.sendMessage("${ChatColor.RED}${liveCompany.name} has no treasury funds to withdraw.")
            return false
        }
        val withdrawal = min(amount, liveCompany.treasury)
        if (withdrawal <= 0.0) {
            player.sendMessage("${ChatColor.RED}Nothing is available to withdraw.")
            return false
        }
        val depositSuccess = vaultHook.deposit(player, withdrawal)
        if (!depositSuccess) {
            player.sendMessage("${ChatColor.RED}Could not deposit funds to your balance. Try again later.")
            return false
        }
        liveCompany.treasury -= withdrawal
        companyService.updateCompany(liveCompany)
        val suffix = if (withdrawal < amount) " (capped to available funds)" else ""
        player.sendMessage("${ChatColor.GREEN}Withdrew ${formatCurrency(withdrawal)} from ${liveCompany.name}'s treasury.$suffix")
        return true
    }

    private fun formatCurrency(value: Double): String = "${'$'}" + String.format("%,.2f", value)
}
