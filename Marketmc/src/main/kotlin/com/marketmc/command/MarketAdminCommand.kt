package com.marketmc.command

import com.marketmc.MarketMCPlugin
import com.marketmc.gui.MarketGuiService
import com.marketmc.model.ApiScope
import com.marketmc.service.AnalyticsApiService
import com.marketmc.service.ApiKeyManager
import com.marketmc.service.SandboxSimulationService
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

class MarketAdminCommand(
    private val plugin: MarketMCPlugin,
    private val guiService: MarketGuiService,
    private val apiKeyManager: ApiKeyManager,
    private val analyticsService: AnalyticsApiService,
    private val sandboxService: SandboxSimulationService
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}Usage: /$label <reload|gui|sus|apikey|analytics|sandbox>")
            return true
        }
        return when (args[0].lowercase(Locale.ROOT)) {
            "reload" -> {
                if (!sender.hasPermission("marketmc.admin.reload")) {
                    sender.sendMessage("${ChatColor.RED}You lack reload permission.")
                    true
                } else {
                    val start = System.currentTimeMillis()
                    val success = plugin.reloadPlugin()
                    if (success) {
                        sender.sendMessage("${ChatColor.GREEN}MarketMC reloaded in ${System.currentTimeMillis() - start}ms.")
                    } else {
                        sender.sendMessage("${ChatColor.RED}Reload failed. Check console.")
                    }
                    true
                }
            }
            "gui" -> {
                if (sender !is org.bukkit.entity.Player) {
                    sender.sendMessage("Player only command.")
                    true
                } else if (!sender.hasPermission("marketmc.admin.gui")) {
                    sender.sendMessage("${ChatColor.RED}You lack admin GUI permission.")
                    true
                } else {
                    guiService.openAdmin(sender)
                    true
                }
            }
            "sus" -> {
                if (sender !is org.bukkit.entity.Player) {
                    sender.sendMessage("Player only command.")
                    true
                } else if (!sender.hasPermission("marketmc.admin.sus")) {
                    sender.sendMessage("${ChatColor.RED}You lack SUS board permission.")
                    true
                } else {
                    guiService.openSusBoard(sender)
                    true
                }
            }
            "apikey" -> handleApiKey(sender, label, args)
            "analytics" -> handleAnalytics(sender, label, args)
            "sandbox" -> handleSandbox(sender, label, args)
            else -> {
                sender.sendMessage("${ChatColor.YELLOW}Usage: /$label <reload|gui|sus|apikey|analytics|sandbox>")
                true
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (args.size == 1) {
            val prefix = args[0].lowercase(Locale.ROOT)
            val options = listOf("reload", "gui", "sus", "apikey", "analytics", "sandbox")
            return options.filter { it.startsWith(prefix) }.toMutableList()
        }
        if (args.size == 2) {
            return when (args[0].lowercase(Locale.ROOT)) {
                "apikey" -> listOf("list", "create", "revoke").filterStarts(args[1])
                "analytics" -> listOf("status", "restart").filterStarts(args[1])
                "sandbox" -> listOf("run", "stop", "status").filterStarts(args[1])
                else -> mutableListOf()
            }
        }
        return mutableListOf()
    }

    private fun handleApiKey(sender: CommandSender, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("marketmc.admin.apikey")) {
            sender.sendMessage("${ChatColor.RED}You lack API key permissions.")
            return true
        }
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.YELLOW}Usage: /$label apikey <list|create|revoke>")
            return true
        }
        return when (args[1].lowercase(Locale.ROOT)) {
            "list" -> {
                val entries = apiKeyManager.listKeys()
                if (entries.isEmpty()) {
                    sender.sendMessage("${ChatColor.GRAY}No API keys generated yet.")
                } else {
                    entries.forEach {
                        sender.sendMessage(
                            "${ChatColor.AQUA}${it.label}${ChatColor.GRAY} :: ${it.token} :: ${it.scopes.joinToString(",")}" +
                                " ${ChatColor.DARK_GRAY}(last used ${formatTimestamp(it.lastUsed)})"
                        )
                    }
                }
                true
            }
            "create" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.YELLOW}Usage: /$label apikey create <label> [admin]")
                    return true
                }
                val labelName = args[2]
                val scopes = if (args.size >= 4 && args[3].equals("admin", ignoreCase = true)) {
                    setOf(ApiScope.ADMIN)
                } else {
                    setOf(ApiScope.READ_ANALYTICS)
                }
                val creator = (sender as? org.bukkit.entity.Player)?.uniqueId
                val key = apiKeyManager.createKey(labelName, scopes, creator)
                sender.sendMessage("${ChatColor.GREEN}API key created! Token: ${key.token}")
                true
            }
            "revoke" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.YELLOW}Usage: /$label apikey revoke <token>")
                    return true
                }
                val success = apiKeyManager.revokeKey(args[2])
                if (success) {
                    sender.sendMessage("${ChatColor.GREEN}API key revoked.")
                } else {
                    sender.sendMessage("${ChatColor.RED}Token not found.")
                }
                true
            }
            else -> {
                sender.sendMessage("${ChatColor.YELLOW}Usage: /$label apikey <list|create|revoke>")
                true
            }
        }
    }

    private fun handleAnalytics(sender: CommandSender, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("marketmc.admin.analytics")) {
            sender.sendMessage("${ChatColor.RED}You lack analytics permissions.")
            return true
        }
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.YELLOW}Usage: /$label analytics <status|restart>")
            return true
        }
        return when (args[1].lowercase(Locale.ROOT)) {
            "status" -> {
                val running = analyticsService.isRunning()
                sender.sendMessage(
                    if (running) "${ChatColor.GREEN}Analytics API is running." else "${ChatColor.RED}Analytics API is offline."
                )
                true
            }
            "restart" -> {
                analyticsService.reload(plugin.getPluginConfig())
                sender.sendMessage("${ChatColor.GREEN}Analytics API restarted.")
                true
            }
            else -> {
                sender.sendMessage("${ChatColor.YELLOW}Usage: /$label analytics <status|restart>")
                true
            }
        }
    }

    private fun handleSandbox(sender: CommandSender, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("marketmc.admin.sandbox")) {
            sender.sendMessage("${ChatColor.RED}You lack sandbox permissions.")
            return true
        }
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.YELLOW}Usage: /$label sandbox <run|stop|status> [minutes]")
            return true
        }
        return when (args[1].lowercase(Locale.ROOT)) {
            "run" -> {
                val minutes = args.getOrNull(2)?.toIntOrNull() ?: plugin.getPluginConfig().sandboxMaxDurationMinutes
                sandboxService.runSimulation(sender, minutes)
            }
            "stop" -> {
                sandboxService.requestStop()
                sender.sendMessage("${ChatColor.YELLOW}Sandbox simulation stop requested.")
                true
            }
            "status" -> {
                if (sandboxService.isRunning()) {
                    sender.sendMessage("${ChatColor.GREEN}Sandbox simulation is running.")
                } else {
                    val report = sandboxService.getLastReport()
                    if (report == null) {
                        sender.sendMessage("${ChatColor.GRAY}No sandbox simulations have been run yet.")
                    } else {
                        sender.sendMessage(
                            "${ChatColor.AQUA}Last sandbox: ${report.companySnapshots.size} companies, ${report.ticksSimulated} ticks."
                        )
                    }
                }
                true
            }
            else -> {
                sender.sendMessage("${ChatColor.YELLOW}Usage: /$label sandbox <run|stop|status> [minutes]")
                true
            }
        }
    }

    private fun List<String>.filterStarts(prefix: String): MutableList<String> {
        val lower = prefix.lowercase(Locale.ROOT)
        return this.filter { it.startsWith(lower) }.toMutableList()
    }

    private fun formatTimestamp(epoch: Long): String {
        if (epoch <= 0) {
            return "never"
        }
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epoch))
    }
}
