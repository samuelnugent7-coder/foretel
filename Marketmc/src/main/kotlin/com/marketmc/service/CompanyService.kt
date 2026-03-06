package com.marketmc.service

import com.marketmc.MarketMCPlugin
import com.marketmc.data.DataRepository
import com.marketmc.model.Company
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CompanyService(
    private val plugin: MarketMCPlugin,
    private val repository: DataRepository
) {
    private val companyCache = ConcurrentHashMap<UUID, Company>()
    private val nameIndex = ConcurrentHashMap<String, UUID>()

    fun cacheCompanies(companies: List<Company>) {
        companyCache.clear()
        nameIndex.clear()
        companies.forEach { company ->
            companyCache[company.id] = company
            nameIndex[company.name.lowercase(Locale.ROOT)] = company.id
        }
        plugin.logger.info("Loaded ${companies.size} companies into cache.")
    }

    fun getCompanyByName(name: String): Company? {
        val key = name.lowercase(Locale.ROOT)
        val cachedId = nameIndex[key]
        if (cachedId != null) {
            companyCache[cachedId]?.let { return it }
        }
        val fromDb = repository.fetchCompanyByName(name) ?: return null
        companyCache[fromDb.id] = fromDb
        nameIndex[fromDb.name.lowercase(Locale.ROOT)] = fromDb.id
        return fromDb
    }

    fun getCompany(id: UUID): Company? = companyCache[id]

    fun getCompanies(): Collection<Company> = companyCache.values

    fun createCompany(
        owner: UUID,
        name: String,
        totalShares: Long,
        sharePrice: Double,
        sector: String,
        buyoutPrice: Double = 0.0
    ): Company {
        val id = UUID.randomUUID()
        val now = System.currentTimeMillis()
        val company = Company(
            id = id,
            owner = owner,
            name = name,
            sector = sector,
            totalShares = totalShares,
            availableShares = totalShares,
            sharePrice = sharePrice,
            marketCap = sharePrice * totalShares,
            treasury = 0.0,
            storeRevenue = 0.0,
            demandScore = 0.0,
            volatility = 1.0,
            unitsSold = 0L,
            createdAt = now,
            revenueGrowth = 0.0,
            buyoutPrice = buyoutPrice.coerceAtLeast(0.0),
            storeSalePercent = 0.0,
            lastSaleAnnouncementAt = 0L
        )
        repository.insertCompany(company)
        companyCache[id] = company
        nameIndex[name.lowercase(Locale.ROOT)] = id
        return company
    }

    fun updateCompany(company: Company) {
        repository.updateCompany(company)
        companyCache[company.id] = company
        nameIndex[company.name.lowercase(Locale.ROOT)] = company.id
    }

    fun updateMarketMetrics(company: Company, newPrice: Double, netDemand: Double) {
        company.sharePrice = newPrice
        company.marketCap = newPrice * company.totalShares
        company.demandScore = netDemand
        repository.updateCompanyPrice(company.id, newPrice, company.marketCap, netDemand)
        companyCache[company.id] = company
    }

    fun updateAvailableShares(company: Company, availableShares: Long) {
        company.availableShares = availableShares
        updateCompany(company)
    }

    fun updateStoreStats(company: Company, revenueDelta: Double, unitsSoldDelta: Long) {
        company.storeRevenue += revenueDelta
        company.unitsSold += unitsSoldDelta
        updateCompany(company)
    }

    fun updateBuyoutPrice(company: Company, price: Double) {
        company.buyoutPrice = price.coerceAtLeast(0.0)
        updateCompany(company)
    }
}
