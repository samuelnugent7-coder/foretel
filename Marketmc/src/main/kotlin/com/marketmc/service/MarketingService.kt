package com.marketmc.service

import com.marketmc.MarketMCPlugin
import com.marketmc.config.PluginConfig
import com.marketmc.data.DataRepository
import com.marketmc.economy.VaultHook
import com.marketmc.model.Company
import com.marketmc.model.MarketingCampaign
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class MarketingService(
    private val plugin: MarketMCPlugin,
    private val config: PluginConfig,
    private val repository: DataRepository,
    private val vaultHook: VaultHook,
    private val achievementService: AchievementService
) {

    private val campaigns = ConcurrentHashMap<UUID, MutableList<MarketingCampaign>>()
    private val lock = ReentrantReadWriteLock()

    init {
        reload()
    }

    fun reload() {
        val now = System.currentTimeMillis()
        val active = repository.fetchActiveMarketingCampaigns(now)
        lock.write {
            campaigns.clear()
            active.forEach { appendCampaign(it) }
        }
    }

    fun tick() {
        expireCampaigns()
    }

    fun getBoost(companyId: UUID): Double = lock.read {
        campaigns[companyId]?.sumOf { it.boost } ?: 0.0
    }

    fun getActiveCampaigns(companyId: UUID): List<MarketingCampaign> = lock.read {
        campaigns[companyId]?.map { it.copy() } ?: emptyList()
    }

    fun startCampaign(player: Player, company: Company, spend: Double): CampaignResult {
        if (!config.marketingEnabled) {
            return CampaignResult(false, "Marketing campaigns are disabled.")
        }
        if (spend < config.marketingMinSpend || spend > config.marketingMaxSpend) {
            return CampaignResult(
                false,
                "Spend must be between ${formatCurrency(config.marketingMinSpend)} and ${formatCurrency(config.marketingMaxSpend)}"
            )
        }
        val durationMillis = config.marketingDurationMinutes * 60_000L
        if (!vaultHook.withdraw(player, spend)) {
            return CampaignResult(false, "You cannot afford a campaign of ${formatCurrency(spend)}")
        }
        val boost = (spend / 1000.0) * config.marketingBoostPerThousand
        val now = System.currentTimeMillis()
        val campaign = MarketingCampaign(
            id = UUID.randomUUID(),
            companyId = company.id,
            sponsorId = player.uniqueId,
            spend = spend,
            boost = boost,
            createdAt = now,
            expiresAt = now + durationMillis
        )
        repository.insertMarketingCampaign(campaign)
        lock.write { appendCampaign(campaign) }
        achievementService.recordMarketingSpend(player.uniqueId, spend)
        plugin.logger.info("${player.name} launched marketing for ${company.name} spending ${String.format("%,.0f", spend)}")
        return CampaignResult(true, "Campaign deployed! Estimated demand boost +${String.format("%.3f", boost)}")
    }

    fun expireCampaigns(now: Long = System.currentTimeMillis()) {
        val expired = mutableListOf<UUID>()
        lock.write {
            val iterator = campaigns.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val expiredCampaigns = entry.value.filter { it.expiresAt <= now }
                if (expiredCampaigns.isNotEmpty()) {
                    expired.addAll(expiredCampaigns.map { it.id })
                }
                val survivors = entry.value.filter { it.expiresAt > now }.toMutableList()
                if (survivors.isEmpty()) {
                    iterator.remove()
                } else {
                    entry.setValue(survivors)
                }
            }
        }
        expired.forEach { repository.removeMarketingCampaign(it) }
    }

    private fun appendCampaign(campaign: MarketingCampaign) {
        campaigns.computeIfAbsent(campaign.companyId) { mutableListOf() }.add(campaign)
    }

    private fun formatCurrency(amount: Double): String = String.format("%,.2f", amount)

    data class CampaignResult(val success: Boolean, val message: String)
}