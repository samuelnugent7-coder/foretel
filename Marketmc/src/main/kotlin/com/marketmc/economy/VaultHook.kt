package com.marketmc.economy

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.java.JavaPlugin

class VaultHook(private val plugin: JavaPlugin) {
    private var economy: Economy? = null

    fun hook(): Boolean {
        if (plugin.server.pluginManager.getPlugin("Vault") == null) {
            return false
        }
        val registration: RegisteredServiceProvider<Economy>? = Bukkit.getServicesManager().getRegistration(Economy::class.java)
        val provider = registration?.provider ?: return false
        economy = provider
        return true
    }

    fun unhook() {
        economy = null
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        val provider = economy ?: return false
        return provider.withdrawPlayer(player, amount).transactionSuccess()
    }

    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        val provider = economy ?: return false
        return provider.depositPlayer(player, amount).transactionSuccess()
    }

    fun getBalance(player: OfflinePlayer): Double {
        val provider = economy ?: return 0.0
        return provider.getBalance(player)
    }

    fun getEconomy(): Economy? = economy
}
