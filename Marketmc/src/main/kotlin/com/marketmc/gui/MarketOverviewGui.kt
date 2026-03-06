package com.marketmc.gui

import com.marketmc.data.DataRepository
import com.marketmc.model.Company
import com.marketmc.model.CompanySignalSnapshot
import com.marketmc.model.Shareholder
import com.marketmc.service.CompanyService
import com.marketmc.service.MarketSignalService
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min

class MarketOverviewGui(
    private val service: MarketGuiService,
    private val companyService: CompanyService,
    private val signalService: MarketSignalService,
    private val repository: DataRepository,
    private val holdings: Map<UUID, Shareholder>,
    private val page: Int
) : BaseGui(Bukkit.createInventory(null, 54, service.getConfig().guiMarketOverviewTitle)) {

    private val slotCompany = mutableMapOf<Int, Company>()
    private val dayChangeCache = mutableMapOf<UUID, Double?>()

    override fun onOpen(player: Player) {
        render(player)
    }

    private fun render(player: Player) {
        inventory.clear()
        slotCompany.clear()
        val companies = companyService.getCompanies().sortedByDescending { it.marketCap }
        val pageSize = 45
        val offset = page * pageSize
        val slice = companies.drop(offset).take(pageSize)
        slice.forEachIndexed { index, company ->
            val slot = index
            slotCompany[slot] = company
            inventory.setItem(slot, buildCompanyItem(company))
        }
            inventory.setItem(45, buildMacroItem())
        if (page > 0) {
            inventory.setItem(48, ItemStack(Material.ARROW).apply {
                applyMeta { displayName(GuiText.legacy("${ChatColor.YELLOW}Previous Page")) }
            })
        }
        if (offset + pageSize < companies.size) {
            inventory.setItem(50, ItemStack(Material.SPECTRAL_ARROW).apply {
                applyMeta { displayName(GuiText.legacy("${ChatColor.YELLOW}Next Page")) }
            })
        }
        inventory.setItem(53, ItemStack(Material.BOOK).apply {
            applyMeta { displayName(GuiText.legacy("${ChatColor.AQUA}Refresh")) }
        })
    }

    override fun handleClick(player: Player, event: InventoryClickEvent) {
        val slot = event.rawSlot
        when (slot) {
            48 -> if (page > 0) service.openOverview(player, page - 1)
            50 -> {
                service.openOverview(player, page + 1)
            }
            53 -> service.openOverview(player, page)
            else -> {
                val company = slotCompany[slot] ?: return
                service.openCompanyDetail(player, company, page)
            }
        }
    }

    private fun buildCompanyItem(company: Company): ItemStack {
        val signal = signalService.getCompanySignal(company.id)
        val percent = signal?.changePercent ?: 0.0
        val arrow = if (percent >= 0) "▲" else "▼"
        val color = if (percent >= 0) ChatColor.GREEN else ChatColor.RED
        val holding = holdings[company.id]
        val pnlValue = if (holding != null && holding.sharesOwned > 0) {
            (company.sharePrice - holding.avgBuyPrice) * holding.sharesOwned
        } else {
            null
        }
        val material = when {
            pnlValue != null && pnlValue > 0 -> Material.LIME_CONCRETE
            pnlValue != null && pnlValue < 0 -> Material.RED_CONCRETE
            else -> Material.PAPER
        }
        val lore = mutableListOf(
            "${ChatColor.GRAY}Shares: ${company.availableShares}/${company.totalShares}",
            "${ChatColor.GRAY}Price: ${formatCurrency(company.sharePrice)}",
            "${ChatColor.GRAY}Cap: ${formatCurrency(company.marketCap)}",
            "${color}${String.format("%.2f", percent * 100)}% $arrow today"
        )
        if (holding != null && holding.sharesOwned > 0 && holding.avgBuyPrice > 0) {
            val pnlPercent = (company.sharePrice - holding.avgBuyPrice) / holding.avgBuyPrice
            val pnlColor = if (pnlValue ?: 0.0 >= 0) ChatColor.GREEN else ChatColor.RED
            val pnlIcon = if (pnlValue ?: 0.0 >= 0) "✔" else "✖"
            lore += "$pnlColor$pnlIcon You: ${formatPercent(pnlPercent)} (${formatSignedCurrency(pnlValue!!)})"
        } else {
            lore += "${ChatColor.DARK_GRAY}You: no holdings"
        }
        val dayChange = getDayChange(company)
        if (dayChange != null) {
            val dayColor = if (dayChange >= 0) ChatColor.GREEN else ChatColor.RED
            val dayIcon = if (dayChange >= 0) "↗" else "↘"
            lore += "$dayColor$dayIcon 24h: ${formatPercent(dayChange)}"
        } else {
            lore += "${ChatColor.DARK_GRAY}24h: n/a"
        }
        if (signal != null) {
            lore += "${ChatColor.DARK_GRAY}${trimCause(signal)}"
        }
        return ItemStack(material).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.GOLD}${company.name}"))
                lore(GuiText.legacy(lore))
            }
        }
    }

    private fun trimCause(signal: CompanySignalSnapshot): String {
        val max = 32
        val cause = signal.cause
        return if (cause.length <= max) cause else cause.substring(0, min(cause.length, max)) + "..."
    }

    private fun buildMacroItem(): ItemStack {
        val macro = signalService.getMacroSnapshot()
        val shocks = signalService.getActiveShocks()
        val lore = mutableListOf(
            "${ChatColor.GRAY}Inflation: ${String.format("%.3f", macro.inflation)}",
            "${ChatColor.GRAY}Liquidity: ${String.format("%.3f", macro.liquidity)}",
            "${ChatColor.GRAY}Growth: ${String.format("%.3f", macro.growth)}",
            "${ChatColor.GRAY}Sentiment: ${String.format("%.2f", macro.sentiment)}"
        )
        if (shocks.isNotEmpty()) {
            lore += "${ChatColor.YELLOW}Active Shocks:"
            shocks.take(3).forEach { shock ->
                lore += "${ChatColor.DARK_AQUA}${shock.sector}: ${String.format("%.2f", shock.magnitude * 100)}%"
            }
        }
        return ItemStack(Material.COMPASS).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.AQUA}Macro Signals"))
                lore(GuiText.legacy(lore))
            }
        }
    }

    private fun getDayChange(company: Company): Double? {
        return dayChangeCache.getOrPut(company.id) {
            val since = System.currentTimeMillis() - DAY_MILLIS
            val history = repository.fetchMarketHistorySince(company.id, since)
            if (history.isEmpty()) {
                return@getOrPut null
            }
            val baseline = history.first().price
            if (baseline <= 0.0) {
                return@getOrPut null
            }
            (company.sharePrice - baseline) / baseline
        }
    }

    private fun ItemStack.applyMeta(block: org.bukkit.inventory.meta.ItemMeta.() -> Unit) {
        val meta = itemMeta ?: return
        meta.block()
        itemMeta = meta
    }

    private fun formatPercent(value: Double): String = String.format("%.2f%%", value * 100)

    private fun formatSignedCurrency(value: Double): String {
        val formatted = formatCurrency(abs(value))
        return if (value >= 0) "+$formatted" else "-$formatted"
    }

    private fun formatCurrency(value: Double): String = "%,.2f".format(value)

    companion object {
        private const val DAY_MILLIS = 86_400_000L
    }
}
