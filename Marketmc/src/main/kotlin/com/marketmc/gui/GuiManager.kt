package com.marketmc.gui

import com.marketmc.MarketMCPlugin
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GuiManager(private val plugin: MarketMCPlugin) : Listener {

    private val openGuis = ConcurrentHashMap<UUID, BaseGui>()

    init {
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    fun open(player: Player, gui: BaseGui) {
        openGuis[player.uniqueId] = gui
        gui.onOpen(player)
        player.openInventory(gui.inventory)
    }

    fun getOpenGui(player: Player): BaseGui? = openGuis[player.uniqueId]

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val gui = openGuis[player.uniqueId] ?: return
        val top = event.view.topInventory
        if (top != gui.inventory) {
            return
        }
        event.isCancelled = true
        gui.handleClick(player, event)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val gui = openGuis[player.uniqueId] ?: return
        if (event.inventory != gui.inventory) {
            return
        }
        openGuis.remove(player.uniqueId)
        gui.onClose(player)
    }
}
