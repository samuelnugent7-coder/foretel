package com.marketmc.gui

import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

abstract class BaseGui(
    val inventory: Inventory
) {
    open fun onOpen(player: Player) {}

    open fun onClose(player: Player) {}

    abstract fun handleClick(player: Player, event: InventoryClickEvent)
}
