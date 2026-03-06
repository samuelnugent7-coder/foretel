package com.marketmc.gui

import com.marketmc.MarketMCPlugin
import com.marketmc.config.PluginConfig
import com.marketmc.model.Company
import com.marketmc.service.CompanyService
import com.marketmc.service.EconomyService
import com.marketmc.service.MarketComplianceService
import com.marketmc.service.MarketSignalService
import com.marketmc.service.CompanyOwnershipService
import com.marketmc.service.MarketingService
import com.marketmc.service.StoreService
import com.marketmc.service.TradingService
import com.marketmc.gui.input.GuiInputService
import org.bukkit.entity.Player

class MarketGuiService(
    val plugin: MarketMCPlugin,
    private val config: PluginConfig,
    private val guiManager: GuiManager,
    private val companyService: CompanyService,
    private val tradingService: TradingService,
    private val storeService: StoreService,
    private val economyService: EconomyService,
    private val signalService: MarketSignalService,
    private val complianceService: MarketComplianceService,
    private val ownershipService: CompanyOwnershipService,
    private val inputService: GuiInputService,
    private val marketingService: MarketingService
) {

    fun openOverview(player: Player, page: Int = 0) {
        val repository = plugin.getDataRepository()
        val holdings = repository
            .fetchShareholders(player.uniqueId)
            .associateBy { it.companyId }
        val gui = MarketOverviewGui(this, companyService, signalService, repository, holdings, page)
        guiManager.open(player, gui)
    }

    fun openCompanyDetail(player: Player, company: Company, page: Int = 0) {
        val gui = CompanyDetailGui(this, company, page)
        guiManager.open(player, gui)
    }

    fun openStore(player: Player, company: Company, originPage: Int = 0) {
        val gui = CompanyStoreGui(this, company, originPage)
        guiManager.open(player, gui)
    }

    fun openStoreListingEditor(player: Player, company: Company, originPage: Int) {
        val gui = StoreListingEditorGui(this, company, originPage)
        guiManager.open(player, gui)
    }

    fun openAdmin(player: Player) {
        val gui = MarketAdminGui(this, economyService, signalService, complianceService)
        guiManager.open(player, gui)
    }

    fun openSusBoard(player: Player) {
        val gui = MarketSusGui(this, complianceService)
        guiManager.open(player, gui)
    }

    fun getConfig(): PluginConfig = config

    fun getCompanyService(): CompanyService = companyService

    fun getTradingService(): TradingService = tradingService

    fun getStoreService(): StoreService = storeService

    fun getSignalService(): MarketSignalService = signalService

    fun getEconomyService(): EconomyService = economyService

    fun getComplianceService(): MarketComplianceService = complianceService

    fun getOwnershipService(): CompanyOwnershipService = ownershipService

    fun getInputService(): GuiInputService = inputService

    fun getMarketingService(): MarketingService = marketingService
}
