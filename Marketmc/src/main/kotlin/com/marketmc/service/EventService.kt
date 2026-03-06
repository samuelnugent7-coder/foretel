package com.marketmc.service

import com.marketmc.MarketMCPlugin
import com.marketmc.config.EventDefinition
import com.marketmc.config.PluginConfig
import com.marketmc.data.DataRepository
import com.marketmc.model.Company
import com.marketmc.model.MarketEvent
import java.util.Random
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class EventService(
    private val plugin: MarketMCPlugin,
    private val config: PluginConfig,
    private val companyService: CompanyService,
    private val repository: DataRepository
) {
    private val random = Random()
    private val activeEvents = ConcurrentHashMap<UUID, MarketEvent>()

    fun triggerRandomEvent() {
        if (config.eventDefinitions.isEmpty()) {
            return
        }
        val definitions = config.eventDefinitions.values.toList()
        val definition = definitions[random.nextInt(definitions.size)]
        applyEvent(definition)
    }

    fun applyEvent(definition: EventDefinition) {
        val expiresAt = System.currentTimeMillis() + config.eventDurationMinutes * 60L * 1000L
        val sectors = definition.sectors.toSet()
        val targets = mutableListOf<Company>()
        when {
            sectors.contains("random") -> {
                val companies = companyService.getCompanies().toList()
                if (companies.isNotEmpty()) {
                    targets += companies[random.nextInt(companies.size)]
                }
            }
            sectors.contains("*") -> targets += companyService.getCompanies()
            else -> companyService.getCompanies().forEach { company ->
                if (sectors.contains(company.sector)) {
                    targets += company
                }
            }
        }

        targets.forEach { company ->
            val newPrice = (company.sharePrice * definition.multiplier).coerceAtLeast(0.01)
            companyService.updateMarketMetrics(company, newPrice, company.demandScore)
            repository.insertMarketHistory(company.id, newPrice, 0.0, definition.key)
            activeEvents[company.id] = MarketEvent(
                key = definition.key,
                description = definition.description,
                multiplier = definition.multiplier,
                sectors = sectors,
                expiresAt = expiresAt
            )
        }
        if (targets.isNotEmpty()) {
            val message = "[Market] ${definition.description}"
            plugin.server.broadcast(message, "marketmc.company.trade")
        }
    }

    fun cleanExpiredEvents() {
        val now = System.currentTimeMillis()
        activeEvents.entries.removeIf { (_, event) -> event.isExpired(now) }
    }

    fun getActiveEvents(): List<MarketEvent> {
        cleanExpiredEvents()
        return activeEvents.values.toList()
    }
}
