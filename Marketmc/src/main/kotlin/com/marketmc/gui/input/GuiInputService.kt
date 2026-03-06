package com.marketmc.gui.input

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GuiInputService(private val plugin: JavaPlugin) : Listener {

    private data class PendingDouble(
        val min: Double,
        val max: Double?,
        val callback: (Double) -> Unit,
        val cancelCallback: (() -> Unit)?
    )

    private data class PendingLong(
        val min: Long,
        val max: Long?,
        val callback: (Long) -> Unit,
        val cancelCallback: (() -> Unit)?
    )

    private val doubleInputs = ConcurrentHashMap<UUID, PendingDouble>()
    private val longInputs = ConcurrentHashMap<UUID, PendingLong>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    fun requestDouble(
        player: Player,
        prompt: Component,
        min: Double = 0.0,
        max: Double? = null,
        onResult: (Double) -> Unit,
        onCancel: (() -> Unit)? = null,
        closeInventory: Boolean = true
    ) {
        if (closeInventory) {
            player.closeInventory()
        }
        player.sendMessage(prompt)
        player.sendMessage(Component.text("${ChatColor.GRAY}Type 'cancel' to abort."))
        longInputs.remove(player.uniqueId)
        doubleInputs[player.uniqueId] = PendingDouble(min, max, onResult, onCancel)
    }

    fun requestLong(
        player: Player,
        prompt: Component,
        min: Long = 1,
        max: Long? = null,
        onResult: (Long) -> Unit,
        onCancel: (() -> Unit)? = null
    ) {
        player.closeInventory()
        player.sendMessage(prompt)
        player.sendMessage(Component.text("${ChatColor.GRAY}Type 'cancel' to abort."))
        doubleInputs.remove(player.uniqueId)
        longInputs[player.uniqueId] = PendingLong(min, max, onResult, onCancel)
    }

    fun clear(player: Player) {
        doubleInputs.remove(player.uniqueId)
        longInputs.remove(player.uniqueId)
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val playerId = event.player.uniqueId
        val doublePending = doubleInputs[playerId]
        val longPending = longInputs[playerId]
        if (doublePending == null && longPending == null) {
            return
        }

        event.isCancelled = true
        val message = event.message.trim()
        if (message.equals("cancel", ignoreCase = true)) {
            doubleInputs.remove(playerId)
            longInputs.remove(playerId)
            val cancelCallback = doublePending?.cancelCallback ?: longPending?.cancelCallback
            Bukkit.getScheduler().runTask(plugin, Runnable {
                cancelCallback?.invoke()
                event.player.sendMessage("${ChatColor.YELLOW}Input cancelled.")
            })
            return
        }

        if (doublePending != null) {
            handleDoubleInput(event.player, doublePending, message)
            return
        }
        if (longPending != null) {
            handleLongInput(event.player, longPending, message)
        }
    }

    private fun handleDoubleInput(player: Player, pending: PendingDouble, message: String) {
        val value = message.toDoubleOrNull()
        if (value == null) {
            player.sendMessage("${ChatColor.RED}Enter a numeric value or type cancel.")
            return
        }
        if (value < pending.min) {
            player.sendMessage("${ChatColor.RED}Value must be at least ${pending.min}.")
            return
        }
        if (pending.max != null && value > pending.max) {
            player.sendMessage("${ChatColor.RED}Value must be at most ${pending.max}.")
            return
        }
        doubleInputs.remove(player.uniqueId)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            pending.callback.invoke(value)
        })
    }

    private fun handleLongInput(player: Player, pending: PendingLong, message: String) {
        val value = message.toLongOrNull()
        if (value == null) {
            player.sendMessage("${ChatColor.RED}Enter a whole number or type cancel.")
            return
        }
        if (value < pending.min) {
            player.sendMessage("${ChatColor.RED}Value must be at least ${pending.min}.")
            return
        }
        if (pending.max != null && value > pending.max) {
            player.sendMessage("${ChatColor.RED}Value must be at most ${pending.max}.")
            return
        }
        longInputs.remove(player.uniqueId)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            pending.callback.invoke(value)
        })
    }
}
