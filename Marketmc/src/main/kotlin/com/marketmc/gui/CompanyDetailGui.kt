package com.marketmc.gui

import com.marketmc.model.Company
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

class CompanyDetailGui(
    private val service: MarketGuiService,
    company: Company,
    private val previousPage: Int
) : BaseGui(Bukkit.createInventory(null, 45, service.getConfig().guiCompanyDetailTitle)) {

    private val companyId: UUID = company.id

    override fun onOpen(player: Player) {
        render(player)
    }

    private fun render(player: Player) {
        inventory.clear()
        val liveCompany = currentCompany() ?: return
        inventory.setItem(10, infoItem(liveCompany))
        inventory.setItem(12, actionItem(Material.LIME_DYE, "+10 Shares", ChatColor.GREEN))
        inventory.setItem(13, actionItem(Material.EMERALD, "+100 Shares", ChatColor.GREEN))
        val canWithdrawTreasury = player.uniqueId == liveCompany.owner ||
            player.hasPermission("marketmc.company.treasury")
        if (canWithdrawTreasury) {
            inventory.setItem(16, buildTreasuryButton(liveCompany))
        }
        inventory.setItem(15, buildCustomTradeButton(
            material = Material.NETHER_STAR,
            label = "Buy Shares",
            color = ChatColor.GREEN,
            description = "Click to enter any amount to buy"
        ))
        inventory.setItem(21, actionItem(Material.RED_DYE, "-10 Shares", ChatColor.RED))
        inventory.setItem(22, actionItem(Material.BLAZE_POWDER, "-100 Shares", ChatColor.RED))
        inventory.setItem(23, buildCustomTradeButton(
            material = Material.COAL,
            label = "Sell Shares",
            color = ChatColor.RED,
            description = "Click to enter any amount to sell"
        ))
        inventory.setItem(31, ItemStack(Material.CHEST).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.GOLD}Company Store"))
                lore(GuiText.legacy(listOf("${ChatColor.GRAY}Browse exclusive listings")))
            }
        })
        inventory.setItem(35, ItemStack(Material.ARROW).apply {
            applyMeta { displayName(GuiText.legacy("${ChatColor.YELLOW}Back to Market")) }
        })

        val canMarket = service.getConfig().marketingEnabled &&
            (player.uniqueId == liveCompany.owner || player.hasPermission("marketmc.company.marketing"))
        if (canMarket) {
            inventory.setItem(14, buildMarketingButton(liveCompany))
        }

        val canAdjustBuyout = player.uniqueId == liveCompany.owner ||
            player.hasPermission("marketmc.company.buyout.set")
        if (canAdjustBuyout) {
            inventory.setItem(24, buildBuyoutEditor(liveCompany))
        }

        val canAcquire = liveCompany.buyoutPrice > 0.0 && player.uniqueId != liveCompany.owner &&
            player.hasPermission("marketmc.company.acquire")
        if (canAcquire) {
            inventory.setItem(33, buildBuyoutPurchase(liveCompany))
        }
    }

    private fun infoItem(company: Company): ItemStack {
        val signal = service.getSignalService().getCompanySignal(company.id)
        val priceLine = "${ChatColor.GRAY}Price: ${formatCurrency(company.sharePrice)}"
        val cause = signal?.cause ?: "Organic order flow"
        val change = signal?.changePercent ?: 0.0
        val changeLine = "${if (change >= 0) ChatColor.GREEN else ChatColor.RED}${String.format("%.2f", change * 100)}%"
        val marketingBoost = service.getMarketingService().getBoost(company.id)
        return ItemStack(Material.BOOK).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.GOLD}${company.name}"))
                lore(GuiText.legacy(listOf(
                    priceLine,
                    "${ChatColor.GRAY}Demand: ${String.format("%.3f", company.demandScore)}",
                    "${ChatColor.GRAY}Available: ${company.availableShares}",
                    "${ChatColor.GRAY}Market Cap: ${formatCurrency(company.marketCap)}",
                    "${ChatColor.GRAY}Treasury: ${formatCurrency(company.treasury)}",
                    "${ChatColor.GRAY}Owner: ${ChatColor.YELLOW}${ownerName(company.owner)}",
                    "${ChatColor.GRAY}Buyout: ${if (company.buyoutPrice <= 0.0) "${ChatColor.RED}Not listed" else "${ChatColor.GOLD}${formatCurrency(company.buyoutPrice)}"}",
                    "${ChatColor.GRAY}Store Sale: ${if (company.storeSalePercent > 0.0) "${ChatColor.GREEN}${String.format("%.0f", company.storeSalePercent * 100)}% off" else "${ChatColor.DARK_GRAY}Inactive"}",
                    if (marketingBoost > 0.0) "${ChatColor.AQUA}Marketing boost: +${String.format("%.3f", marketingBoost)}" else "${ChatColor.DARK_GRAY}Marketing idle",
                    "${ChatColor.GRAY}Last Move: $changeLine",
                    "${ChatColor.DARK_GRAY}$cause"
                )))
            }
        }
    }

    override fun handleClick(player: Player, event: InventoryClickEvent) {
        when (event.rawSlot) {
            12 -> executeTrade(player, 10)
            13 -> executeTrade(player, 100)
            15 -> promptCustomTrade(player, isBuy = true)
            16 -> promptTreasuryWithdraw(player)
            21 -> executeTrade(player, -10)
            22 -> executeTrade(player, -100)
            23 -> promptCustomTrade(player, isBuy = false)
            31 -> currentCompany()?.let { service.openStore(player, it, previousPage) }
            35 -> service.openOverview(player, previousPage)
            24 -> promptBuyoutPrice(player)
            33 -> attemptCompanyPurchase(player)
            14 -> promptMarketingSpend(player)
        }
    }

    private fun buildTreasuryButton(company: Company): ItemStack = ItemStack(Material.GOLD_BLOCK).apply {
        applyMeta {
            displayName(GuiText.legacy("${ChatColor.GOLD}Withdraw Treasury"))
            lore(GuiText.legacy(listOf(
                "${ChatColor.GRAY}Available: ${formatCurrency(company.treasury)}",
                "${ChatColor.DARK_GRAY}Click to transfer funds to you"
            )))
        }
    }

    private fun executeTrade(player: Player, delta: Int) {
        val permission = if (delta > 0) "marketmc.company.buy" else "marketmc.company.sell"
        if (!player.hasPermission(permission)) {
            player.sendMessage("${ChatColor.RED}You lack permission for this action.")
            return
        }
        val tradingService = service.getTradingService()
        val company = currentCompany() ?: return
        if (delta > 0) {
            tradingService.buyShares(player, company, delta.toLong())
        } else {
            tradingService.sellShares(player, company, -delta.toLong())
        }
        render(player)
    }

    private fun actionItem(material: Material, label: String, color: ChatColor): ItemStack {
        return ItemStack(material).apply {
            applyMeta { displayName(GuiText.legacy("$color$label")) }
        }
    }

    private fun buildCustomTradeButton(
        material: Material,
        label: String,
        color: ChatColor,
        description: String
    ): ItemStack = ItemStack(material).apply {
        applyMeta {
            displayName(GuiText.legacy("$color$label"))
            lore(GuiText.legacy(listOf("${ChatColor.GRAY}$description")))
        }
    }

    private fun buildBuyoutEditor(company: Company): ItemStack = ItemStack(Material.WRITABLE_BOOK).apply {
        applyMeta {
            displayName(GuiText.legacy("${ChatColor.AQUA}Set Buyout Price"))
            lore(GuiText.legacy(listOf(
                "${ChatColor.GRAY}Current: ${if (company.buyoutPrice <= 0.0) "${ChatColor.RED}Not for sale" else "${ChatColor.GOLD}${formatCurrency(company.buyoutPrice)}"}",
                "${ChatColor.DARK_GRAY}Click to enter price"
            )))
        }
    }

    private fun buildMarketingButton(company: Company): ItemStack = ItemStack(Material.FIREWORK_ROCKET).apply {
        val config = service.getConfig()
        val active = service.getMarketingService().getActiveCampaigns(company.id).size
        applyMeta {
            displayName(GuiText.legacy("${ChatColor.LIGHT_PURPLE}Launch Marketing"))
            lore(GuiText.legacy(listOf(
                "${ChatColor.GRAY}Min: ${formatCurrency(config.marketingMinSpend)}",
                "${ChatColor.GRAY}Max: ${formatCurrency(config.marketingMaxSpend)}",
                "${ChatColor.GRAY}Active Campaigns: ${ChatColor.AQUA}$active",
                "${ChatColor.DARK_GRAY}Spending is permanently removed"
            )))
        }
    }

    private fun buildBuyoutPurchase(company: Company): ItemStack = ItemStack(Material.EMERALD_BLOCK).apply {
        applyMeta {
            displayName(GuiText.legacy("${ChatColor.GREEN}Buy Company"))
            lore(GuiText.legacy(listOf(
                "${ChatColor.GRAY}Price: ${formatCurrency(company.buyoutPrice)}",
                "${ChatColor.DARK_GRAY}Transfers ownership"
            )))
        }
    }

    private fun promptBuyoutPrice(player: Player) {
        val company = currentCompany() ?: return
        val prompt = GuiText.legacy("${ChatColor.AQUA}Enter new buyout price for ${company.name}:")
        service.getInputService().requestDouble(player, prompt, min = 0.0, onResult = { value ->
            val latest = currentCompany() ?: return@requestDouble
            service.getOwnershipService().setBuyoutPrice(latest, value)
            player.sendMessage("${ChatColor.GREEN}Buyout price updated to ${formatCurrency(value)}")
            service.openCompanyDetail(player, latest, previousPage)
        })
    }

    private fun attemptCompanyPurchase(player: Player) {
        val company = currentCompany() ?: return
        val success = service.getOwnershipService().purchaseCompany(player, company)
        if (success) {
            service.openCompanyDetail(player, company, previousPage)
        }
    }

    private fun promptMarketingSpend(player: Player) {
        val company = currentCompany() ?: return
        val config = service.getConfig()
        val prompt = GuiText.legacy("${ChatColor.LIGHT_PURPLE}Enter marketing spend for ${company.name}:")
        service.getInputService().requestDouble(
            player,
            prompt,
            min = config.marketingMinSpend,
            max = config.marketingMaxSpend,
            onResult = { amount ->
                val latest = currentCompany() ?: return@requestDouble
                val result = service.getMarketingService().startCampaign(player, latest, amount)
                player.sendMessage(if (result.success) "${ChatColor.GREEN}${result.message}" else "${ChatColor.RED}${result.message}")
                if (result.success) {
                    service.openCompanyDetail(player, latest, previousPage)
                }
            }
        )
    }

    private fun promptTreasuryWithdraw(player: Player) {
        val company = currentCompany() ?: return
        val prompt = GuiText.legacy("${ChatColor.GOLD}Enter how much to withdraw from ${company.name}'s treasury:")
        service.getInputService().requestDouble(
            player = player,
            prompt = prompt,
            min = 1.0,
            onResult = { amount ->
                val latest = currentCompany() ?: return@requestDouble
                val success = service.getOwnershipService().withdrawTreasury(player, latest, amount)
                if (success) {
                    service.openCompanyDetail(player, latest, previousPage)
                }
            },
            onCancel = {
                service.openCompanyDetail(player, company, previousPage)
            }
        )
    }

    private fun promptCustomTrade(player: Player, isBuy: Boolean) {
        val permission = if (isBuy) "marketmc.company.buy" else "marketmc.company.sell"
        if (!player.hasPermission(permission)) {
            player.sendMessage("${ChatColor.RED}You lack permission for this action.")
            return
        }
        val company = currentCompany() ?: return
        val color = if (isBuy) ChatColor.GREEN else ChatColor.RED
        val action = if (isBuy) "buy" else "sell"
        val prompt = GuiText.legacy("$color Enter how many shares to $action for ${company.name}:")
        service.getInputService().requestLong(
            player = player,
            prompt = prompt,
            min = 1,
            onResult = { amount ->
                val latest = currentCompany() ?: return@requestLong
                val tradingService = service.getTradingService()
                if (isBuy) {
                    tradingService.buyShares(player, latest, amount)
                } else {
                    tradingService.sellShares(player, latest, amount)
                }
                service.openCompanyDetail(player, latest, previousPage)
            },
            onCancel = {
                service.openCompanyDetail(player, company, previousPage)
            }
        )
    }

    private fun ItemStack.applyMeta(block: org.bukkit.inventory.meta.ItemMeta.() -> Unit) {
        val meta = itemMeta ?: return
        meta.block()
        itemMeta = meta
    }

    private fun formatCurrency(value: Double): String = "%,.2f".format(value)

    private fun ownerName(ownerId: UUID): String {
        val offline = Bukkit.getOfflinePlayer(ownerId)
        return offline.name ?: ownerId.toString().substring(0, 8)
    }

    private fun currentCompany(): Company? = service.getCompanyService().getCompany(companyId)
}
