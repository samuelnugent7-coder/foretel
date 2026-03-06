package com.marketmc.data

import com.marketmc.MarketMCPlugin
import com.marketmc.database.DatabaseManager
import com.marketmc.model.Company
import com.marketmc.model.ExploitIncident
import com.marketmc.model.MarketHistoryRecord
import com.marketmc.model.MarketingCampaign
import com.marketmc.model.Shareholder
import com.marketmc.model.StoreListing
import com.marketmc.model.StoreSale
import com.marketmc.model.TransactionRecord
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
import java.util.UUID
import java.util.logging.Level

class SqlDataRepository(
    private val plugin: MarketMCPlugin,
    private val databaseManager: DatabaseManager,
    private val defaultEconomyBalance: Double
) : DataRepository {

    override fun fetchCompanies(): List<Company> {
        val companies = mutableListOf<Company>()
        val sql = "SELECT * FROM companies"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            companies += mapCompany(rs)
                        }
                    }
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to fetch companies", ex)
        }
        return companies
    }

    override fun fetchCompanyByName(name: String): Company? {
        val sql = "SELECT * FROM companies WHERE name = ?"
        return try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, name)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            return mapCompany(rs)
                        }
                    }
                }
            }
            null
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to fetch company", ex)
            null
        }
    }

    override fun insertCompany(company: Company) {
        val sql = """
            INSERT INTO companies (
                id, owner, name, sector, total_shares, available_shares, share_price,
                market_cap, treasury, store_revenue, demand_score, volatility,
                units_sold, created_at, revenue_growth, buyout_price, store_sale_percent,
                last_sale_announcement_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    fillCompanyStatement(company, statement)
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to insert company", ex)
        }
    }

    override fun updateCompany(company: Company) {
        val sql = """
            UPDATE companies SET owner=?, sector=?, total_shares=?, available_shares=?, share_price=?,
                market_cap=?, treasury=?, store_revenue=?, demand_score=?, volatility=?, units_sold=?, revenue_growth=?, buyout_price=?,
                store_sale_percent=?, last_sale_announcement_at=?
            WHERE id=?
        """.trimIndent()
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, company.owner.toString())
                    statement.setString(2, company.sector)
                    statement.setLong(3, company.totalShares)
                    statement.setLong(4, company.availableShares)
                    statement.setDouble(5, company.sharePrice)
                    statement.setDouble(6, company.marketCap)
                    statement.setDouble(7, company.treasury)
                    statement.setDouble(8, company.storeRevenue)
                    statement.setDouble(9, company.demandScore)
                    statement.setDouble(10, company.volatility)
                    statement.setLong(11, company.unitsSold)
                    statement.setDouble(12, company.revenueGrowth)
                    statement.setDouble(13, company.buyoutPrice)
                    statement.setDouble(14, company.storeSalePercent)
                    statement.setLong(15, company.lastSaleAnnouncementAt)
                    statement.setString(16, company.id.toString())
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to update company", ex)
        }
    }

    override fun updateCompanyPrice(companyId: UUID, price: Double, marketCap: Double, demandScore: Double) {
        val sql = "UPDATE companies SET share_price=?, market_cap=?, demand_score=? WHERE id=?"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setDouble(1, price)
                    statement.setDouble(2, marketCap)
                    statement.setDouble(3, demandScore)
                    statement.setString(4, companyId.toString())
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to update price", ex)
        }
    }

    override fun fetchShareholder(playerId: UUID, companyId: UUID): Shareholder? {
        val sql = "SELECT * FROM shareholders WHERE player_id=? AND company_id=?"
        return try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId.toString())
                    statement.setString(2, companyId.toString())
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            return mapShareholder(rs)
                        }
                    }
                }
            }
            null
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to fetch shareholder", ex)
            null
        }
    }

    override fun fetchShareholders(playerId: UUID): List<Shareholder> {
        val sql = "SELECT * FROM shareholders WHERE player_id=?"
        val shareholders = mutableListOf<Shareholder>()
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId.toString())
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            shareholders += mapShareholder(rs)
                        }
                    }
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to fetch shareholder list", ex)
        }
        return shareholders
    }

    override fun fetchCompanyShareholders(companyId: UUID): List<Shareholder> {
        val sql = "SELECT * FROM shareholders WHERE company_id=?"
        val shareholders = mutableListOf<Shareholder>()
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, companyId.toString())
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            shareholders += mapShareholder(rs)
                        }
                    }
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to fetch company shareholders", ex)
        }
        return shareholders
    }

    override fun upsertShareholder(shareholder: Shareholder) {
        val sql = """
            INSERT INTO shareholders (player_id, company_id, shares_owned, avg_buy_price, last_trade_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE shares_owned=?, avg_buy_price=?, last_trade_at=?
        """.trimIndent()
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, shareholder.playerId.toString())
                    statement.setString(2, shareholder.companyId.toString())
                    statement.setLong(3, shareholder.sharesOwned)
                    statement.setDouble(4, shareholder.avgBuyPrice)
                    statement.setLong(5, shareholder.lastTradeAt)
                    statement.setLong(6, shareholder.sharesOwned)
                    statement.setDouble(7, shareholder.avgBuyPrice)
                    statement.setLong(8, shareholder.lastTradeAt)
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to upsert shareholder", ex)
        }
    }

    override fun deleteShareholder(playerId: UUID, companyId: UUID) {
        val sql = "DELETE FROM shareholders WHERE player_id=? AND company_id=?"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId.toString())
                    statement.setString(2, companyId.toString())
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to delete shareholder", ex)
        }
    }

    override fun insertTransaction(record: TransactionRecord) {
        val sql = """
            INSERT INTO transactions (company_id, buyer, seller, shares, price, tax_paid, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, record.companyId.toString())
                    statement.setString(2, record.buyer?.toString())
                    statement.setString(3, record.seller?.toString())
                    statement.setLong(4, record.shares)
                    statement.setDouble(5, record.price)
                    statement.setDouble(6, record.taxPaid)
                    statement.setLong(7, record.timestamp)
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to insert transaction", ex)
        }
    }

    override fun insertMarketHistory(companyId: UUID, price: Double, netDemand: Double, cause: String?) {
        val sql = "INSERT INTO market_history (company_id, price, net_demand, event_key, created_at) VALUES (?, ?, ?, ?, ?)"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, companyId.toString())
                    statement.setDouble(2, price)
                    statement.setDouble(3, netDemand)
                    if (cause == null) {
                        statement.setNull(4, Types.VARCHAR)
                    } else {
                        statement.setString(4, cause)
                    }
                    statement.setLong(5, System.currentTimeMillis())
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to insert market history", ex)
        }
    }

    override fun fetchRecentMarketHistory(companyId: UUID, limit: Int): List<MarketHistoryRecord> {
        val records = mutableListOf<MarketHistoryRecord>()
        val sql = """
            SELECT company_id, price, net_demand, event_key, created_at
            FROM market_history
            WHERE company_id=?
            ORDER BY created_at DESC
            LIMIT ?
        """.trimIndent()
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, companyId.toString())
                    statement.setInt(2, limit)
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            records += MarketHistoryRecord(
                                companyId = UUID.fromString(rs.getString("company_id")),
                                price = rs.getDouble("price"),
                                netDemand = rs.getDouble("net_demand"),
                                cause = rs.getString("event_key"),
                                timestamp = rs.getLong("created_at")
                            )
                        }
                    }
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to fetch market history", ex)
        }
        return records
    }

    override fun fetchMarketHistorySince(companyId: UUID, since: Long): List<MarketHistoryRecord> {
        val records = mutableListOf<MarketHistoryRecord>()
        val sql = """
            SELECT company_id, price, net_demand, event_key, created_at
            FROM market_history
            WHERE company_id=? AND created_at>=?
            ORDER BY created_at ASC
        """.trimIndent()
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, companyId.toString())
                    statement.setLong(2, since)
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            records += MarketHistoryRecord(
                                companyId = UUID.fromString(rs.getString("company_id")),
                                price = rs.getDouble("price"),
                                netDemand = rs.getDouble("net_demand"),
                                cause = rs.getString("event_key"),
                                timestamp = rs.getLong("created_at")
                            )
                        }
                    }
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to fetch timed market history", ex)
        }
        return records
    }

    override fun insertStoreListing(listing: StoreListing) {
        val sql = "INSERT INTO store_listings (id, company_id, item, price, stock, created_at) VALUES (?, ?, ?, ?, ?, ?)"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, listing.id.toString())
                    statement.setString(2, listing.companyId.toString())
                    statement.setString(3, listing.itemSerialized)
                    statement.setDouble(4, listing.price)
                    statement.setInt(5, listing.stock)
                    statement.setLong(6, listing.createdAt)
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to insert store listing", ex)
        }
    }

    override fun updateStoreListing(listing: StoreListing) {
        val sql = "UPDATE store_listings SET price=?, stock=? WHERE id=?"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setDouble(1, listing.price)
                    statement.setInt(2, listing.stock)
                    statement.setString(3, listing.id.toString())
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to update store listing", ex)
        }
    }

    override fun deleteStoreListing(listingId: UUID) {
        val sql = "DELETE FROM store_listings WHERE id=?"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, listingId.toString())
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to delete store listing", ex)
        }
    }

    override fun fetchListings(companyId: UUID): List<StoreListing> {
        val listings = mutableListOf<StoreListing>()
        val sql = "SELECT * FROM store_listings WHERE company_id=?"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, companyId.toString())
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            listings += StoreListing(
                                id = UUID.fromString(rs.getString("id")),
                                companyId = UUID.fromString(rs.getString("company_id")),
                                itemSerialized = rs.getString("item"),
                                price = rs.getDouble("price"),
                                stock = rs.getInt("stock"),
                                createdAt = rs.getLong("created_at")
                            )
                        }
                    }
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to fetch listings", ex)
        }
        return listings
    }

    override fun insertStoreSale(sale: StoreSale) {
        val sql = """
            INSERT INTO store_sales (company_id, listing_id, buyer, units, revenue, profit, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, sale.companyId.toString())
                    statement.setString(2, sale.listingId.toString())
                    statement.setString(3, sale.buyer.toString())
                    statement.setInt(4, sale.units)
                    statement.setDouble(5, sale.revenue)
                    statement.setDouble(6, sale.profit)
                    statement.setLong(7, sale.timestamp)
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to insert store sale", ex)
        }
    }

    override fun getEconomyBalance(): Double {
        val sql = "SELECT balance FROM economy_state WHERE id=1"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            return rs.getDouble("balance")
                        }
                    }
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to fetch economy balance", ex)
        }
        setEconomyBalance(defaultEconomyBalance)
        return defaultEconomyBalance
    }

    override fun setEconomyBalance(balance: Double) {
        val sql = """
            INSERT INTO economy_state (id, balance)
            VALUES (1, ?)
            ON DUPLICATE KEY UPDATE balance=VALUES(balance)
        """.trimIndent()
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setDouble(1, balance)
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to set economy balance", ex)
        }
    }

    override fun adjustEconomyBalance(delta: Double): Double {
        val current = getEconomyBalance()
        val updated = current + delta
        setEconomyBalance(updated)
        return updated
    }

    override fun recordExploitIncident(incident: ExploitIncident) {
        val sql = """
            INSERT INTO exploit_incidents (player_id, company_id, action, reason, severity, details, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, incident.playerId.toString())
                    if (incident.companyId == null) {
                        statement.setNull(2, Types.VARCHAR)
                    } else {
                        statement.setString(2, incident.companyId.toString())
                    }
                    statement.setString(3, incident.action)
                    statement.setString(4, incident.reason)
                    statement.setDouble(5, incident.severity)
                    statement.setString(6, incident.details)
                    statement.setLong(7, incident.createdAt)
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to record exploit incident", ex)
        }
    }

    override fun fetchExploitIncidents(limit: Int): List<ExploitIncident> {
        val incidents = mutableListOf<ExploitIncident>()
        val sql = "SELECT * FROM exploit_incidents ORDER BY created_at DESC LIMIT ?"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setInt(1, limit)
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            incidents += mapIncident(rs)
                        }
                    }
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to fetch exploit incidents", ex)
        }
        return incidents
    }

    override fun fetchExploitIncidentsForPlayer(playerId: UUID, limit: Int): List<ExploitIncident> {
        val incidents = mutableListOf<ExploitIncident>()
        val sql = "SELECT * FROM exploit_incidents WHERE player_id=? ORDER BY created_at DESC LIMIT ?"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId.toString())
                    statement.setInt(2, limit)
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            incidents += mapIncident(rs)
                        }
                    }
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to fetch player exploit incidents", ex)
        }
        return incidents
    }

    override fun clearExploitIncidents(playerId: UUID) {
        val sql = "DELETE FROM exploit_incidents WHERE player_id=?"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId.toString())
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to clear exploit incidents", ex)
        }
    }

    override fun insertMarketingCampaign(campaign: MarketingCampaign) {
        val sql = """
            INSERT INTO marketing_campaigns (id, company_id, sponsor, spend, boost, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, campaign.id.toString())
                    statement.setString(2, campaign.companyId.toString())
                    statement.setString(3, campaign.sponsorId.toString())
                    statement.setDouble(4, campaign.spend)
                    statement.setDouble(5, campaign.boost)
                    statement.setLong(6, campaign.createdAt)
                    statement.setLong(7, campaign.expiresAt)
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to insert marketing campaign", ex)
        }
    }

    override fun removeMarketingCampaign(campaignId: UUID) {
        val sql = "DELETE FROM marketing_campaigns WHERE id=?"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, campaignId.toString())
                    statement.executeUpdate()
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to remove marketing campaign", ex)
        }
    }

    override fun fetchActiveMarketingCampaigns(currentTime: Long): List<MarketingCampaign> {
        val campaigns = mutableListOf<MarketingCampaign>()
        val sql = "SELECT * FROM marketing_campaigns WHERE expires_at > ?"
        try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, currentTime)
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            campaigns += mapCampaign(rs)
                        }
                    }
                }
            }
        } catch (ex: SQLException) {
            plugin.logger.log(Level.SEVERE, "Failed to fetch marketing campaigns", ex)
        }
        return campaigns
    }

    private fun fillCompanyStatement(company: Company, statement: PreparedStatement) {
        statement.setString(1, company.id.toString())
        statement.setString(2, company.owner.toString())
        statement.setString(3, company.name)
        statement.setString(4, company.sector)
        statement.setLong(5, company.totalShares)
        statement.setLong(6, company.availableShares)
        statement.setDouble(7, company.sharePrice)
        statement.setDouble(8, company.marketCap)
        statement.setDouble(9, company.treasury)
        statement.setDouble(10, company.storeRevenue)
        statement.setDouble(11, company.demandScore)
        statement.setDouble(12, company.volatility)
        statement.setLong(13, company.unitsSold)
        statement.setLong(14, company.createdAt)
        statement.setDouble(15, company.revenueGrowth)
        statement.setDouble(16, company.buyoutPrice)
        statement.setDouble(17, company.storeSalePercent)
        statement.setLong(18, company.lastSaleAnnouncementAt)
    }

    private fun mapIncident(rs: ResultSet): ExploitIncident {
        val companyIdRaw = rs.getString("company_id")
        return ExploitIncident(
            id = rs.getLong("id"),
            playerId = UUID.fromString(rs.getString("player_id")),
            companyId = if (companyIdRaw.isNullOrBlank()) null else UUID.fromString(companyIdRaw),
            action = rs.getString("action"),
            reason = rs.getString("reason"),
            severity = rs.getDouble("severity"),
            details = rs.getString("details"),
            createdAt = rs.getLong("created_at")
        )
    }

    private fun mapCompany(rs: ResultSet): Company {
        return Company(
            id = UUID.fromString(rs.getString("id")),
            owner = UUID.fromString(rs.getString("owner")),
            name = rs.getString("name"),
            sector = rs.getString("sector"),
            totalShares = rs.getLong("total_shares"),
            availableShares = rs.getLong("available_shares"),
            sharePrice = rs.getDouble("share_price"),
            marketCap = rs.getDouble("market_cap"),
            treasury = rs.getDouble("treasury"),
            storeRevenue = rs.getDouble("store_revenue"),
            demandScore = rs.getDouble("demand_score"),
            volatility = rs.getDouble("volatility"),
            unitsSold = rs.getLong("units_sold"),
            createdAt = rs.getLong("created_at"),
            revenueGrowth = rs.getDouble("revenue_growth"),
            buyoutPrice = rs.getDouble("buyout_price"),
            storeSalePercent = rs.getDouble("store_sale_percent"),
            lastSaleAnnouncementAt = rs.getLong("last_sale_announcement_at")
        )
    }

    private fun mapShareholder(rs: ResultSet): Shareholder {
        return Shareholder(
            playerId = UUID.fromString(rs.getString("player_id")),
            companyId = UUID.fromString(rs.getString("company_id")),
            sharesOwned = rs.getLong("shares_owned"),
            avgBuyPrice = rs.getDouble("avg_buy_price"),
            lastTradeAt = rs.getLong("last_trade_at")
        )
    }

    private fun mapCampaign(rs: ResultSet): MarketingCampaign {
        return MarketingCampaign(
            id = UUID.fromString(rs.getString("id")),
            companyId = UUID.fromString(rs.getString("company_id")),
            sponsorId = UUID.fromString(rs.getString("sponsor")),
            spend = rs.getDouble("spend"),
            boost = rs.getDouble("boost"),
            createdAt = rs.getLong("created_at"),
            expiresAt = rs.getLong("expires_at")
        )
    }
}

