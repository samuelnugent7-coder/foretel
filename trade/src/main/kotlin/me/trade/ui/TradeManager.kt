package me.trade.ui

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class TradeManager(private val plugin: TradePlugin, private val economy: EconomyBridge) {
    private val pendingRequests: MutableMap<UUID, MutableSet<UUID>> = ConcurrentHashMap()
    private val activeSessions: ConcurrentMap<UUID, TradeSession> = ConcurrentHashMap()
    private val lastRequestSent: MutableMap<UUID, Long> = ConcurrentHashMap()

    private val requestExpireTicks: Long = plugin.config.getLong("requests.expire-seconds", 60L) * 20L
    private val requestCooldownMillis: Long = plugin.config.getLong("requests.cooldown-seconds", 3L) * 1000L

    fun sendRequest(sender: Player, target: Player) {
        if (sender.uniqueId == target.uniqueId) {
            sender.sendMessage("§cYou can't trade with yourself.")
            return
        }
        if (isInTrade(sender) || isInTrade(target)) {
            sender.sendMessage("§cSomeone is already in a trade.")
            return
        }
        val now = System.currentTimeMillis()
        val last = lastRequestSent[sender.uniqueId] ?: 0L
        if (now - last < requestCooldownMillis) {
            val waitSec = ((requestCooldownMillis - (now - last)) / 1000) + 1
            sender.sendMessage("§ePlease wait ${waitSec}s before sending another request.")
            return
        }
        lastRequestSent[sender.uniqueId] = now

        val incoming = pendingRequests.getOrPut(target.uniqueId) { ConcurrentHashMap.newKeySet() }
        if (!incoming.add(sender.uniqueId)) {
            sender.sendMessage("§eYou already sent a trade request to ${target.name}.")
            return
        }

        sender.sendMessage("§aTrade request sent to ${target.name}. They must run /tradeaccept ${sender.name} within ${requestExpireTicks / 20}s.")
        target.sendMessage("§6${sender.name} wants to trade. Use §e/tradeaccept ${sender.name} §6to open the trade GUI.")

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val stillPending = pendingRequests[target.uniqueId]
            if (stillPending?.remove(sender.uniqueId) == true) {
                if (stillPending.isEmpty()) pendingRequests.remove(target.uniqueId)
                sender.sendMessage("§eYour trade request to ${target.name} expired.")
            }
        }, requestExpireTicks)
    }

    fun acceptRequest(accepter: Player, fromName: String?) {
        val incoming = pendingRequests[accepter.uniqueId]
        if (incoming.isNullOrEmpty()) {
            accepter.sendMessage("§cYou have no pending trade requests.")
            return
        }

        val requester = if (fromName == null) {
            incoming.mapNotNull { plugin.server.getPlayer(it) }.firstOrNull()
        } else {
            incoming.mapNotNull { plugin.server.getPlayer(it) }
                .firstOrNull { it.name.equals(fromName, ignoreCase = true) }
        }

        if (requester == null) {
            accepter.sendMessage("§cThat player is not online or didn't request to trade with you.")
            return
        }

        if (isInTrade(accepter) || isInTrade(requester)) {
            accepter.sendMessage("§cSomeone is already in a trade.")
            return
        }

        incoming.remove(requester.uniqueId)
        if (incoming.isEmpty()) pendingRequests.remove(accepter.uniqueId)

        startSession(requester, accepter)
    }

    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = activeSessions[player.uniqueId] ?: return
        session.handleClick(event)
    }

    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = activeSessions[player.uniqueId] ?: return
        session.handleDrag(event)
    }

    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val session = activeSessions[player.uniqueId] ?: return
        if (session.isAwaitingMoney(player)) {
            return
        }

        plugin.server.scheduler.runTask(plugin, Runnable {
            if (!session.isClosing) {
                session.cancel("Trade cancelled because a window was closed.", refund = true)
            }
        })
    }

    fun onQuit(player: Player) {
        cancelRequestsFor(player.uniqueId)
        activeSessions[player.uniqueId]?.cancel("Trade cancelled because a player left.", refund = true)
    }

    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val session = activeSessions[player.uniqueId] ?: return
        val plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message())
        if (session.handleMoneyChat(player, plainMessage)) {
            event.isCancelled = true
        }
    }

    fun shutdown() {
        pendingRequests.clear()
        activeSessions.values.distinct().forEach { it.cancel("Trade cancelled because the server is stopping.", refund = true) }
        activeSessions.clear()
    }

    fun endSession(session: TradeSession) {
        activeSessions.remove(session.left.player.uniqueId)
        activeSessions.remove(session.right.player.uniqueId)
    }

    fun isInTrade(player: Player): Boolean = activeSessions.containsKey(player.uniqueId)

    private fun startSession(requester: Player, receiver: Player) {
        val session = TradeSession(plugin, this, economy, requester, receiver)
        activeSessions[requester.uniqueId] = session
        activeSessions[receiver.uniqueId] = session
        session.open()
    }

    private fun cancelRequestsFor(playerId: UUID) {
        pendingRequests.remove(playerId)
        pendingRequests.values.forEach { it.remove(playerId) }
    }
}

class TradeCommand(private val manager: TradeManager) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can trade.")
            return true
        }
        if (args.isEmpty()) {
            sender.sendMessage("§eUsage: /$label <player>")
            return true
        }

        val target = Bukkit.getPlayerExact(args[0]) ?: Bukkit.getPlayer(args[0])
        if (target == null) {
            sender.sendMessage("§cThat player is not online.")
            return true
        }

        manager.sendRequest(sender, target)
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        if (args.size != 1) return mutableListOf()
        val lower = args[0].lowercase()
        return Bukkit.getOnlinePlayers()
            .filter { it.name.lowercase().startsWith(lower) && it != sender }
            .map { it.name }
            .toMutableList()
    }
}

class TradeAcceptCommand(private val manager: TradeManager) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can accept trades.")
            return true
        }
        val from = args.firstOrNull()
        manager.acceptRequest(sender, from)
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        if (args.size != 1 || sender !is Player) return mutableListOf()
        val lower = args[0].lowercase()
        return Bukkit.getOnlinePlayers()
            .filter { it.name.lowercase().startsWith(lower) && it != sender }
            .map { it.name }
            .toMutableList()
    }
}

class TradeListener(private val manager: TradeManager) : Listener {
    @EventHandler(priority = EventPriority.NORMAL)
    fun onInventoryClick(event: InventoryClickEvent) = manager.onInventoryClick(event)

    @EventHandler(priority = EventPriority.NORMAL)
    fun onInventoryDrag(event: InventoryDragEvent) = manager.onInventoryDrag(event)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) = manager.onInventoryClose(event)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) = manager.onQuit(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerKick(event: PlayerKickEvent) = manager.onQuit(event.player)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) = manager.onChat(event)
}
