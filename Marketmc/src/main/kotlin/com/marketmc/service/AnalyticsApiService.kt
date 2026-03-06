package com.marketmc.service

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.marketmc.MarketMCPlugin
import com.marketmc.config.PluginConfig
import com.marketmc.data.DataRepository
import com.marketmc.model.ApiKeyInfo
import com.marketmc.model.ApiScope
import com.marketmc.model.Company
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.EnumSet
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.logging.Level
import kotlin.concurrent.read
import kotlin.concurrent.write

class AnalyticsApiService(
    private val plugin: MarketMCPlugin,
    private var config: PluginConfig,
    private val companyService: CompanyService,
    private val repository: DataRepository,
    private val apiKeyManager: ApiKeyManager
) {

    private val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    private var server: HttpServer? = null

    fun start() {
        if (!config.analyticsEnabled) {
            plugin.logger.info("Analytics API disabled in config.yml")
            stop()
            return
        }
        stop()
        try {
            val address = InetSocketAddress(config.analyticsBindAddress, config.analyticsPort)
            val instance = HttpServer.create(address, 0)
            instance.createContext("/analytics/overview") { exchange ->
                handleWithAuth(exchange, ApiScope.READ_ANALYTICS) { writeOverview(exchange) }
            }
            instance.createContext("/analytics/company") { exchange ->
                handleWithAuth(exchange, ApiScope.READ_ANALYTICS) { writeCompany(exchange) }
            }
            instance.executor = Executors.newCachedThreadPool()
            instance.start()
            server = instance
            plugin.logger.info("Analytics API listening on ${address.hostString}:${address.port}")
        } catch (ex: IOException) {
            plugin.logger.log(Level.SEVERE, "Failed to start analytics API", ex)
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    fun reload(newConfig: PluginConfig) {
        config = newConfig
        start()
    }

    fun isRunning(): Boolean = server != null

    private fun handleWithAuth(exchange: HttpExchange, scope: ApiScope, block: () -> Unit) {
        try {
            if (config.analyticsRequireKey) {
                val token = exchange.requestHeaders.getFirst("X-API-Key")
                val key = apiKeyManager.validate(token, scope)
                if (key == null) {
                    respond(exchange, 401, mapOf("error" to "Missing or invalid API key"))
                    return
                }
                apiKeyManager.markUsed(key.token)
            }
            block()
        } catch (ex: Exception) {
            plugin.logger.log(Level.WARNING, "Analytics API failure", ex)
            respond(exchange, 500, mapOf("error" to "Internal server error"))
        } finally {
            exchange.close()
        }
    }

    private fun writeOverview(exchange: HttpExchange) {
        if (exchange.requestMethod.uppercase(Locale.ROOT) != "GET") {
            respond(exchange, 405, mapOf("error" to "Method not allowed"))
            return
        }
        val companies = companyService.getCompanies()
        val totalCap = companies.sumOf { it.marketCap }
        val avgPrice = if (companies.isNotEmpty()) companies.sumOf { it.sharePrice } / companies.size else 0.0
        val topMovers = companies.sortedByDescending { kotlin.math.abs(it.demandScore) }
            .take(5)
            .map {
                mapOf(
                    "name" to it.name,
                    "price" to it.sharePrice,
                    "demand" to it.demandScore
                )
            }
        val payload = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "companyCount" to companies.size,
            "totalMarketCap" to totalCap,
            "averageSharePrice" to avgPrice,
            "economyBalance" to repository.getEconomyBalance(),
            "topMovers" to topMovers
        )
        respond(exchange, 200, payload)
    }

    private fun writeCompany(exchange: HttpExchange) {
        if (exchange.requestMethod.uppercase(Locale.ROOT) != "GET") {
            respond(exchange, 405, mapOf("error" to "Method not allowed"))
            return
        }
        val query = parseQuery(exchange.requestURI.query)
        val name = query["name"]
        val idRaw = query["id"]
        val company = when {
            !name.isNullOrBlank() -> companyService.getCompanyByName(name)
            !idRaw.isNullOrBlank() -> runCatching { UUID.fromString(idRaw) }.getOrNull()?.let { companyService.getCompany(it) }
            else -> null
        }
        if (company == null) {
            respond(exchange, 404, mapOf("error" to "Company not found"))
            return
        }
        val payload = mapOf(
            "id" to company.id.toString(),
            "name" to company.name,
            "sector" to company.sector,
            "sharePrice" to company.sharePrice,
            "marketCap" to company.marketCap,
            "treasury" to company.treasury,
            "storeRevenue" to company.storeRevenue,
            "unitsSold" to company.unitsSold,
            "demandScore" to company.demandScore,
            "buyoutPrice" to company.buyoutPrice
        )
        respond(exchange, 200, payload)
    }

    private fun respond(exchange: HttpExchange, status: Int, payload: Any) {
        val json = mapper.writeValueAsBytes(payload)
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, json.size.toLong())
        exchange.responseBody.use { it.write(json) }
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) {
            return emptyMap()
        }
        return raw.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = pair.substring(0, idx)
            val value = pair.substring(idx + 1)
            key to URLDecoder.decode(value, StandardCharsets.UTF_8)
        }.toMap()
    }
}

class ApiKeyManager(private val plugin: MarketMCPlugin) {

    private val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    private val storage = File(plugin.dataFolder, "apikeys.json")
    private val lock = ReentrantReadWriteLock()
    private val keys = mutableMapOf<String, ApiKeyInfo>()

    init {
        load()
    }

    fun createKey(label: String, scopes: Set<ApiScope>, creator: UUID?): ApiKeyInfo {
        val token = UUID.randomUUID().toString().replace("-", "")
        val info = ApiKeyInfo(token, label, scopes.ifEmpty { EnumSet.of(ApiScope.READ_ANALYTICS) }, creator, System.currentTimeMillis())
        lock.write {
            keys[token] = info
            save()
        }
        return info
    }

    fun revokeKey(token: String): Boolean {
        var removed = false
        lock.write {
            removed = keys.remove(token) != null
            if (removed) {
                save()
            }
        }
        return removed
    }

    fun listKeys(): List<ApiKeyInfo> = lock.read {
        keys.values.map {
            val copyScopes = if (it.scopes.isEmpty()) EnumSet.noneOf(ApiScope::class.java) else EnumSet.copyOf(it.scopes)
            it.copy(scopes = copyScopes)
        }
    }

    fun validate(token: String?, scope: ApiScope): ApiKeyInfo? {
        if (token.isNullOrBlank()) return null
        return lock.read {
            val key = keys[token] ?: return@read null
            if (key.scopes.contains(ApiScope.ADMIN) || key.scopes.contains(scope)) {
                key
            } else {
                null
            }
        }
    }

    fun markUsed(token: String) {
        lock.write {
            val info = keys[token] ?: return
            info.lastUsed = System.currentTimeMillis()
            save()
        }
    }

    private fun load() {
        if (!storage.exists()) {
            storage.parentFile?.mkdirs()
            return
        }
        try {
            val type = mapper.typeFactory.constructMapType(Map::class.java, String::class.java, ApiKeyInfo::class.java)
            @Suppress("UNCHECKED_CAST")
            val loaded = mapper.readValue<Map<String, ApiKeyInfo>>(storage, type)
            lock.write {
                keys.clear()
                keys.putAll(loaded)
            }
        } catch (ex: IOException) {
            plugin.logger.log(Level.SEVERE, "Failed to load API keys", ex)
        }
    }

    private fun save() {
        try {
            mapper.writeValue(storage, keys)
        } catch (ex: IOException) {
            plugin.logger.log(Level.SEVERE, "Failed to save API keys", ex)
        }
    }
}