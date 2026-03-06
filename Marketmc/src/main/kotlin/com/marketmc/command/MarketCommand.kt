package com.marketmc.command

import com.marketmc.gui.MarketGuiService
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class MarketCommand(
    private val guiService: MarketGuiService
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players may open the market UI.")
            return true
        }
        if (!sender.hasPermission("marketmc.market.gui")) {
            sender.sendMessage("${ChatColor.RED}You lack permission to use the market interface.")
            return true
        }
        val page = args.firstOrNull()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        guiService.openOverview(sender, page)
        return true
    }
}
