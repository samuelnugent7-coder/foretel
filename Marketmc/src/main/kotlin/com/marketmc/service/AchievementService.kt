package com.marketmc.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.marketmc.MarketMCPlugin
import com.marketmc.config.PluginConfig
import com.marketmc.model.AchievementDefinition
import com.marketmc.model.AchievementTrigger
import com.marketmc.model.Company
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.logging.Level
import kotlin.concurrent.read
import kotlin.concurrent.write

class AchievementService(
    private val plugin: MarketMCPlugin,
    config: PluginConfig
) {

    private val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    private val storageFile = File(plugin.dataFolder, "achievements.json")
    private val lock = ReentrantReadWriteLock()
    private val progress = ConcurrentHashMap<UUID, PlayerAchievementProgress>()

    @Volatile
    private var enabled: Boolean = config.achievementsEnabled

    @Volatile
    private var definitions: Map<String, AchievementDefinition> = config.achievementDefinitions.associateBy { it.id }

    init {
        load()
        pruneUnknownDefinitions()
    }

    fun reload(config: PluginConfig) {
        enabled = config.achievementsEnabled
        definitions = config.achievementDefinitions.associateBy { it.id }
        pruneUnknownDefinitions()
    }

    fun recordSharesTraded(playerId: UUID, shares: Long) {
        if (!enabled || shares <= 0) return
        val total = mutate(playerId) { record ->
            record.sharesTraded += shares
            record.sharesTraded.toDouble()
        }
        evaluateThresholds(playerId, AchievementTrigger.SHARES_TRADED, total)
    }

    fun recordMarketingSpend(playerId: UUID, amount: Double) {
        if (!enabled || amount <= 0.0) return
        val total = mutate(playerId) { record ->
            record.marketingSpend += amount
            record.marketingSpend
        }
        evaluateThresholds(playerId, AchievementTrigger.MARKETING_SPEND, total)
    }

    fun recordStorePurchase(playerId: UUID, units: Int) {
        if (!enabled || units <= 0) return
        val total = mutate(playerId) { record ->
            record.storePurchases += units.toLong()
            record.storePurchases.toDouble()
        }
        evaluateThresholds(playerId, AchievementTrigger.STORE_PURCHASES, total)
    }

    fun evaluateCompany(company: Company) {
        if (!enabled) return
        val value = mutate(company.owner) { record ->
            if (company.marketCap > record.topMarketCap) {
                record.topMarketCap = company.marketCap
            }
            record.topMarketCap
        }
        evaluateThresholds(company.owner, AchievementTrigger.COMPANY_MARKET_CAP, value)
    }

    fun getUnlocked(playerId: UUID): Set<String> = lock.read {
        progress[playerId]?.unlocked?.toSet() ?: emptySet()
    }

    private fun mutate(playerId: UUID, block: (PlayerAchievementProgress) -> Double): Double {
        return lock.write {
            val record = progress.computeIfAbsent(playerId) { PlayerAchievementProgress() }
            val result = block(record)
            saveInternal()
            result
        }
    }

    private fun evaluateThresholds(playerId: UUID, trigger: AchievementTrigger, value: Double) {
        val unlocked = mutableListOf<AchievementDefinition>()
        lock.write {
            val record = progress[playerId] ?: return
            definitions.values
                .asSequence()
                .filter { it.trigger == trigger }
                .filter { !record.unlocked.contains(it.id) }
                .filter { value >= it.threshold }
                .forEach {
                    record.unlocked += it.id
                    unlocked += it
                }
            if (unlocked.isNotEmpty()) {
                saveInternal()
            }
        }
        unlocked.forEach { notifyPlayer(playerId, it) }
    }

    private fun notifyPlayer(playerId: UUID, definition: AchievementDefinition) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            val player = Bukkit.getPlayer(playerId)
            if (player != null && player.isOnline) {
                player.sendMessage(
                    "${ChatColor.GOLD}Achievement unlocked: ${definition.name}${ChatColor.GRAY} - ${definition.description}"
                )
            }
        })
    }

    private fun pruneUnknownDefinitions() {
        lock.write {
            val activeIds = definitions.keys
            progress.values.forEach { it.unlocked.retainAll(activeIds) }
            saveInternal()
        }
    }

    private fun load() {
        if (!storageFile.exists()) {
            storageFile.parentFile?.mkdirs()
            return
        }
        try {
            val type = object : TypeReference<Map<String, PlayerAchievementProgress>>() {}
            val raw: Map<String, PlayerAchievementProgress> = mapper.readValue(storageFile, type)
            lock.write {
                progress.clear()
                raw.forEach { (key, value) ->
                    runCatching { UUID.fromString(key) }.getOrNull()?.let { progress[it] = value }
                }
            }
        } catch (ex: IOException) {
            plugin.logger.log(Level.SEVERE, "Failed to load achievements", ex)
        }
    }

    private fun saveInternal() {
        try {
            val payload = progress.mapKeys { it.key.toString() }
            mapper.writeValue(storageFile, payload)
        } catch (ex: IOException) {
            plugin.logger.log(Level.SEVERE, "Failed to save achievements", ex)
        }
    }

    data class PlayerAchievementProgress(
        var sharesTraded: Long = 0,
        var marketingSpend: Double = 0.0,
        var storePurchases: Long = 0,
        var topMarketCap: Double = 0.0,
        val unlocked: MutableSet<String> = mutableSetOf()
    )
}