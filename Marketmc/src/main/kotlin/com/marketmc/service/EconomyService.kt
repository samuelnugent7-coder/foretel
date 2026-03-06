package com.marketmc.service

import com.marketmc.config.PluginConfig
import com.marketmc.data.DataRepository
import com.marketmc.model.Company
import kotlin.math.min

class EconomyService(
    private val config: PluginConfig,
    private val repository: DataRepository
) {
    private var lastBoostAt = 0L

    fun ensureBaseline() {
        val balance = repository.getEconomyBalance()
        if (balance <= 0.0) {
            repository.setEconomyBalance(config.startingEconomyBalance)
        }
    }

    fun getBalance(): Double = repository.getEconomyBalance()

    fun deposit(amount: Double) {
        if (amount <= 0) return
        repository.adjustEconomyBalance(amount)
    }

    fun withdraw(amount: Double): Double {
        if (amount <= 0) return 0.0
        val balance = repository.getEconomyBalance()
        val actual = min(balance, amount)
        repository.adjustEconomyBalance(-actual)
        return actual
    }

    fun performMarketBoost(requestedPercent: Double): Double {
        val cooldownMillis = config.boostCooldownSeconds * 1000L
        val now = System.currentTimeMillis()
        if (now - lastBoostAt < cooldownMillis) {
            throw IllegalStateException("Boost cooldown active")
        }
        val balance = repository.getEconomyBalance()
        if (balance <= 0.0) {
            return 0.0
        }
        val percent = requestedPercent.coerceIn(0.01, config.maxBoostPercent)
        val amount = balance * percent
        repository.adjustEconomyBalance(-amount)
        lastBoostAt = now
        return amount
    }

    fun remainingBoostCooldown(): Long {
        val cooldownMillis = config.boostCooldownSeconds * 1000L
        val elapsed = System.currentTimeMillis() - lastBoostAt
        return (cooldownMillis - elapsed).coerceAtLeast(0L)
    }

    fun applyStoreKickback(revenue: Double) {
        if (revenue <= 0) return
        val kickback = revenue * config.storeKickbackPercent
        repository.adjustEconomyBalance(kickback)
    }

    fun supportCompany(company: Company): Double {
        val balance = repository.getEconomyBalance()
        if (balance <= 0.0) {
            return 0.0
        }
        val payout = min(balance * config.treasurySupportPercent, balance)
        company.treasury += payout
        repository.adjustEconomyBalance(-payout)
        repository.updateCompany(company)
        return payout
    }
}
