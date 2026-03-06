package com.marketmc.service

import com.marketmc.config.PluginConfig
import com.marketmc.model.Company
import com.marketmc.model.CompanySignalSnapshot
import com.marketmc.model.MacroSnapshot
import com.marketmc.model.MarketSignalResult
import com.marketmc.model.ShockSnapshot
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.collections.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MarketSignalService(private val config: PluginConfig) {

    private val random = ThreadLocalRandom.current()
    private val momentumWindow = config.priceHistoryWindow.coerceAtLeast(3)
    private val momentumMap = ConcurrentHashMap<UUID, ArrayDeque<Double>>()
    private val companySignals = ConcurrentHashMap<UUID, CompanySignalSnapshot>()
    private val sectorShocks = ConcurrentHashMap<String, Shock>()

    private var inflation = config.macroInflationBase
    private var liquidity = config.macroLiquidityBase
    private var growth = config.macroGrowthBase
    private var sentiment = 0.0
    private var sentimentTarget = randomSentimentTarget()
    private var macroSnapshot = MacroSnapshot(inflation, liquidity, growth, sentiment)

    fun tick() {
        inflation = wobble(inflation, config.macroInflationBase)
        liquidity = wobble(liquidity, config.macroLiquidityBase)
        growth = wobble(growth, config.macroGrowthBase)
        sentiment = wobbleSentiment(sentiment)
        macroSnapshot = MacroSnapshot(inflation, liquidity, growth, sentiment)
        decayShocks(System.currentTimeMillis())
        maybeSpawnShock()
    }

    fun composeSignal(company: Company, baseDemand: Double): MarketSignalResult {
        val history = momentumMap.computeIfAbsent(company.id) { ArrayDeque() }
        val momentum = if (history.isEmpty()) 0.0 else history.sum() / history.size.toDouble()
        history.addLast(baseDemand)
        if (history.size > momentumWindow) {
            history.removeFirst()
        }

        val macroComponent = ((growth - inflation) + liquidity) * 0.2
        val sentimentComponent = sentiment * 0.05
        val shock = sectorShocks[company.sector] ?: sectorShocks[GLOBAL_SECTOR]
        val shockComponent = shock?.magnitude ?: 0.0
        val noise = random.nextDouble(-config.macroNoiseStrength, config.macroNoiseStrength) * 0.05
        val adjustedDemand = baseDemand + macroComponent + sentimentComponent + (momentum * 0.25) + shockComponent + noise

        val volatilityFactor = 1.0 + config.macroVolatilityFloor + abs(sentiment) * 0.4
        val causes = mutableListOf<String>()
        if (shock != null) {
            causes += shock.label
        }
        if (abs(momentum) > 0.01) {
            causes += if (momentum > 0) "Positive momentum" else "Profit taking"
        }
        if (abs(macroComponent) > 0.01) {
            causes += if (macroComponent > 0) "Macro growth tailwind" else "Macro slowdown"
        }
        if (abs(sentimentComponent) > 0.005) {
            causes += if (sentimentComponent > 0) "Investor optimism" else "Risk-off mood"
        }
        if (causes.isEmpty()) {
            causes += "Organic order flow"
        }

        return MarketSignalResult(adjustedDemand, volatilityFactor, causes.joinToString(" + "), macroSnapshot)
    }

    fun recordPriceSignal(companyId: UUID, percentChange: Double, cause: String) {
        companySignals[companyId] = CompanySignalSnapshot(percentChange, cause, System.currentTimeMillis())
    }

    fun getCompanySignal(companyId: UUID): CompanySignalSnapshot? = companySignals[companyId]

    fun getMacroSnapshot(): MacroSnapshot = macroSnapshot

    fun getActiveShocks(): List<ShockSnapshot> = sectorShocks.map { (sector, shock) ->
        ShockSnapshot(sector, shock.label, shock.magnitude, shock.expiresAt)
    }

    private fun wobble(current: Double, anchor: Double): Double {
        val pull = (anchor - current) * 0.15
        val noise = random.nextDouble(-config.macroNoiseStrength, config.macroNoiseStrength) * 0.05
        return current + pull + noise
    }

    private fun wobbleSentiment(current: Double): Double {
        if (abs(sentimentTarget - current) < 0.05) {
            sentimentTarget = randomSentimentTarget()
        }
        val pull = (sentimentTarget - current) * 0.1
        val noise = random.nextDouble(-0.02, 0.02)
        val next = current + pull + noise
        return min(config.sentimentMax, max(config.sentimentMin, next))
    }

    private fun randomSentimentTarget(): Double {
        return random.nextDouble(config.sentimentMin, config.sentimentMax)
    }

    private fun maybeSpawnShock() {
        val now = System.currentTimeMillis()
        val maxShock = config.maxShockPercent
        if (random.nextDouble() < config.supplyShockChance) {
            registerShock(now, -random.nextDouble(maxShock * 0.5, maxShock), "Supply crunch", allowSpill = true)
        }
        if (random.nextDouble() < config.demandShockChance) {
            registerShock(now, random.nextDouble(maxShock * 0.5, maxShock), "Demand frenzy", allowSpill = true)
        }
    }

    fun pushShock(sector: String, magnitude: Double, label: String, durationMillis: Long = defaultDuration()) {
        val normalized = if (sector.isBlank()) GLOBAL_SECTOR else sector
        placeShock(normalized, magnitude, label, System.currentTimeMillis(), durationMillis, allowSpill = false)
    }

    private fun registerShock(now: Long, magnitude: Double, label: String, allowSpill: Boolean) {
        val sectors = config.allowedSectors.ifEmpty { listOf(GLOBAL_SECTOR) }
        val sector = sectors[random.nextInt(sectors.size)]
        placeShock(sector, magnitude, label, now, defaultDuration(), allowSpill && sectors.size > 1)
    }

    private fun placeShock(
        sector: String,
        magnitude: Double,
        label: String,
        now: Long,
        duration: Long,
        allowSpill: Boolean
    ) {
        sectorShocks[sector] = Shock(magnitude, now + duration, label)
        val sectors = config.allowedSectors.ifEmpty { listOf(GLOBAL_SECTOR) }
        if (allowSpill && random.nextDouble() < config.sectorCorrelation) {
            val correlated = sectors[random.nextInt(sectors.size)]
            sectorShocks[correlated] = Shock(magnitude * 0.5, now + duration / 2, "$label spillover")
        }
        if (allowSpill && random.nextDouble() < 0.2) {
            sectorShocks[GLOBAL_SECTOR] = Shock(magnitude * 0.35, now + duration / 2, "$label (global)")
        }
    }

    private fun defaultDuration(): Long = max(20000L, config.tradingTickIntervalSeconds * 1000L * 4)

    private fun decayShocks(now: Long) {
        sectorShocks.entries.removeIf { it.value.expiresAt <= now }
    }

    private data class Shock(val magnitude: Double, val expiresAt: Long, val label: String)

    companion object {
        private const val GLOBAL_SECTOR = "*"
    }
}
