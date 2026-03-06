package com.marketmc.data

import com.marketmc.model.Company
import com.marketmc.model.ExploitIncident
import com.marketmc.model.MarketHistoryRecord
import com.marketmc.model.MarketingCampaign
import com.marketmc.model.Shareholder
import com.marketmc.model.StoreListing
import com.marketmc.model.StoreSale
import com.marketmc.model.TransactionRecord
import java.util.UUID

interface DataRepository {
    fun fetchCompanies(): List<Company>

    fun fetchCompanyByName(name: String): Company?

    fun insertCompany(company: Company)

    fun updateCompany(company: Company)

    fun updateCompanyPrice(companyId: UUID, price: Double, marketCap: Double, demandScore: Double)

    fun fetchShareholder(playerId: UUID, companyId: UUID): Shareholder?

    fun fetchShareholders(playerId: UUID): List<Shareholder>

    fun fetchCompanyShareholders(companyId: UUID): List<Shareholder>

    fun upsertShareholder(shareholder: Shareholder)

    fun deleteShareholder(playerId: UUID, companyId: UUID)

    fun insertTransaction(record: TransactionRecord)

    fun insertMarketHistory(companyId: UUID, price: Double, netDemand: Double, cause: String?)

    fun fetchRecentMarketHistory(companyId: UUID, limit: Int = 12): List<MarketHistoryRecord>

    fun fetchMarketHistorySince(companyId: UUID, since: Long): List<MarketHistoryRecord>

    fun insertStoreListing(listing: StoreListing)

    fun updateStoreListing(listing: StoreListing)

    fun deleteStoreListing(listingId: UUID)

    fun fetchListings(companyId: UUID): List<StoreListing>

    fun insertStoreSale(sale: StoreSale)

    fun getEconomyBalance(): Double

    fun setEconomyBalance(balance: Double)

    fun adjustEconomyBalance(delta: Double): Double

    fun recordExploitIncident(incident: ExploitIncident)

    fun fetchExploitIncidents(limit: Int = 100): List<ExploitIncident>

    fun fetchExploitIncidentsForPlayer(playerId: UUID, limit: Int = 25): List<ExploitIncident>

    fun clearExploitIncidents(playerId: UUID)

    fun insertMarketingCampaign(campaign: MarketingCampaign)

    fun removeMarketingCampaign(campaignId: UUID)

    fun fetchActiveMarketingCampaigns(currentTime: Long = System.currentTimeMillis()): List<MarketingCampaign>
}
