package com.marketmc.command

import com.marketmc.data.DataRepository
import com.marketmc.service.CompanyService
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.Locale

class PortfolioCommand(
    private val companyService: CompanyService,
    private val repository: DataRepository
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players may run this command.")
            return true
        }
        if (!sender.hasPermission("marketmc.portfolio.view")) {
            sender.sendMessage("${ChatColor.RED}You lack permission to view portfolios.")
            return true
        }
        val holdings = repository.fetchShareholders(sender.uniqueId)
        if (holdings.isEmpty()) {
            sender.sendMessage("${ChatColor.GRAY}You currently hold no shares.")
            return true
        }
        sender.sendMessage("${ChatColor.GOLD}-- Portfolio --")
        holdings.forEach { shareholder ->
            val company = companyService.getCompany(shareholder.companyId) ?: return@forEach
            val value = shareholder.sharesOwned * company.sharePrice
            sender.sendMessage(
                "${ChatColor.YELLOW}${company.name}${ChatColor.GRAY}: ${shareholder.sharesOwned} shares | Value \$${String.format(Locale.US, "%.2f", value)}"
            )
        }
        return true
    }
}
