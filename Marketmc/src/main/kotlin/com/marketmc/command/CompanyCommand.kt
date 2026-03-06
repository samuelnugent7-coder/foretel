package com.marketmc.command

import com.marketmc.config.PluginConfig
import com.marketmc.economy.VaultHook
import com.marketmc.model.Company
import com.marketmc.service.CompanyService
import com.marketmc.service.CompanyOwnershipService
import com.marketmc.service.StoreService
import com.marketmc.service.TradingService
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.Locale

class CompanyCommand(
    private val config: PluginConfig,
    private val companyService: CompanyService,
    private val tradingService: TradingService,
    private val storeService: StoreService,
    private val vaultHook: VaultHook,
    private val ownershipService: CompanyOwnershipService
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("${ChatColor.GOLD}/company create|info|buy|sellshares|sell|list|buycompany|buyout")
            return true
        }
        val sub = args[0].lowercase(Locale.ROOT)
        val remaining = args.copyOfRange(1, args.size)
        when (sub) {
            "create" -> handleCreate(sender, remaining)
            "info" -> handleInfo(sender, remaining)
            "buy" -> handleBuy(sender, remaining)
            "sellshares" -> handleSellShares(sender, remaining)
            "sell" -> handleStoreSell(sender, remaining)
            "list" -> handleList(sender)
            "buycompany" -> handleBuyCompany(sender, remaining)
            "buyout" -> handleBuyout(sender, remaining)
            else -> sender.sendMessage("${ChatColor.RED}Unknown sub-command.")
        }
        return true
    }

    private fun handleCreate(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("Only players can create companies.")
            return
        }
        if (!sender.hasPermission("marketmc.company.create")) {
            sender.sendMessage("${ChatColor.RED}You lack permission.")
            return
        }
        if (args.isEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}Usage: /company create <name> [shares] [price] [sector]")
            return
        }
        val name = args[0]
        if (companyService.getCompanyByName(name) != null) {
            sender.sendMessage("${ChatColor.RED}A company with that name already exists.")
            return
        }
        val shares = if (args.size >= 2) parseLong(args[1], config.defaultShares) else config.defaultShares
        val price = if (args.size >= 3) parseDouble(args[2], config.defaultSharePrice) else config.defaultSharePrice
        val sector = if (args.size >= 4) args[3].lowercase(Locale.ROOT) else "general"

        if (config.allowedSectors.isNotEmpty() && !config.allowedSectors.contains(sector)) {
            sender.sendMessage("${ChatColor.RED}Invalid sector. Allowed: ${config.allowedSectors.joinToString(", ")}")
            return
        }
        if (shares <= 0 || price <= 0) {
            sender.sendMessage("${ChatColor.RED}Shares and price must be positive.")
            return
        }

        val creationCost = config.companyCreationCost
        val ipoFee = shares * price * config.ipoFeePercent
        val totalCost = creationCost + ipoFee
        if (vaultHook.getBalance(sender) < totalCost) {
            sender.sendMessage("${ChatColor.RED}You need \$${totalCost} to start this company.")
            return
        }
        if (!vaultHook.withdraw(sender, totalCost)) {
            sender.sendMessage("${ChatColor.RED}Payment failed.")
            return
        }

        val company = companyService.createCompany(
            owner = sender.uniqueId,
            name = name,
            totalShares = shares,
            sharePrice = price,
            sector = sector,
            buyoutPrice = config.defaultBuyoutPrice
        )
        sender.sendMessage("${ChatColor.GREEN}Created company ${company.name} with $shares shares at \$${price}")
    }

    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("marketmc.company.info")) {
            sender.sendMessage("${ChatColor.RED}You lack permission.")
            return
        }
        if (args.isEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}Usage: /company info <name>")
            return
        }
        val company = companyService.getCompanyByName(args[0])
        if (company == null) {
            sender.sendMessage("${ChatColor.RED}Company not found.")
            return
        }
        sender.sendMessage("${ChatColor.AQUA}--- ${company.name} ---")
        sender.sendMessage("${ChatColor.AQUA}Share Price: \$${String.format(Locale.US, "%.2f", company.sharePrice)}")
        sender.sendMessage("${ChatColor.AQUA}Market Cap: \$${String.format(Locale.US, "%.2f", company.marketCap)}")
        sender.sendMessage("${ChatColor.AQUA}Available Shares: ${company.availableShares}/${company.totalShares}")
        sender.sendMessage("${ChatColor.AQUA}Sector: ${company.sector}")
        sender.sendMessage("${ChatColor.AQUA}Demand Score: ${String.format(Locale.US, "%.4f", company.demandScore)}")
        sender.sendMessage("${ChatColor.AQUA}Revenue: \$${String.format(Locale.US, "%.2f", company.storeRevenue)}")
    }

    private fun handleBuy(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("Only players can buy shares.")
            return
        }
            if (!sender.hasPermission("marketmc.company.buy")) {
            sender.sendMessage("${ChatColor.RED}You lack permission.")
            return
        }
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.YELLOW}Usage: /company buy <name> <shares>")
            return
        }
        val company = companyService.getCompanyByName(args[0])
        if (company == null) {
            sender.sendMessage("${ChatColor.RED}Company not found.")
            return
        }
        val shares = parseLong(args[1], 0)
        tradingService.buyShares(sender, company, shares)
    }

    private fun handleSellShares(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("Only players can sell shares.")
            return
        }
            if (!sender.hasPermission("marketmc.company.sell")) {
            sender.sendMessage("${ChatColor.RED}You lack permission.")
            return
        }
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.YELLOW}Usage: /company sellshares <name> <shares>")
            return
        }
        val company = companyService.getCompanyByName(args[0])
        if (company == null) {
            sender.sendMessage("${ChatColor.RED}Company not found.")
            return
        }
        val shares = parseLong(args[1], 0)
        tradingService.sellShares(sender, company, shares)
    }

    private fun handleStoreSell(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("Only players can list items.")
            return
        }
        if (!sender.hasPermission("marketmc.company.store")) {
            sender.sendMessage("${ChatColor.RED}You lack permission.")
            return
        }
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.YELLOW}Usage: /company sell <company> <price>")
            return
        }
        val company = companyService.getCompanyByName(args[0])
        if (company == null) {
            sender.sendMessage("${ChatColor.RED}Company not found.")
            return
        }
        val price = parseDouble(args[1], 0.0)
        if (company.owner != sender.uniqueId) {
            sender.sendMessage("${ChatColor.RED}Only the owner may list store items.")
            return
        }
        storeService.createListing(sender, company, price)
    }

    private fun handleList(sender: CommandSender) {
        if (!sender.hasPermission("marketmc.company.list")) {
            sender.sendMessage("${ChatColor.RED}You lack permission.")
            return
        }
        val companies = companyService.getCompanies()
        if (companies.isEmpty()) {
            sender.sendMessage("${ChatColor.GRAY}No companies exist yet.")
            return
        }
        sender.sendMessage("${ChatColor.GOLD}-- Listed Companies --")
        companies.forEach { company ->
            val price = String.format(Locale.US, "%.2f", company.sharePrice)
            val cap = String.format(Locale.US, "%.0f", company.marketCap)
            sender.sendMessage("${ChatColor.YELLOW}${company.name}${ChatColor.GRAY} | \$${price} | Cap \$${cap}")
        }
    }

    private fun handleBuyCompany(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("Only players may buy companies.")
            return
        }
        if (!sender.hasPermission("marketmc.company.acquire")) {
            sender.sendMessage("${ChatColor.RED}You lack permission to acquire companies.")
            return
        }
        if (args.isEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}Usage: /company buycompany <name>")
            return
        }
        val company = companyService.getCompanyByName(args[0])
        if (company == null) {
            sender.sendMessage("${ChatColor.RED}Company not found.")
            return
        }
        ownershipService.purchaseCompany(sender, company)
    }

    private fun handleBuyout(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.YELLOW}Usage: /company buyout <name> <price|clear>")
            return
        }
        val company = companyService.getCompanyByName(args[0])
        if (company == null) {
            sender.sendMessage("${ChatColor.RED}Company not found.")
            return
        }
        if (sender is Player) {
            val ownsCompany = company.owner == sender.uniqueId
            val hasAdmin = sender.hasPermission("marketmc.company.buyout.set")
            if (!ownsCompany && !hasAdmin) {
                sender.sendMessage("${ChatColor.RED}Only the owner or admins may update the buyout price.")
                return
            }
        } else if (!sender.hasPermission("marketmc.company.buyout.set")) {
            sender.sendMessage("${ChatColor.RED}Console lacks buyout permission.")
            return
        }

        if (args[1].equals("clear", ignoreCase = true)) {
            ownershipService.clearBuyoutPrice(company)
            sender.sendMessage("${ChatColor.GREEN}Cleared buyout price for ${company.name}.")
            return
        }
        val price = parseDouble(args[1], -1.0)
        if (price <= 0.0) {
            sender.sendMessage("${ChatColor.RED}Price must be positive or use 'clear'.")
            return
        }
        ownershipService.setBuyoutPrice(company, price)
        sender.sendMessage("${ChatColor.GREEN}Set buyout price for ${company.name} to \$${String.format(Locale.US, "%.2f", price)}")
    }

    private fun parseLong(input: String, fallback: Long): Long {
        return input.toLongOrNull() ?: fallback
    }

    private fun parseDouble(input: String, fallback: Double): Double {
        return input.toDoubleOrNull() ?: fallback
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (args.size == 1) {
            val options = listOf("create", "info", "buy", "sellshares", "sell", "list", "buycompany", "buyout")
            val prefix = args[0].lowercase(Locale.ROOT)
            return options.filter { it.startsWith(prefix) }.toMutableList()
        }
        if (
            args.size == 2 &&
            listOf("info", "buy", "sellshares", "sell", "buycompany", "buyout")
                .contains(args[0].lowercase(Locale.ROOT))
        ) {
            val prefix = args[1].lowercase(Locale.ROOT)
            val suggestions = mutableListOf<String>()
            companyService.getCompanies().forEach { company ->
                if (company.name.lowercase(Locale.ROOT).startsWith(prefix)) {
                    suggestions += company.name
                }
            }
            return suggestions
        }
        return mutableListOf()
    }
}
