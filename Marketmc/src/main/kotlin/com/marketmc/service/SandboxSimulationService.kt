package com.marketmc.service

import com.marketmc.MarketMCPlugin
import com.marketmc.config.PluginConfig
import com.marketmc.model.Company
import org.bukkit.command.CommandSender
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SandboxSimulationService(
    private val plugin: MarketMCPlugin,
    private var config: PluginConfig,
    private val companyService: CompanyService
) {

    private val running = AtomicBoolean(false)
    private val canceled = AtomicBoolean(false)
    private val ticksSimulated = AtomicInteger(0)

    @Volatile
    private var lastReport: SandboxReport? = null

    fun reload(newConfig: PluginConfig) {
        config = newConfig
    }

    fun isRunning(): Boolean = running.get()

    fun requestStop() {
        canceled.set(true)
    }

    fun getLastReport(): SandboxReport? = lastReport

    fun runSimulation(sender: CommandSender, requestedMinutes: Int): Boolean {
        if (!config.sandboxEnabled) {
            sendMessage(sender, "Sandbox mode disabled in config.yml")
            return false
        }
        if (!running.compareAndSet(false, true)) {
            sendMessage(sender, "Sandbox simulation already running")
            return false
        }
        canceled.set(false)
        val duration = requestedMinutes.coerceIn(1, config.sandboxMaxDurationMinutes)
        val companies = companyService.getCompanies().map { SandboxCompany(it) }
        if (companies.isEmpty()) {
            sendMessage(sender, "No companies available for sandbox simulation")
            running.set(false)
            return false
        }
        val interval = config.tradingTickIntervalSeconds.coerceAtLeast(1)
        val ticks = duration * 60 / interval * config.sandboxTickAcceleration
        ticksSimulated.set(0)
        val signalService = MarketSignalService(config)
        val startedAt = System.currentTimeMillis()
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            val random = ThreadLocalRandom.current()
            while (ticksSimulated.get() < ticks && !canceled.get()) {
                signalService.tick()
                companies.forEach { simulateCompany(it, signalService, random) }
                ticksSimulated.incrementAndGet()
            }
            val report = SandboxReport(
                startedAt = startedAt,
                durationMinutes = duration,
                ticksSimulated = ticksSimulated.get(),
                companySnapshots = companies.map { it.snapshot() }
            )
            lastReport = report
            running.set(false)
            val message = if (canceled.get()) {
                "Sandbox simulation canceled after ${report.ticksSimulated} ticks"
            } else {
                "Sandbox simulation complete. ${report.companySnapshots.size} companies analyzed."
            }
            sendMessage(sender, message)
        })
        sendMessage(sender, "Sandbox simulation started for $duration minute(s) at x${config.sandboxTickAcceleration} speed")
        return true
    }

    private fun simulateCompany(
        sandboxCompany: SandboxCompany,
        signalService: MarketSignalService,
        random: ThreadLocalRandom
    ) {
        val sim = sandboxCompany.simCompany
        val baseDemand = random.nextDouble(-0.02, 0.02)
        val signal = signalService.composeSignal(sim, baseDemand)
        val priceChange = sim.sharePrice * signal.adjustedDemand * config.volatilityMultiplier * signal.volatilityFactor
        val maxMove = sim.sharePrice * config.maxPriceMovePercent
        val bounded = priceChange.coerceIn(-maxMove, maxMove)
        val newPrice = (sim.sharePrice + bounded).coerceAtLeast(0.01)
        sim.sharePrice = newPrice
        sim.marketCap = newPrice * sim.totalShares
        sandboxCompany.minPrice = min(sandboxCompany.minPrice, newPrice)
        sandboxCompany.maxPrice = max(sandboxCompany.maxPrice, newPrice)
    }

    private fun sendMessage(sender: CommandSender, message: String) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            sender.sendMessage(message)
        })
    }

    data class SandboxReport(
        val startedAt: Long,
        val durationMinutes: Int,
        val ticksSimulated: Int,
        val companySnapshots: List<SandboxCompanySnapshot>
    )

    data class SandboxCompanySnapshot(
        val id: UUID,
        val name: String,
        val startPrice: Double,
        val endPrice: Double,
        val minPrice: Double,
        val maxPrice: Double,
        val absoluteSwing: Double
    )

    private class SandboxCompany(company: Company) {
        val simCompany: Company = Company(
            id = company.id,
            owner = company.owner,
            name = company.name,
            sector = company.sector,
            totalShares = company.totalShares,
            availableShares = company.availableShares,
            sharePrice = company.sharePrice,
            marketCap = company.marketCap,
            treasury = company.treasury,
            storeRevenue = company.storeRevenue,
            demandScore = company.demandScore,
            volatility = company.volatility,
            unitsSold = company.unitsSold,
            createdAt = company.createdAt,
            revenueGrowth = company.revenueGrowth,
            buyoutPrice = company.buyoutPrice
        )

        var minPrice: Double = simCompany.sharePrice
        var maxPrice: Double = simCompany.sharePrice
        private val startPrice: Double = simCompany.sharePrice

        fun snapshot(): SandboxCompanySnapshot {
            return SandboxCompanySnapshot(
                id = simCompany.id,
                name = simCompany.name,
                startPrice = startPrice,
                endPrice = simCompany.sharePrice,
                minPrice = minPrice,
                maxPrice = maxPrice,
                absoluteSwing = abs(simCompany.sharePrice - startPrice)
            )
        }
    }
}