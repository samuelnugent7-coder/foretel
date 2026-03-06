package com.marketmc.gui

import com.marketmc.model.Company
import com.marketmc.model.StoreListing
import com.marketmc.service.StoreService
import com.marketmc.util.ItemSerializer
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class CompanyStoreGui(
    private val service: MarketGuiService,
    company: Company,
    private val originPage: Int
) : BaseGui(Bukkit.createInventory(null, 54, service.getConfig().guiStoreTitle)) {

    private var listings: List<StoreListing> = emptyList()
    private val companyId = company.id

    override fun onOpen(player: Player) {
        refresh(player)
    }

    private fun refresh(player: Player) {
        val company = currentCompany() ?: return
        listings = service.getStoreService().getListings(company)
        inventory.clear()
        val salePercent = company.storeSalePercent.coerceAtLeast(0.0)
        listings.take(45).forEachIndexed { index, listing ->
            inventory.setItem(index, buildListingItem(listing, salePercent))
        }
        val canListItems = canManageStore(player, company)
        if (canListItems) {
            inventory.setItem(45, ItemStack(Material.WRITABLE_BOOK).apply {
                applyMeta {
                    displayName(GuiText.legacy("${ChatColor.GREEN}List New Item"))
                    lore(GuiText.legacy(listOf(
                        "${ChatColor.GRAY}Open the listing editor",
                        "${ChatColor.GRAY}Supports custom pricing"
                    )))
                }
            })
            inventory.setItem(46, buildSaleToggleItem(company))
            inventory.setItem(47, buildSellerGuideItem())
            inventory.setItem(52, buildSaleAnnouncementItem(company))
        } else {
            inventory.setItem(46, buildSaleStatusItem(company))
        }
        inventory.setItem(49, ItemStack(Material.SPYGLASS).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.AQUA}Store Metrics"))
                lore(GuiText.legacy(listOf(
                    "${ChatColor.GRAY}Units sold: ${company.unitsSold}",
                    "${ChatColor.GRAY}Revenue: ${formatCurrency(company.storeRevenue)}"
                )))
            }
        })
        inventory.setItem(53, ItemStack(Material.ARROW).apply {
            applyMeta { displayName(GuiText.legacy("${ChatColor.YELLOW}Back")) }
        })
        if (listings.isEmpty()) {
            inventory.setItem(22, ItemStack(Material.BARRIER).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.RED}No listings"))
                lore(GuiText.legacy(listOf("${ChatColor.GRAY}Owners can add inventory via List New Item")))
                }
            })
        }
    }

    override fun handleClick(player: Player, event: InventoryClickEvent) {
        val slot = event.rawSlot
        if (slot == 53) {
            currentCompany()?.let { service.openCompanyDetail(player, it, originPage) }
            return
        }
        if (slot == 45) {
            openListingEditor(player)
            return
        }
        if (slot == 46) {
            currentCompany()?.let { company ->
                if (canManageStore(player, company)) {
                    promptSalePercent(player, company)
                } else if (company.storeSalePercent > 0.0) {
                    player.sendMessage("${ChatColor.AQUA}${company.name} is running ${String.format("%.0f", company.storeSalePercent * 100)}% off in their store.")
                }
            }
            return
        }
        if (slot == 52) {
            currentCompany()?.let { company ->
                if (canManageStore(player, company)) {
                    service.getStoreService().announceStoreSale(player, company)
                    refresh(player)
                } else {
                    player.sendMessage("${ChatColor.RED}Only the owner may promote sales.")
                }
            }
            return
        }
        if (slot >= listings.size) {
            return
        }
        val listing = listings.getOrNull(slot) ?: return
        val amount = if (event.isShiftClick) 4 else 1
        val liveCompany = currentCompany() ?: return
        service.getStoreService().purchaseListing(player, liveCompany, listing, amount)
        refresh(player)
    }

    private fun buildListingItem(listing: StoreListing, salePercent: Double): ItemStack {
        val item = ItemSerializer.deserialize(listing.itemSerialized)?.clone() ?: ItemStack(Material.CHEST)
        val meta = item.itemMeta
            meta?.displayName(GuiText.legacy("${ChatColor.GOLD}${item.type}"))
            val discountedPrice = (listing.price * (1.0 - salePercent)).coerceAtLeast(0.01)
            val lore = mutableListOf(
            "${ChatColor.GRAY}Base: ${formatCurrency(listing.price)}",
            "${ChatColor.GRAY}Stock: ${listing.stock}",
            "${ChatColor.DARK_GRAY}Shift-click to buy 4"
        )
            if (salePercent > 0.0) {
                lore.add(1, "${ChatColor.AQUA}Sale: ${formatCurrency(discountedPrice)} (${String.format("%.0f", salePercent * 100)}% off)")
            }
            meta?.lore(GuiText.legacy(lore))
        item.itemMeta = meta
        return item
    }

    private fun openListingEditor(player: Player) {
        val company = currentCompany() ?: return
        if (!canManageStore(player, company)) {
            player.sendMessage("${ChatColor.RED}Only the owner may list store items.")
            return
        }
        service.openStoreListingEditor(player, company, originPage)
    }

    private fun promptSalePercent(player: Player, company: Company) {
        val maxPercent = service.getConfig().saleMaxPercent * 100.0
        val prompt = GuiText.legacy("${ChatColor.GOLD}Enter sale percent (0 disables):")
        service.getInputService().requestDouble(
            player = player,
            prompt = prompt,
            min = 0.0,
            max = maxPercent,
            onResult = { value ->
                val normalized = value / 100.0
                service.getStoreService().updateStoreSale(player, company, normalized)
                service.openStore(player, company, originPage)
            },
            onCancel = { service.openStore(player, company, originPage) },
            closeInventory = true
        )
    }

    private fun buildSellerGuideItem(): ItemStack = ItemStack(Material.BOOK).apply {
        applyMeta {
            displayName(GuiText.legacy("${ChatColor.AQUA}How to Sell Items"))
            lore(GuiText.legacy(listOf(
                "${ChatColor.GRAY}1. Click List New Item.",
                "${ChatColor.GRAY}2. Drag the item onto the slot.",
                "${ChatColor.GRAY}3. Set a price, then confirm."
            )))
        }
    }

    private fun buildSaleToggleItem(company: Company): ItemStack {
        val active = company.storeSalePercent > 0.0
        val percentLabel = if (active) formatPercent(company.storeSalePercent) else "${ChatColor.RED}No sale"
        val material = if (active) Material.HONEY_BLOCK else Material.HONEY_BOTTLE
        val maxPercentLabel = String.format("%.0f", service.getConfig().saleMaxPercent * 100)
        return ItemStack(material).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.GOLD}Store-Wide Sale"))
                lore(GuiText.legacy(listOf(
                    "${ChatColor.GRAY}Active: $percentLabel",
                    "${ChatColor.GRAY}Max: ${maxPercentLabel}%",
                    "${ChatColor.YELLOW}Click to set or clear"
                )))
            }
        }
    }

    private fun buildSaleStatusItem(company: Company): ItemStack {
        val active = company.storeSalePercent > 0.0
        val display = if (active) "${ChatColor.AQUA}${formatPercent(company.storeSalePercent)} off" else "${ChatColor.RED}No sale"
        return ItemStack(Material.SUNFLOWER).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.GOLD}Store Sale"))
                lore(GuiText.legacy(listOf(
                    "${ChatColor.GRAY}Current: $display",
                    "${ChatColor.DARK_GRAY}Owners may configure sales"
                )))
            }
        }
    }

    private fun buildSaleAnnouncementItem(company: Company): ItemStack {
        val cost = service.getConfig().saleAnnouncementCost
        val costLine = if (cost > 0.0) formatCurrency(cost) else "Free"
        val last = if (company.lastSaleAnnouncementAt <= 0) {
            "${ChatColor.DARK_GRAY}Never announced"
        } else {
            val minutes = ((System.currentTimeMillis() - company.lastSaleAnnouncementAt) / 60000L).coerceAtLeast(0)
            "${ChatColor.GRAY}Last blast: ${minutes}m ago"
        }
        val statusLine = if (company.storeSalePercent > 0.0) {
            "${ChatColor.GREEN}Sale active: ready to announce"
        } else {
            "${ChatColor.RED}Activate a sale before announcing"
        }
        return ItemStack(Material.NOTE_BLOCK).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.YELLOW}Announce Sale"))
                lore(GuiText.legacy(listOf(
                    "${ChatColor.GRAY}Cost: ${costLine}",
                    last,
                    statusLine,
                    "${ChatColor.GOLD}Broadcast the sale server-wide"
                )))
            }
        }
    }

    private fun canManageStore(player: Player, company: Company): Boolean {
        return (player.uniqueId == company.owner && player.hasPermission("marketmc.company.store")) ||
            player.hasPermission("marketmc.company.store.manage")
    }

    private fun ItemStack.applyMeta(block: org.bukkit.inventory.meta.ItemMeta.() -> Unit) {
        val meta = itemMeta ?: return
        meta.block()
        itemMeta = meta
    }

    private fun formatPercent(value: Double): String = String.format("%.0f%%", value * 100)

    private fun formatCurrency(value: Double): String = "%,.2f".format(value)

    private fun currentCompany(): Company? = service.getCompanyService().getCompany(companyId)
}
