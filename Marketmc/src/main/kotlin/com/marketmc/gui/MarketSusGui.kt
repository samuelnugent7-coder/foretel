package com.marketmc.gui

import com.marketmc.model.ExploitIncident
import com.marketmc.service.MarketComplianceService
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack

class MarketSusGui(
    private val service: MarketGuiService,
    private val complianceService: MarketComplianceService
) : BaseGui(Bukkit.createInventory(null, 54, service.getConfig().guiSusTitle)) {

    private var incidents: List<ExploitIncident> = emptyList()

    override fun onOpen(player: Player) {
        render()
    }

    private fun render() {
        incidents = complianceService.getRecentIncidents(45)
        inventory.clear()
        incidents.forEachIndexed { index, incident ->
            inventory.setItem(index, buildIncidentItem(incident))
        }
        inventory.setItem(53, ItemStack(Material.ARROW).apply {
            applyMeta { displayName(GuiText.legacy("${ChatColor.YELLOW}Back")) }
        })
    }

    override fun handleClick(player: Player, event: InventoryClickEvent) {
        val slot = event.rawSlot
        if (slot == 53) {
            service.openAdmin(player)
            return
        }
        val incident = incidents.getOrNull(slot) ?: return
        if (event.isLeftClick) {
            player.sendMessage("${ChatColor.GRAY}${playerName(incident.playerId)} ${incident.reason} (${String.format("%.2f", incident.severity)})")
        } else if (event.isRightClick) {
            complianceService.clearIncidents(incident.playerId)
            player.sendMessage("${ChatColor.GREEN}Cleared incident history for ${playerName(incident.playerId)}")
            render()
        }
    }

    private fun buildIncidentItem(incident: ExploitIncident): ItemStack {
        val head = ItemStack(Material.PAPER)
        val meta = head.itemMeta
        val severityColor = when {
            incident.severity >= 1.0 -> ChatColor.DARK_RED
            incident.severity >= 0.8 -> ChatColor.RED
            incident.severity >= 0.6 -> ChatColor.GOLD
            incident.severity >= 0.4 -> ChatColor.YELLOW
            else -> ChatColor.GREEN
        }
        meta?.displayName(GuiText.legacy("$severityColor${playerName(incident.playerId)}"))
        meta?.lore(
            GuiText.legacy(
                listOf(
                    "${ChatColor.GRAY}${incident.action} - ${incident.reason}",
                    "${ChatColor.GRAY}${incident.details}",
                    "${ChatColor.DARK_GRAY}Right-click to clear"
                )
            )
        )
        head.itemMeta = meta
        return head
    }

    private fun playerName(uuid: java.util.UUID): String {
        val offline: OfflinePlayer = Bukkit.getOfflinePlayer(uuid)
        return offline.name ?: uuid.toString().substring(0, 8)
    }

    private fun ItemStack.applyMeta(block: org.bukkit.inventory.meta.ItemMeta.() -> Unit) {
        val meta = itemMeta ?: return
        meta.block()
        itemMeta = meta
    }
}
