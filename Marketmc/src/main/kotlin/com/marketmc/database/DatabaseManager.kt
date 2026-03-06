package com.marketmc.database

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.Plugin
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.util.Locale

class DatabaseManager(
    private val plugin: Plugin,
    private val config: ConfigurationSection
) {
    private var dataSource: HikariDataSource? = null
    private var mongoClient: MongoClient? = null
    private var mongoDatabaseField: MongoDatabase? = null

    @Throws(SQLException::class)
    fun init() {
        when (config.getString("type", "MYSQL")!!.uppercase(Locale.ROOT)) {
            "MYSQL" -> initMySql()
            "MONGODB" -> initMongo()
            else -> throw IllegalStateException("Unsupported database type: ${config.getString("type")}")
        }
    }

    @Throws(SQLException::class)
    private fun initMySql() {
        val section = config.getConfigurationSection("mysql")
            ?: throw IllegalStateException("MySQL section missing in config")
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://" + section.getString("host", "localhost") + ":" +
                    section.getInt("port", 3306) + "/" + section.getString("database", "marketmc") +
                    "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
            username = section.getString("username", "root") ?: "root"
            password = section.getString("password", "") ?: ""
            val pool = section.getConfigurationSection("pool")
            maximumPoolSize = pool?.getInt("maximumPoolSize", 10) ?: 10
            minimumIdle = pool?.getInt("minimumIdle", 2) ?: 2
            poolName = "MarketMCPool"
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }
        dataSource = HikariDataSource(hikariConfig)
        createSchema()
    }

    private fun initMongo() {
        val section = config.getConfigurationSection("mongodb")
            ?: throw IllegalStateException("MongoDB section missing in config")
        val connectionString = section.getString("connectionString")
            ?: throw IllegalStateException("MongoDB connectionString missing in config")
        mongoClient = MongoClients.create(connectionString)
        mongoDatabaseField = mongoClient?.getDatabase(section.getString("database", "marketmc") ?: "marketmc")
        plugin.logger.info("Connected to MongoDB. Ensure collections are managed externally.")
    }

    @Throws(SQLException::class)
    private fun createSchema() {
        getConnection().use { conn ->
            conn.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS companies (
                        id CHAR(36) PRIMARY KEY,
                        owner CHAR(36) NOT NULL,
                        name VARCHAR(48) UNIQUE NOT NULL,
                        sector VARCHAR(32),
                        total_shares BIGINT NOT NULL,
                        available_shares BIGINT NOT NULL,
                        share_price DOUBLE NOT NULL,
                        market_cap DOUBLE NOT NULL,
                        treasury DOUBLE NOT NULL DEFAULT 0,
                        store_revenue DOUBLE NOT NULL DEFAULT 0,
                        demand_score DOUBLE NOT NULL DEFAULT 0,
                        volatility DOUBLE NOT NULL DEFAULT 1,
                        units_sold BIGINT NOT NULL DEFAULT 0,
                        created_at BIGINT NOT NULL,
                        revenue_growth DOUBLE NOT NULL DEFAULT 0,
                        buyout_price DOUBLE NOT NULL DEFAULT 0,
                        store_sale_percent DOUBLE NOT NULL DEFAULT 0,
                        last_sale_announcement_at BIGINT NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    ALTER TABLE companies ADD COLUMN IF NOT EXISTS buyout_price DOUBLE NOT NULL DEFAULT 0
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    ALTER TABLE companies ADD COLUMN IF NOT EXISTS store_sale_percent DOUBLE NOT NULL DEFAULT 0
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    ALTER TABLE companies ADD COLUMN IF NOT EXISTS last_sale_announcement_at BIGINT NOT NULL DEFAULT 0
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS shareholders (
                        player_id CHAR(36) NOT NULL,
                        company_id CHAR(36) NOT NULL,
                        shares_owned BIGINT NOT NULL,
                        avg_buy_price DOUBLE NOT NULL,
                        last_trade_at BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (player_id, company_id),
                        FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS transactions (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        company_id CHAR(36) NOT NULL,
                        buyer CHAR(36),
                        seller CHAR(36),
                        shares BIGINT NOT NULL,
                        price DOUBLE NOT NULL,
                        tax_paid DOUBLE NOT NULL,
                        created_at BIGINT NOT NULL
                    )
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS market_history (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        company_id CHAR(36) NOT NULL,
                        price DOUBLE NOT NULL,
                        net_demand DOUBLE NOT NULL,
                        event_key VARCHAR(32),
                        created_at BIGINT NOT NULL
                    )
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS store_listings (
                        id CHAR(36) PRIMARY KEY,
                        company_id CHAR(36) NOT NULL,
                        item LONGTEXT NOT NULL,
                        price DOUBLE NOT NULL,
                        stock INT NOT NULL,
                        created_at BIGINT NOT NULL
                    )
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS store_sales (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        company_id CHAR(36) NOT NULL,
                        listing_id CHAR(36) NOT NULL,
                        buyer CHAR(36) NOT NULL,
                        units INT NOT NULL,
                        revenue DOUBLE NOT NULL,
                        profit DOUBLE NOT NULL,
                        created_at BIGINT NOT NULL
                    )
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS economy_state (
                        id TINYINT PRIMARY KEY,
                        balance DOUBLE NOT NULL
                    )
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS exploit_incidents (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        player_id CHAR(36) NOT NULL,
                        company_id CHAR(36),
                        action VARCHAR(32) NOT NULL,
                        reason VARCHAR(96) NOT NULL,
                        severity DOUBLE NOT NULL,
                        details VARCHAR(255) NOT NULL,
                        created_at BIGINT NOT NULL,
                        FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS marketing_campaigns (
                        id CHAR(36) PRIMARY KEY,
                        company_id CHAR(36) NOT NULL,
                        sponsor CHAR(36) NOT NULL,
                        spend DOUBLE NOT NULL,
                        boost DOUBLE NOT NULL,
                        created_at BIGINT NOT NULL,
                        expires_at BIGINT NOT NULL,
                        FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }
    }

    @Throws(SQLException::class)
    fun getConnection(): Connection {
        return dataSource?.connection ?: throw SQLException("MySQL datasource not initialized")
    }

    val mongoDatabase: MongoDatabase?
        get() = mongoDatabaseField

    val isMongoEnabled: Boolean
        get() = mongoDatabaseField != null

    fun shutdown() {
        dataSource?.close()
        dataSource = null
        mongoClient?.close()
        mongoClient = null
        mongoDatabaseField = null
    }
}
