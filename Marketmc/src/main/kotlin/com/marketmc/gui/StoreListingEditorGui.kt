package com.marketmc.gui

import com.marketmc.model.Company
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

class StoreListingEditorGui(
    private val service: MarketGuiService,
    company: Company,
    private val originPage: Int
) : BaseGui(Bukkit.createInventory(null, 45, GuiText.legacy("${ChatColor.GOLD}Store Listing"))) {

    private val companyId: UUID = company.id
    private var capturedItem: ItemStack? = null
    private var listingPrice: Double? = null
    private var listingCreated: Boolean = false

    private val depositSlot = 13

    override fun onOpen(player: Player) {
        render(player)
    }

    override fun onClose(player: Player) {
        if (!listingCreated) {
            returnCapturedItem(player)
        }
    }

    override fun handleClick(player: Player, event: InventoryClickEvent) {
        when (event.rawSlot) {
            depositSlot -> handleDepositSlot(player, event)
            16 -> promptPrice(player)
            22 -> attemptCreateListing(player)
            31 -> {
                returnCapturedItem(player)
                currentCompany()?.let { service.openStore(player, it, originPage) }
            }
            40 -> currentCompany()?.let { service.openStore(player, it, originPage) }
        }
    }

    private fun render(player: Player) {
        val company = currentCompany() ?: return
        inventory.clear()
        fillBackground()
        inventory.setItem(4, ItemStack(Material.BOOK).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.GOLD}${company.name} Store"))
                lore(GuiText.legacy(listOf(
                    "${ChatColor.GRAY}List custom items with a GUI",
                    "${ChatColor.GRAY}Listing fee: ${formatCurrency(service.getConfig().listingFee)}"
                )))
            }
        })
        inventory.setItem(10, ItemStack(Material.HOPPER).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.AQUA}Insert Item"))
                lore(GuiText.legacy(listOf(
                    "${ChatColor.GRAY}Drag an item onto the display slot",
                    "${ChatColor.GRAY}Click again to take it back"
                )))
            }
        })
        inventory.setItem(depositSlot, buildDepositSlot())
        inventory.setItem(16, buildPriceButton())
        inventory.setItem(22, buildConfirmButton())
        inventory.setItem(31, ItemStack(Material.BARRIER).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.RED}Cancel"))
                lore(GuiText.legacy(listOf("${ChatColor.GRAY}Return item and go back")))
            }
        })
        inventory.setItem(40, ItemStack(Material.ARROW).apply {
            applyMeta { displayName(GuiText.legacy("${ChatColor.YELLOW}Back to Store")) }
        })
    }

    private fun handleDepositSlot(player: Player, event: InventoryClickEvent) {
        val cursor = player.itemOnCursor
        val hasCursorItem = cursor != null && cursor.type != Material.AIR
        if (hasCursorItem) {
            if (capturedItem != null) {
                player.sendMessage("${ChatColor.RED}Remove the current item first.")
                return
            }
            capturedItem = cursor?.clone()
            player.setItemOnCursor(ItemStack(Material.AIR))
            player.updateInventory()
            val snapshot = capturedItem
            player.sendMessage("${ChatColor.GREEN}Captured ${snapshot?.amount}x ${snapshot?.type}.")
            render(player)
            return
        }
        val stored = capturedItem
        if (stored != null) {
            val cursorOccupied = cursor != null && cursor.type != Material.AIR
            if (cursorOccupied) {
                player.sendMessage("${ChatColor.RED}Clear your cursor before taking the item back.")
                return
            }
            player.setItemOnCursor(stored)
            capturedItem = null
            player.updateInventory()
            render(player)
        }
    }

    private fun promptPrice(player: Player) {
        val company = currentCompany() ?: return
        val prompt = GuiText.legacy("${ChatColor.GOLD}Enter unit price for ${company.name}:")
        service.getInputService().requestDouble(
            player = player,
            prompt = prompt,
            min = 0.01,
            onResult = { value ->
                listingPrice = value
                player.sendMessage("${ChatColor.GREEN}Price set to ${formatCurrency(value)}")
                render(player)
            },
            onCancel = null,
            closeInventory = false
        )
    }

    private fun attemptCreateListing(player: Player) {
        val company = currentCompany() ?: return
        if (!canManageStore(player, company)) {
            player.sendMessage("${ChatColor.RED}Only the owner may list store items.")
            service.openStore(player, company, originPage)
            return
        }
        val item = capturedItem
        val price = listingPrice
        if (item == null) {
            player.sendMessage("${ChatColor.RED}Place an item in the slot first.")
            return
        }
        if (price == null) {
            player.sendMessage("${ChatColor.RED}Set a price before confirming.")
            return
        }
        val success = service.getStoreService().createListingFromGui(player, company, item, price)
        if (success) {
            listingCreated = true
            capturedItem = null
            service.openStore(player, company, originPage)
        } else {
            returnCapturedItem(player)
            render(player)
        }
    }

    private fun buildDepositSlot(): ItemStack {
        val item = capturedItem
        if (item == null) {
            return ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
                applyMeta {
                    displayName(GuiText.legacy("${ChatColor.DARK_GRAY}Drag item here"))
                    lore(GuiText.legacy(listOf(
                        "${ChatColor.GRAY}Supports any custom item",
                        "${ChatColor.GRAY}Stack size becomes stock"
                    )))
                }
            }
        }
        val clone = item.clone()
        clone.applyMeta {
            val priceLine = listingPrice?.let { "${ChatColor.GRAY}Price: ${formatCurrency(it)}" }
            val lore = mutableListOf(
                "${ChatColor.GRAY}Stock: ${item.amount}",
                "${ChatColor.GRAY}Listing fee: ${formatCurrency(service.getConfig().listingFee)}"
            )
            if (priceLine != null) {
                lore.add(0, priceLine)
            }
            lore(GuiText.legacy(lore))
        }
        return clone
    }

    private fun buildPriceButton(): ItemStack {
        val priceDisplay = listingPrice?.let { formatCurrency(it) } ?: "${ChatColor.RED}Not set"
        return ItemStack(Material.GOLD_NUGGET).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.GOLD}Set Price"))
                lore(GuiText.legacy(listOf(
                    "${ChatColor.GRAY}Current: $priceDisplay",
                    "${ChatColor.YELLOW}Click to type a price"
                )))
            }
        }
    }

    private fun buildConfirmButton(): ItemStack {
        val ready = capturedItem != null && listingPrice != null
        val material = if (ready) Material.LIME_CONCRETE else Material.GRAY_CONCRETE
        return ItemStack(material).apply {
            applyMeta {
                displayName(GuiText.legacy("${if (ready) ChatColor.GREEN else ChatColor.DARK_GRAY}Create Listing"))
                val requirements = mutableListOf<String>()
                requirements += if (capturedItem == null) "${ChatColor.RED}- Item required" else "${ChatColor.GREEN}- Item ready"
                requirements += if (listingPrice == null) "${ChatColor.RED}- Price required" else "${ChatColor.GREEN}- Price set"
                requirements += "${ChatColor.GRAY}Listing fee: ${formatCurrency(service.getConfig().listingFee)}"
                lore(GuiText.legacy(requirements))
            }
        }
    }

    private fun fillBackground() {
        val filler = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            applyMeta { displayName(GuiText.legacy(" ")) }
        }
        for (slot in 0 until inventory.size) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler)
            }
        }
    }

    private fun returnCapturedItem(player: Player) {
        val item = capturedItem ?: return
        val cursor = player.itemOnCursor
        if (cursor == null || cursor.type == Material.AIR) {
            player.setItemOnCursor(item)
        } else {
            val leftovers = player.inventory.addItem(item)
            leftovers.values.forEach { leftover ->
                player.world.dropItemNaturally(player.location, leftover)
            }
        }
        capturedItem = null
        player.updateInventory()
    }

    private fun canManageStore(player: Player, company: Company): Boolean {
        return (player.uniqueId == company.owner && player.hasPermission("marketmc.company.store")) ||
            player.hasPermission("marketmc.company.store.manage")
    }

    private fun formatCurrency(value: Double): String = "%,.2f".format(value)

    private fun currentCompany(): Company? = service.getCompanyService().getCompany(companyId)

    private fun ItemStack.applyMeta(block: org.bukkit.inventory.meta.ItemMeta.() -> Unit) {
        val meta = itemMeta ?: return
        meta.block()
        itemMeta = meta
    }
}
