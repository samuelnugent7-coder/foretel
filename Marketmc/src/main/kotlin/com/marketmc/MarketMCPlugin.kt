package com.marketmc

import com.marketmc.command.CompanyCommand
import com.marketmc.command.LearnMarketCommand
import com.marketmc.command.MarketAdminCommand
import com.marketmc.command.MarketCommand
import com.marketmc.command.PortfolioCommand
import com.marketmc.config.PluginConfig
import com.marketmc.config.StorageMode
import com.marketmc.data.DataRepository
import com.marketmc.data.FileDataRepository
import com.marketmc.data.SqlDataRepository
import com.marketmc.database.DatabaseManager
import com.marketmc.economy.VaultHook
import com.marketmc.placeholder.MarketPlaceholderExpansion
import com.marketmc.service.AntiManipulationService
import com.marketmc.service.AchievementService
import com.marketmc.service.AnalyticsApiService
import com.marketmc.service.ApiKeyManager
import com.marketmc.service.CompanyService
import com.marketmc.gui.GuiManager
import com.marketmc.gui.MarketGuiService
import com.marketmc.gui.input.GuiInputService
import com.marketmc.service.MarketingService
import com.marketmc.service.EconomyService
import com.marketmc.service.EventService
import com.marketmc.service.CompanyOwnershipService
import com.marketmc.service.MarketComplianceService
import com.marketmc.service.MarketEngine
import com.marketmc.service.MarketSignalService
import com.marketmc.service.SandboxSimulationService
import com.marketmc.service.SecuritySettings
import com.marketmc.service.StoreService
import com.marketmc.service.TradingService
import com.marketmc.service.ValuationService
import com.marketmc.task.EconomicEventTask
import com.marketmc.task.MarketTickTask
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.Locale
import java.util.logging.Level

class MarketMCPlugin : JavaPlugin() {

    private lateinit var pluginConfig: PluginConfig
    private var databaseManager: DatabaseManager? = null
    private lateinit var dataRepository: DataRepository
    private lateinit var vaultHook: VaultHook

    private lateinit var companyService: CompanyService
    private lateinit var companyOwnershipService: CompanyOwnershipService
    private lateinit var marketingService: MarketingService
    private lateinit var antiManipulationService: AntiManipulationService
    private lateinit var securitySettings: SecuritySettings
    private lateinit var marketEngine: MarketEngine
    private lateinit var marketSignalService: MarketSignalService
    private lateinit var marketComplianceService: MarketComplianceService
    private lateinit var economyService: EconomyService
    private lateinit var tradingService: TradingService
    private lateinit var storeService: StoreService
    private lateinit var eventService: EventService
    private lateinit var valuationService: ValuationService
    private lateinit var guiManager: GuiManager
    private lateinit var marketGuiService: MarketGuiService
    private lateinit var guiInputService: GuiInputService
    private lateinit var achievementService: AchievementService
    private lateinit var analyticsApiService: AnalyticsApiService
    private lateinit var apiKeyManager: ApiKeyManager
    private lateinit var sandboxSimulationService: SandboxSimulationService

    private var placeholderExpansion: MarketPlaceholderExpansion? = null
    private var marketTask: BukkitTask? = null
    private var eventTask: BukkitTask? = null

    override fun onEnable() {
        instance = this
        saveDefaultConfig()
        if (!reloadPluginConfig()) {
            logger.severe("MarketMC will disable itself until config.yml issues are resolved.")
            server.pluginManager.disablePlugin(this)
            return
        }

        vaultHook = VaultHook(this)
        if (!vaultHook.hook()) {
            logger.severe("Vault not found, disabling MarketMC.")
            server.pluginManager.disablePlugin(this)
            return
        }

        val repositoryResult = createRepository(pluginConfig)
        if (repositoryResult == null) {
            server.pluginManager.disablePlugin(this)
            return
        }
        databaseManager = repositoryResult.first
        dataRepository = repositoryResult.second

        if (!::guiInputService.isInitialized) {
            guiInputService = GuiInputService(this)
        }

        initializeServices()
        loadCompaniesIntoCache()
        logStartupSummary()
        registerCommands()
        startTasks()
        hookPlaceholderApi()

        logger.info("MarketMC enabled successfully.")
    }

    override fun onDisable() {
        stopTasks()
        unregisterPlaceholderHook()
        if (::marketEngine.isInitialized) {
            marketEngine.flushSnapshots()
        }
        if (::analyticsApiService.isInitialized) {
            analyticsApiService.stop()
        }
        if (::sandboxSimulationService.isInitialized && sandboxSimulationService.isRunning()) {
            sandboxSimulationService.requestStop()
        }
        databaseManager?.shutdown()
        if (::vaultHook.isInitialized) {
            vaultHook.unhook()
        }
    }

    fun reloadPlugin(): Boolean {
        val previousConfig = pluginConfig
        if (!reloadPluginConfig()) {
            pluginConfig = previousConfig
            logger.severe("Reload aborted because config.yml contains errors. Keeping previous settings.")
            return false
        }

        val previousManager = databaseManager
        val previousRepository = dataRepository

        val repositoryResult = createRepository(pluginConfig)
        if (repositoryResult == null) {
            pluginConfig = previousConfig
            databaseManager = previousManager
            dataRepository = previousRepository
            return false
        }

        stopTasks()
        unregisterPlaceholderHook()
        if (::marketEngine.isInitialized) {
            marketEngine.flushSnapshots()
        }
        if (::analyticsApiService.isInitialized) {
            analyticsApiService.stop()
        }
        if (::sandboxSimulationService.isInitialized && sandboxSimulationService.isRunning()) {
            sandboxSimulationService.requestStop()
        }
        previousManager?.shutdown()

        databaseManager = repositoryResult.first
        dataRepository = repositoryResult.second

        if (!::guiInputService.isInitialized) {
            guiInputService = GuiInputService(this)
        }

        initializeServices()
        loadCompaniesIntoCache()
        registerCommands()
        startTasks()
        hookPlaceholderApi()
        return true
    }

    private fun createRepository(config: PluginConfig): Pair<DatabaseManager?, DataRepository>? {
        return when (config.storageMode) {
            StorageMode.FILE -> {
                try {
                    Pair(null, FileDataRepository(this, config.fileStorageDirectory, config.startingEconomyBalance))
                } catch (ex: Exception) {
                    logger.log(
                        Level.SEVERE,
                        "Failed to initialize file storage at '${config.fileStorageDirectory}'. Check database.file.* settings in config.yml.",
                        ex
                    )
                    null
                }
            }
            StorageMode.MYSQL, StorageMode.MONGODB -> {
                try {
                    val manager = DatabaseManager(this, config.databaseSection)
                    manager.init()
                    Pair(manager, SqlDataRepository(this, manager, config.startingEconomyBalance))
                } catch (ex: Exception) {
                    logger.log(
                        Level.SEVERE,
                        "Failed to initialize ${config.storageMode} database. Verify database.${config.storageMode.name.lowercase(Locale.ROOT)}.* settings in config.yml.",
                        ex
                    )
                    null
                }
            }
        }
    }

    private fun initializeServices() {
        securitySettings = SecuritySettings(pluginConfig)
        achievementService = AchievementService(this, pluginConfig)
        companyService = CompanyService(this, dataRepository)
        companyOwnershipService = CompanyOwnershipService(this, companyService, vaultHook)
        marketingService = MarketingService(this, pluginConfig, dataRepository, vaultHook, achievementService)
        antiManipulationService = AntiManipulationService(pluginConfig, securitySettings)
        economyService = EconomyService(pluginConfig, dataRepository)
        economyService.ensureBaseline()
        marketSignalService = MarketSignalService(pluginConfig)
        marketEngine = MarketEngine(this, pluginConfig, dataRepository, companyService, marketSignalService, marketingService, achievementService)
        valuationService = ValuationService()
        storeService = StoreService(pluginConfig, dataRepository, companyService, valuationService, economyService, vaultHook, achievementService)
        marketComplianceService = MarketComplianceService(pluginConfig, dataRepository, securitySettings)
        tradingService = TradingService(
            config = pluginConfig,
            repository = dataRepository,
            companyService = companyService,
            antiManipulationService = antiManipulationService,
            marketEngine = marketEngine,
            complianceService = marketComplianceService,
            vaultHook = vaultHook,
            achievementService = achievementService
        )
        eventService = EventService(this, pluginConfig, companyService, dataRepository)
        apiKeyManager = ApiKeyManager(this)
        analyticsApiService = AnalyticsApiService(this, pluginConfig, companyService, dataRepository, apiKeyManager)
        analyticsApiService.start()
        sandboxSimulationService = SandboxSimulationService(this, pluginConfig, companyService)
        if (!::guiManager.isInitialized) {
            guiManager = GuiManager(this)
        }
        marketGuiService = MarketGuiService(
            plugin = this,
            config = pluginConfig,
            guiManager = guiManager,
            companyService = companyService,
            tradingService = tradingService,
            storeService = storeService,
            economyService = economyService,
            signalService = marketSignalService,
            complianceService = marketComplianceService,
            ownershipService = companyOwnershipService,
            inputService = guiInputService,
            marketingService = marketingService
        )
    }

    private fun reloadPluginConfig(): Boolean {
        return try {
            reloadConfig()
            pluginConfig = PluginConfig(config, logger)
            true
        } catch (ex: Exception) {
            logger.log(Level.SEVERE, "Failed to load config.yml: ${ex.message}", ex)
            false
        }
    }

    private fun loadCompaniesIntoCache() {
        val companies = dataRepository.fetchCompanies()
        companyService.cacheCompanies(companies)
    }

    private fun registerCommands() {
        val companyCommand = CompanyCommand(
            config = pluginConfig,
            companyService = companyService,
            tradingService = tradingService,
            storeService = storeService,
            vaultHook = vaultHook,
            ownershipService = companyOwnershipService
        )
        val portfolioCommand = PortfolioCommand(companyService, dataRepository)
        val adminCommand = MarketAdminCommand(
            plugin = this,
            guiService = marketGuiService,
            apiKeyManager = apiKeyManager,
            analyticsService = analyticsApiService,
            sandboxService = sandboxSimulationService
        )
        val marketCommand = MarketCommand(marketGuiService)
        val learnMarketCommand = LearnMarketCommand()

        getCommand("company")?.apply {
            setExecutor(companyCommand)
            tabCompleter = companyCommand
        }
        getCommand("portfolio")?.setExecutor(portfolioCommand)
        getCommand("market")?.setExecutor(marketCommand)
        getCommand("learnthemarket")?.setExecutor(learnMarketCommand)
        getCommand("marketmc")?.apply {
            setExecutor(adminCommand)
            tabCompleter = adminCommand
        }
    }

    private fun startTasks() {
        stopTasks()
        val tickIntervalSeconds = pluginConfig.tradingTickIntervalSeconds.coerceAtLeast(1)
        val tickIntervalTicks = 20L * tickIntervalSeconds
        marketTask = MarketTickTask(marketEngine).runTaskTimer(this, tickIntervalTicks, tickIntervalTicks)
        eventTask = EconomicEventTask(eventService, pluginConfig).runTaskTimer(this, 20L * 60L, 20L * 60L)
    }

    private fun stopTasks() {
        marketTask?.cancel()
        marketTask = null
        eventTask?.cancel()
        eventTask = null
    }

    private fun hookPlaceholderApi() {
        unregisterPlaceholderHook()
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return
        }
        placeholderExpansion = MarketPlaceholderExpansion(this, companyService, dataRepository, vaultHook).also { expansion ->
            if (expansion.register()) {
                logger.info("Registered PlaceholderAPI placeholders.")
            }
        }
    }

    private fun unregisterPlaceholderHook() {
        placeholderExpansion?.unregister()
        placeholderExpansion = null
    }

    private fun logStartupSummary() {
        val storageDescriptor = when (pluginConfig.storageMode) {
            StorageMode.FILE -> "file storage @ ${pluginConfig.fileStorageDirectory}"
            StorageMode.MYSQL -> {
                val mysql = pluginConfig.databaseSection.getConfigurationSection("mysql")
                val host = mysql?.getString("host") ?: "?"
                val db = mysql?.getString("database") ?: "marketmc"
                "MySQL ${host}/${db}"
            }
            StorageMode.MONGODB -> {
                val mongo = pluginConfig.databaseSection.getConfigurationSection("mongodb")
                val connection = mongo?.getString("connectionString")
                val hostPort = connection
                    ?.substringAfter("@", connection.substringAfter("://", ""))
                    ?.substringBeforeLast("/")
                    ?.ifBlank { "?" }
                    ?: "?"
                val dbName = mongo?.getString("database") ?: "marketmc"
                "MongoDB ${hostPort}/${dbName}"
            }
        }
        logger.info("MarketMC v${description.version} targeting $storageDescriptor")
        val featureSummary = buildString {
            append("Marketing=")
            append(if (pluginConfig.marketingEnabled) "ON" else "OFF")
            append(", Analytics=")
            append(if (pluginConfig.analyticsEnabled) "ON" else "OFF")
            append(", Sandbox=")
            append(if (pluginConfig.sandboxEnabled) "ON" else "OFF")
            append(", Achievements=")
            append(if (pluginConfig.achievementsEnabled) "ON" else "OFF")
        }
        logger.info(featureSummary)
    }

    fun getPluginConfig(): PluginConfig = pluginConfig

    fun getCompanyService(): CompanyService = companyService

    fun getTradingService(): TradingService = tradingService

    fun getStoreService(): StoreService = storeService

    fun getCompanyOwnershipService(): CompanyOwnershipService = companyOwnershipService

    fun getMarketingService(): MarketingService = marketingService

    fun getEventService(): EventService = eventService

    fun getAntiManipulationService(): AntiManipulationService = antiManipulationService

    fun getMarketSignalService(): MarketSignalService = marketSignalService

    fun getMarketComplianceService(): MarketComplianceService = marketComplianceService

    fun getEconomyService(): EconomyService = economyService

    fun getMarketGuiService(): MarketGuiService = marketGuiService

    fun getGuiInputService(): GuiInputService = guiInputService

    fun getAchievementService(): AchievementService = achievementService

    fun getAnalyticsService(): AnalyticsApiService = analyticsApiService

    fun getApiKeyManager(): ApiKeyManager = apiKeyManager

    fun getSandboxSimulationService(): SandboxSimulationService = sandboxSimulationService

    fun getDataRepository(): DataRepository = dataRepository

    fun getVaultHook(): VaultHook = vaultHook

    fun getSecuritySettings(): SecuritySettings = securitySettings

    companion object {
        private lateinit var instance: MarketMCPlugin

        fun getInstance(): MarketMCPlugin = instance
    }
}
