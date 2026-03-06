package me.trade.ui

import net.milkbowl.vault.economy.Economy
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.ServicesManager
import org.bukkit.plugin.java.JavaPlugin
import java.text.DecimalFormat

class EconomyBridge(private val plugin: JavaPlugin) {
    private var economy: Economy? = null
    private val formatter = DecimalFormat("#,##0.00")

    val isAvailable: Boolean
        get() = economy != null

    fun hook(): Boolean {
        val provider = plugin.server.servicesManager.getRegistration(Economy::class.java)
        economy = provider?.provider
        return isAvailable
    }

    fun format(amount: Double): String {
        if (!amount.isFinite()) return "N/A"
        return economy?.format(amount) ?: formatter.format(amount)
    }

    fun balance(player: OfflinePlayer): Double {
        return economy?.getBalance(player) ?: 0.0
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0.0) return true
        val eco = economy ?: return false
        val current = eco.getBalance(player)
        if (current + 1e-6 < amount) {
            player.player?.sendMessage("§cYou don't have enough money for that offer. Available: ${format(current)}")
            return false
        }
        val response = eco.withdrawPlayer(player, amount)
        if (!response.transactionSuccess()) {
            player.player?.sendMessage("§cYou don't have enough money for that offer.")
            return false
        }
        return true
    }

    fun deposit(player: OfflinePlayer, amount: Double) {
        if (amount <= 0.0) return
        economy?.depositPlayer(player, amount)
    }
}
