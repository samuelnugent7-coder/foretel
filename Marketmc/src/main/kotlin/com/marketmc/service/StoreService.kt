package com.marketmc.service

import com.marketmc.config.PluginConfig
import com.marketmc.data.DataRepository
import com.marketmc.economy.VaultHook
import com.marketmc.model.Company
import com.marketmc.model.StoreListing
import com.marketmc.model.StoreSale
import com.marketmc.util.ItemSerializer
import com.marketmc.service.AchievementService
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

class StoreService(
    private val config: PluginConfig,
    private val repository: DataRepository,
    private val companyService: CompanyService,
    private val valuationService: ValuationService,
    private val economyService: EconomyService,
    private val vaultHook: VaultHook,
    private val achievementService: AchievementService
) {

    fun createListing(player: Player, company: Company, price: Double) {
        val inHand = player.inventory.itemInMainHand
        if (!validateListingInput(player, inHand, price)) {
            return
        }
        if (!chargeListingFee(player)) {
            return
        }
        val snapshot = inHand.clone()
        player.inventory.setItemInMainHand(null)
        player.updateInventory()
        finalizeListing(player, company, snapshot, price)
    }

    fun createListingFromGui(player: Player, company: Company, item: ItemStack?, price: Double): Boolean {
        if (item == null) {
            player.sendMessage("Provide an item to list.")
            return false
        }
        if (!validateListingInput(player, item, price)) {
            return false
        }
        val snapshot = item.clone()
        if (!chargeListingFee(player)) {
            return false
        }
        finalizeListing(player, company, snapshot, price)
        return true
    }

    fun updateStoreSale(player: Player, company: Company, percent: Double) {
        val normalized = percent.coerceIn(0.0, config.saleMaxPercent)
        val applied = if (normalized < 0.0001) 0.0 else normalized
        company.storeSalePercent = applied
        companyService.updateCompany(company)
        if (applied <= 0.0) {
            player.sendMessage("${ChatColor.YELLOW}Store-wide sale cleared for ${company.name}.")
        } else {
            player.sendMessage(
                "${ChatColor.GREEN}Store-wide sale set to ${String.format("%.0f", applied * 100)}% off."
            )
        }
    }

    fun announceStoreSale(player: Player, company: Company): Boolean {
        val salePercent = company.storeSalePercent
        if (salePercent <= 0.0) {
            player.sendMessage("${ChatColor.RED}Activate a sale before announcing it.")
            return false
        }
        val cost = config.saleAnnouncementCost
        if (cost > 0.0 && !vaultHook.withdraw(player, cost)) {
            player.sendMessage("${ChatColor.RED}You need ${formatCurrency(cost)} to promote this sale.")
            return false
        }
        company.lastSaleAnnouncementAt = System.currentTimeMillis()
        companyService.updateCompany(company)
        val percentLabel = String.format("%.0f", salePercent * 100)
        Bukkit.broadcastMessage(
            "${ChatColor.GOLD}${company.name}${ChatColor.GRAY} launched a ${ChatColor.AQUA}$percentLabel% off ${ChatColor.GRAY}store-wide sale! Visit their store via ${ChatColor.YELLOW}/market${ChatColor.GRAY}."
        )
        player.sendMessage("${ChatColor.GREEN}Sale announcement sent to the entire server.")
        return true
    }

    fun getListings(company: Company): List<StoreListing> {
        return repository.fetchListings(company.id)
    }

    fun purchaseListing(player: Player, company: Company, listing: StoreListing, units: Int) {
        if (units <= 0) {
            player.sendMessage("Invalid quantity.")
            return
        }
        if (listing.stock < units) {
            player.sendMessage("Only ${listing.stock} units remain in stock.")
            return
        }
        val salePercent = company.storeSalePercent.coerceIn(0.0, config.saleMaxPercent)
        val discountedUnitPrice = (listing.price * (1.0 - salePercent)).coerceAtLeast(0.01)
        val totalPrice = discountedUnitPrice * units
        if (!vaultHook.withdraw(player, totalPrice)) {
            player.sendMessage("You cannot afford this purchase.")
            return
        }
        val item = ItemSerializer.deserialize(listing.itemSerialized)?.clone()
        if (item == null) {
            player.sendMessage("Listing data is corrupt.")
            vaultHook.deposit(player, totalPrice)
            return
        }
        item.amount = units
        val leftovers = player.inventory.addItem(item)
        leftovers.values.forEach { leftover ->
            player.world.dropItemNaturally(player.location, leftover)
        }
        player.updateInventory()
        recordStoreSale(listing, company, player, units, totalPrice)
        achievementService.recordStorePurchase(player.uniqueId, units)
        val saleNote = if (salePercent > 0.0) " ${ChatColor.AQUA}(Sale applied)" else ""
        player.sendMessage("Purchased $units x ${item.type} from ${company.name} for ${formatCurrency(totalPrice)}$saleNote")
    }

    fun recordStoreSale(listing: StoreListing, company: Company, buyer: Player, units: Int, revenue: Double) {
        if (units <= 0) {
            return
        }
        listing.stock = (listing.stock - units).coerceAtLeast(0)
        repository.updateStoreListing(listing)
        val profit = revenue * config.revenueWeight
        val sale = StoreSale(
            companyId = company.id,
            listingId = listing.id,
            buyer = buyer.uniqueId,
            units = units,
            revenue = revenue,
            profit = profit,
            timestamp = System.currentTimeMillis()
        )
        repository.insertStoreSale(sale)

        company.treasury += profit
        companyService.updateStoreStats(company, revenue, units.toLong())
        economyService.applyStoreKickback(revenue)
        if (listing.stock <= 0) {
            repository.deleteStoreListing(listing.id)
        }

        val impact = valuationService.calculateRevenueImpact(revenue, company.totalShares, config.revenueWeight)
        val priceCap = company.sharePrice * config.maxPriceMovePercent
        val boundedImpact = impact.coerceAtMost(priceCap)
        val newPrice = company.sharePrice + boundedImpact
        companyService.updateMarketMetrics(company, newPrice, company.demandScore + impact)
    }
    private fun validateListingInput(player: Player, item: ItemStack, price: Double): Boolean {
        if (item.type == Material.AIR || item.amount <= 0) {
            player.sendMessage("Hold an item to list it.")
            return false
        }
        if (price <= 0) {
            player.sendMessage("Price must be positive.")
            return false
        }
        return true
    }

    private fun chargeListingFee(player: Player): Boolean {
        if (config.listingFee <= 0.0) {
            return true
        }
        if (!vaultHook.withdraw(player, config.listingFee)) {
            player.sendMessage("Insufficient funds for listing fee (\$${config.listingFee}).")
            return false
        }
        return true
    }

    private fun finalizeListing(player: Player, company: Company, item: ItemStack, price: Double) {
        val serialized = ItemSerializer.serialize(item)
        val listing = StoreListing(
            id = UUID.randomUUID(),
            companyId = company.id,
            itemSerialized = serialized,
            price = price,
            stock = item.amount,
            createdAt = System.currentTimeMillis()
        )
        repository.insertStoreListing(listing)
        player.sendMessage("Listing created for ${company.name}.")
    }

    private fun formatCurrency(value: Double): String = "%,.2f".format(value)
}
