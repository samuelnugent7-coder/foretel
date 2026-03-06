package com.marketmc.data

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.marketmc.MarketMCPlugin
import com.marketmc.model.Company
import com.marketmc.model.ExploitIncident
import com.marketmc.model.MarketHistoryRecord
import com.marketmc.model.Shareholder
import com.marketmc.model.StoreListing
import com.marketmc.model.StoreSale
import com.marketmc.model.TransactionRecord
import com.marketmc.model.MarketingCampaign
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.logging.Level
import kotlin.concurrent.read
import kotlin.concurrent.write

class FileDataRepository(
    private val plugin: MarketMCPlugin,
    directoryName: String,
    private val defaultEconomyBalance: Double
) : DataRepository {

    private val lock = ReentrantReadWriteLock()
    private val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    private val storageDir: File
    private val storageFile: File

    private val companies = mutableMapOf<UUID, Company>()
    private val nameIndex = mutableMapOf<String, UUID>()
    private val shareholders = mutableMapOf<String, Shareholder>()
    private val storeListings = mutableMapOf<UUID, StoreListing>()
    private val storeSales = mutableListOf<StoreSale>()
    private val transactions = mutableListOf<TransactionRecord>()
    private val marketHistory = mutableListOf<MarketHistoryRecord>()
    private val exploitIncidents = mutableListOf<ExploitIncident>()
    private val marketingCampaigns = mutableListOf<MarketingCampaign>()

    private var incidentCounter = 0L
    private var economyBalance = defaultEconomyBalance
    private var economyInitialized = false

    init {
        plugin.dataFolder.mkdirs()
        storageDir = File(plugin.dataFolder, directoryName).apply { mkdirs() }
        storageFile = File(storageDir, "storage.json")
        load()
    }

    override fun fetchCompanies(): List<Company> = lock.read {
        companies.values.map { it.clone() }
    }

    override fun fetchCompanyByName(name: String): Company? = lock.read {
        val id = nameIndex[name.lowercase(Locale.ROOT)]
        val company = if (id != null) companies[id] else companies.values.firstOrNull {
            it.name.equals(name, ignoreCase = true)
        }
        company?.clone()
    }

    override fun insertCompany(company: Company) = lock.write {
        companies[company.id] = company.clone()
        nameIndex[company.name.lowercase(Locale.ROOT)] = company.id
        save()
    }

    override fun updateCompany(company: Company) = lock.write {
        companies[company.id] = company.clone()
        nameIndex[company.name.lowercase(Locale.ROOT)] = company.id
        save()
    }

    override fun updateCompanyPrice(companyId: UUID, price: Double, marketCap: Double, demandScore: Double) = lock.write {
        val existing = companies[companyId] ?: return@write
        val updated = existing.clone().apply {
            sharePrice = price
            this.marketCap = marketCap
            this.demandScore = demandScore
        }
        companies[companyId] = updated
        save()
    }

    override fun fetchShareholder(playerId: UUID, companyId: UUID): Shareholder? = lock.read {
        shareholders[shareholderKey(playerId, companyId)]?.clone()
    }

    override fun fetchShareholders(playerId: UUID): List<Shareholder> = lock.read {
        shareholders.values.filter { it.playerId == playerId }.map { it.clone() }
    }

    override fun fetchCompanyShareholders(companyId: UUID): List<Shareholder> = lock.read {
        shareholders.values.filter { it.companyId == companyId }.map { it.clone() }
    }

    override fun upsertShareholder(shareholder: Shareholder) = lock.write {
        shareholders[shareholderKey(shareholder.playerId, shareholder.companyId)] = shareholder.clone()
        save()
    }

    override fun deleteShareholder(playerId: UUID, companyId: UUID) = lock.write {
        shareholders.remove(shareholderKey(playerId, companyId))
        save()
    }

    override fun insertTransaction(record: TransactionRecord) = lock.write {
        transactions += record.copy()
        save()
    }

    override fun insertMarketHistory(companyId: UUID, price: Double, netDemand: Double, cause: String?) = lock.write {
        marketHistory += MarketHistoryRecord(companyId, price, netDemand, cause, System.currentTimeMillis())
        save()
    }

    override fun fetchRecentMarketHistory(companyId: UUID, limit: Int): List<MarketHistoryRecord> = lock.read {
        marketHistory.asReversed()
            .asSequence()
            .filter { it.companyId == companyId }
            .take(limit)
            .map { it.copy() }
            .toList()
    }

    override fun fetchMarketHistorySince(companyId: UUID, since: Long): List<MarketHistoryRecord> = lock.read {
        marketHistory.asSequence()
            .filter { it.companyId == companyId && it.timestamp >= since }
            .sortedBy { it.timestamp }
            .map { it.copy() }
            .toList()
    }

    override fun insertStoreListing(listing: StoreListing) = lock.write {
        storeListings[listing.id] = listing.copy()
        save()
    }

    override fun updateStoreListing(listing: StoreListing) = lock.write {
        storeListings[listing.id] = listing.copy()
        save()
    }

    override fun deleteStoreListing(listingId: UUID) = lock.write {
        storeListings.remove(listingId)
        save()
    }

    override fun fetchListings(companyId: UUID): List<StoreListing> = lock.read {
        storeListings.values.filter { it.companyId == companyId }.map { it.copy() }
    }

    override fun insertStoreSale(sale: StoreSale) = lock.write {
        storeSales += sale.copy()
        save()
    }

    override fun getEconomyBalance(): Double = lock.read { economyBalance }

    override fun setEconomyBalance(balance: Double) = lock.write {
        economyBalance = balance
        save()
    }

    override fun adjustEconomyBalance(delta: Double): Double = lock.write {
        economyBalance += delta
        save()
        economyBalance
    }

    override fun recordExploitIncident(incident: ExploitIncident) = lock.write {
        val assignedId = incident.id ?: ++incidentCounter
        exploitIncidents += incident.copy(id = assignedId)
        save()
    }

    override fun fetchExploitIncidents(limit: Int): List<ExploitIncident> = lock.read {
        exploitIncidents.asReversed().take(limit).map { it.copy() }
    }

    override fun fetchExploitIncidentsForPlayer(playerId: UUID, limit: Int): List<ExploitIncident> = lock.read {
        exploitIncidents.asReversed()
            .asSequence()
            .filter { it.playerId == playerId }
            .take(limit)
            .map { it.copy() }
            .toList()
    }

    override fun clearExploitIncidents(playerId: UUID) = lock.write {
        exploitIncidents.removeIf { it.playerId == playerId }
        save()
    }

    override fun insertMarketingCampaign(campaign: MarketingCampaign) = lock.write {
        marketingCampaigns += campaign.copy()
        save()
    }

    override fun removeMarketingCampaign(campaignId: UUID) = lock.write {
        marketingCampaigns.removeIf { it.id == campaignId }
        save()
    }

    override fun fetchActiveMarketingCampaigns(currentTime: Long): List<MarketingCampaign> = lock.read {
        marketingCampaigns.filter { it.expiresAt > currentTime }.map { it.copy() }
    }

    private fun load() {
        if (!storageFile.exists()) {
            save()
            return
        }
        try {
            val snapshot = mapper.readValue(storageFile, PersistedState::class.java)
            companies.clear()
            nameIndex.clear()
            shareholders.clear()
            storeListings.clear()
            storeSales.clear()
            transactions.clear()
            marketHistory.clear()
            exploitIncidents.clear()
            marketingCampaigns.clear()

            snapshot.companies.forEach {
                companies[it.id] = it.clone()
                nameIndex[it.name.lowercase(Locale.ROOT)] = it.id
            }
            snapshot.shareholders.forEach {
                shareholders[shareholderKey(it.playerId, it.companyId)] = it.clone()
            }
            snapshot.storeListings.forEach { storeListings[it.id] = it.copy() }
            snapshot.storeSales.forEach { storeSales += it.copy() }
            snapshot.transactions.forEach { transactions += it.copy() }
            snapshot.marketHistory.forEach { marketHistory += it.copy() }
            snapshot.exploitIncidents.forEach { exploitIncidents += it.copy() }
            snapshot.marketingCampaigns.forEach { marketingCampaigns += it.copy() }

            var needsSave = false
            economyBalance = snapshot.economyBalance
            economyInitialized = snapshot.economyInitialized
            if (!economyInitialized) {
                economyBalance = defaultEconomyBalance
                economyInitialized = true
                needsSave = true
            }
            val maxExistingId = exploitIncidents.maxOfOrNull { it.id ?: 0L } ?: 0L
            incidentCounter = maxOf(snapshot.incidentCounter, maxExistingId)
            if (needsSave) {
                save()
            }
        } catch (ex: IOException) {
            plugin.logger.log(Level.SEVERE, "Failed to load MarketMC storage", ex)
        }
    }

    private fun save() {
        try {
            val snapshot = PersistedState(
                companies = companies.values.map { it.clone() },
                shareholders = shareholders.values.map { it.clone() },
                storeListings = storeListings.values.map { it.copy() },
                storeSales = storeSales.map { it.copy() },
                transactions = transactions.map { it.copy() },
                marketHistory = marketHistory.map { it.copy() },
                exploitIncidents = exploitIncidents.map { it.copy() },
                marketingCampaigns = marketingCampaigns.map { it.copy() },
                incidentCounter = incidentCounter,
                economyBalance = economyBalance,
                economyInitialized = economyInitialized
            )
            mapper.writeValue(storageFile, snapshot)
        } catch (ex: IOException) {
            plugin.logger.log(Level.SEVERE, "Failed to save MarketMC storage", ex)
        }
    }

    private fun shareholderKey(playerId: UUID, companyId: UUID): String = "$playerId:$companyId"

    private fun Company.clone(): Company = Company(
        id = id,
        owner = owner,
        name = name,
        sector = sector,
        totalShares = totalShares,
        availableShares = availableShares,
        sharePrice = sharePrice,
        marketCap = marketCap,
        treasury = treasury,
        storeRevenue = storeRevenue,
        demandScore = demandScore,
        volatility = volatility,
        unitsSold = unitsSold,
        createdAt = createdAt,
        revenueGrowth = revenueGrowth,
        buyoutPrice = buyoutPrice,
        storeSalePercent = storeSalePercent,
        lastSaleAnnouncementAt = lastSaleAnnouncementAt
    )

    private fun Shareholder.clone(): Shareholder = Shareholder(
        playerId = playerId,
        companyId = companyId,
        sharesOwned = sharesOwned,
        avgBuyPrice = avgBuyPrice,
        lastTradeAt = lastTradeAt
    )

    private data class PersistedState(
        val companies: List<Company> = emptyList(),
        val shareholders: List<Shareholder> = emptyList(),
        val storeListings: List<StoreListing> = emptyList(),
        val storeSales: List<StoreSale> = emptyList(),
        val transactions: List<TransactionRecord> = emptyList(),
        val marketHistory: List<MarketHistoryRecord> = emptyList(),
        val exploitIncidents: List<ExploitIncident> = emptyList(),
        val marketingCampaigns: List<MarketingCampaign> = emptyList(),
        val incidentCounter: Long = 0,
        val economyBalance: Double = 0.0,
        val economyInitialized: Boolean = false
    )
}
