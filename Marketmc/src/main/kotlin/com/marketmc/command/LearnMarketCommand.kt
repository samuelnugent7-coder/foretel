package com.marketmc.command

import com.marketmc.gui.GuiText
import net.kyori.adventure.text.Component
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta

class LearnMarketCommand : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players may read the market handbook.")
            return true
        }
        if (!sender.hasPermission("marketmc.learn")) {
            sender.sendMessage("${ChatColor.RED}You lack permission to read this guide.")
            return true
        }
        sender.openBook(buildGuideBook())
        return true
    }

    private fun buildGuideBook(): ItemStack {
        val book = ItemStack(Material.WRITTEN_BOOK)
        val meta = book.itemMeta as? BookMeta ?: return book
        meta.title(Component.text("Learn the Market"))
        meta.author(Component.text("MarketMC"))
        meta.pages(buildPages())
        book.itemMeta = meta
        return book
    }

    private fun buildPages() = guidePages.map { page ->
        GuiText.legacy(page.trimIndent())
    }

    private val guidePages = listOf(
        """
        ${ChatColor.GOLD}${ChatColor.BOLD}Learn the Market
        ${ChatColor.RESET}${ChatColor.GRAY}This handbook covers every player command and GUI. Run ${ChatColor.YELLOW}/learnthemarket ${ChatColor.GRAY}anytime for a refresher.
        """,
        """
        ${ChatColor.YELLOW}/market
        ${ChatColor.GRAY}- Opens the Market Overview GUI.
        - /market <page> jumps to later listings.
        - Requires marketmc.market.gui.
        ${ChatColor.GRAY}Browse companies fast, then click any entry to dive into details.
        """,
        """
        ${ChatColor.YELLOW}/company create|info|list
        ${ChatColor.GRAY}- create <name> [shares] [price] [sector] starts a public company.
        - info <name> prints live stats.
        - list shows every company with price + cap.
        Fees pull from your Vault balance.
        """,
        """
        ${ChatColor.YELLOW}/company trading
        ${ChatColor.GRAY}- buy <name> <shares> purchases stock.
        - sellshares <name> <shares> cashes out.
        - sell <company> <price> opens the store listing flow for owners.
        Watch chat for tax + compliance notices when trades settle.
        """,
        """
        ${ChatColor.YELLOW}/company ownership
        ${ChatColor.GRAY}- buycompany <name> completes a listed buyout if you can afford it.
        - buyout <name> <price|clear> lets owners set or remove their asking price.
        Combine this with the GUI buyout button for quick flips.
        """,
        """
        ${ChatColor.YELLOW}/portfolio
        ${ChatColor.GRAY}- Lists every holding you own, the share count, and the live Vault value.
        - Perfect for quick checkups without opening a GUI.
        ${ChatColor.GRAY}Pair this with /market to pick your next move.
        """,
        """
        ${ChatColor.AQUA}Market Overview GUI
        ${ChatColor.GRAY}- Lists all companies by market cap with live price, supply, and macro signals.
        - Item color shows your personal PnL.
        - Lore tracks today vs 24h change plus recent news.
        - Click any logo to open that Company's detail view.
        - Arrows flip pages; compass shows inflation/liquidity.
        """,
        """
        ${ChatColor.AQUA}Company Detail GUI
        ${ChatColor.GRAY}- Quick buttons buy or sell ±10/±100 shares; stars open custom trades.
        - Info book covers treasury, owner, sale status, demand, and marketing boost.
        - Owners can withdraw treasury, set buyout pricing, or launch marketing.
        - Anyone with permission can trigger buyouts or jump into the store.
        """,
        """
        ${ChatColor.AQUA}Company Store GUI
        ${ChatColor.GRAY}- Browse owner-made listings; shift-click to buy four at once.
        - Spyglass tile tracks units sold and lifetime revenue.
        - Sale widgets show active discounts and announcement timers.
        - Owners can list items, set store-wide sales, and broadcast promotions.
        - Back arrow returns to the company card.
        """,
        """
        ${ChatColor.AQUA}Store Listing Editor
        ${ChatColor.GRAY}- Drag any item onto the deposit slot to capture it.
        - Use the gold nugget to set a unit price (listing fee shown in lore).
        - Confirm publishes the listing with your stack size as stock.
        - Cancel or closing safely returns the item to you.
        """
    )
}
