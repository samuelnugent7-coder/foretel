package me.trade.ui

import org.bukkit.plugin.java.JavaPlugin

class TradePlugin : JavaPlugin() {
    private lateinit var economyBridge: EconomyBridge
    private lateinit var tradeManager: TradeManager

    override fun onEnable() {
        saveDefaultConfig()

        economyBridge = EconomyBridge(this)
        if (!economyBridge.hook()) {
            logger.warning("No Vault economy provider found. Money offers will be disabled.")
        }

        tradeManager = TradeManager(this, economyBridge)
        server.pluginManager.registerEvents(TradeListener(tradeManager), this)

        val tradeCommand = TradeCommand(tradeManager)
        getCommand("trade")?.apply {
            setExecutor(tradeCommand)
            tabCompleter = tradeCommand
        }

        val tradeAcceptCommand = TradeAcceptCommand(tradeManager)
        getCommand("tradeaccept")?.apply {
            setExecutor(tradeAcceptCommand)
            tabCompleter = tradeAcceptCommand
        }
    }

    override fun onDisable() {
        if (this::tradeManager.isInitialized) {
            tradeManager.shutdown()
        }
    }
}
