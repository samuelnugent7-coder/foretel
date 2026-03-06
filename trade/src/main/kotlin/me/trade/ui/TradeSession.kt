package me.trade.ui

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

private val LEFT_SLOTS = setOf(0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21)
private val RIGHT_SLOTS = setOf(5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26)
private val SEPARATOR_SLOTS = setOf(4, 13, 22, 31, 40)
private const val CANCEL_SLOT = 49
private const val LEFT_MONEY_SLOT = 36
private const val RIGHT_MONEY_SLOT = 44
private const val LEFT_ACCEPT_SLOT = 45
private const val RIGHT_ACCEPT_SLOT = 53

enum class TradeSide { LEFT, RIGHT }

data class Participant(val player: Player, val side: TradeSide)

class TradeSession(
    private val plugin: TradePlugin,
    private val manager: TradeManager,
    private val economy: EconomyBridge,
    requester: Player,
    receiver: Player
) {
    val left = Participant(requester, TradeSide.LEFT)
    val right = Participant(receiver, TradeSide.RIGHT)

    private val participants = mapOf(
        left.player.uniqueId to left,
        right.player.uniqueId to right
    )

    private val inventory: Inventory

    private val readyPlayers = mutableSetOf<UUID>()
    private val moneyOffers = mutableMapOf<UUID, Double>()
    private val awaitingMoneyInput = mutableSetOf<UUID>()
    private var countdownTask: BukkitTask? = null

    private val countdownSeconds: Int = plugin.config.getInt("trade.countdown-seconds", 3).coerceAtLeast(1)

    var isClosing: Boolean = false
        private set

    init {
        val titleTemplate = plugin.config.getString("trade.gui-title", "Trade: {left} ↔ {right}")
        val title = ChatColor.translateAlternateColorCodes(
            '&',
            (titleTemplate ?: "Trade: {left} ↔ {right}")
                .replace("{left}", left.player.name)
                .replace("{right}", right.player.name)
        )
        inventory = Bukkit.createInventory(null, 54, title)
    }

    fun isAwaitingMoney(player: Player): Boolean = awaitingMoneyInput.contains(player.uniqueId)

    fun open() {
        setupBoard()
        left.player.openInventory(inventory)
        right.player.openInventory(inventory)
    }

    fun handleClick(event: InventoryClickEvent) {
        if (event.view.topInventory != inventory) return
        val player = event.whoClicked as? Player ?: return
        val participant = participants[player.uniqueId] ?: return

        when (event.clickedInventory) {
            inventory -> handleTopInventoryClick(event, participant)
            player.inventory -> handleBottomInventoryClick(event, participant)
            else -> event.isCancelled = true
        }
    }

    fun handleDrag(event: InventoryDragEvent) {
        if (event.view.topInventory != inventory) return
        val player = event.whoClicked as? Player ?: return
        val side = participants[player.uniqueId]?.side ?: return

        val allowed = if (side == TradeSide.LEFT) LEFT_SLOTS else RIGHT_SLOTS
        if (event.rawSlots.any { it < inventory.size && it !in allowed }) {
            event.isCancelled = true
            return
        }

        markChanged()
    }

    fun handleMoneyChat(player: Player, message: String): Boolean {
        if (!awaitingMoneyInput.contains(player.uniqueId)) return false

        if (message.equals("cancel", ignoreCase = true)) {
            awaitingMoneyInput.remove(player.uniqueId)
            player.sendMessage("§eMoney entry cancelled.")
            reopenInventory(player)
            return true
        }

        val amount = message.toDoubleOrNull()
        if (amount == null || amount < 0.0 || !amount.isFinite()) {
            player.sendMessage("§cEnter a valid non-negative number or type 'cancel'.")
            reopenInventory(player)
            return true
        }

        awaitingMoneyInput.remove(player.uniqueId)
        plugin.server.scheduler.runTask(plugin, Runnable {
            setMoney(player, amount)
            reopenInventory(player)
        })

        player.sendMessage("§eSet your money offer to ${economy.format(amount)}.")
        return true
    }

    fun cancel(reason: String, refund: Boolean) {
        if (isClosing) return
        isClosing = true
        stopCountdown()

        if (refund) {
            refundMoney()
            returnItems(left)
            returnItems(right)
        }

        left.player.closeInventory()
        right.player.closeInventory()

        left.player.sendMessage("§c$reason")
        right.player.sendMessage("§c$reason")

        manager.endSession(this)
    }

    private fun handleTopInventoryClick(event: InventoryClickEvent, participant: Participant) {
        val slot = event.rawSlot
        val side = participant.side
        val allowedSlots = if (side == TradeSide.LEFT) LEFT_SLOTS else RIGHT_SLOTS
        val otherAllowedSlots = if (side == TradeSide.LEFT) RIGHT_SLOTS else LEFT_SLOTS

        when (slot) {
            in allowedSlots -> {
                if (event.action == InventoryAction.COLLECT_TO_CURSOR || event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    event.isCancelled = true
                    return
                }
                markChanged()
            }
            in otherAllowedSlots, in SEPARATOR_SLOTS, CANCEL_SLOT -> {
                event.isCancelled = true
                if (slot == CANCEL_SLOT) {
                    cancel("Trade cancelled.", refund = true)
                }
            }
            LEFT_ACCEPT_SLOT -> if (side == TradeSide.LEFT) {
                event.isCancelled = true
                toggleReady(participant)
            } else event.isCancelled = true
            RIGHT_ACCEPT_SLOT -> if (side == TradeSide.RIGHT) {
                event.isCancelled = true
                toggleReady(participant)
            } else event.isCancelled = true
            LEFT_MONEY_SLOT -> if (side == TradeSide.LEFT) {
                event.isCancelled = true
                promptMoney(participant.player)
            } else event.isCancelled = true
            RIGHT_MONEY_SLOT -> if (side == TradeSide.RIGHT) {
                event.isCancelled = true
                promptMoney(participant.player)
            } else event.isCancelled = true
            else -> event.isCancelled = true
        }
    }

    private fun handleBottomInventoryClick(event: InventoryClickEvent, participant: Participant) {
        if (event.isShiftClick) {
            event.isCancelled = true
            return
        }
        // Allow normal pickup/place in player inventory
    }

    private fun toggleReady(participant: Participant) {
        if (awaitingMoneyInput.contains(participant.player.uniqueId)) {
            participant.player.sendMessage("§cFinish entering your money amount first.")
            return
        }

        val nowReady = if (readyPlayers.contains(participant.player.uniqueId)) {
            readyPlayers.remove(participant.player.uniqueId)
            false
        } else {
            readyPlayers.add(participant.player.uniqueId)
            true
        }

        updateReadyButtons()

        if (nowReady) {
            participant.player.sendMessage("§aMarked as ready.")
        } else {
            participant.player.sendMessage("§eYou are no longer ready.")
        }

        if (readyPlayers.contains(left.player.uniqueId) && readyPlayers.contains(right.player.uniqueId)) {
            startCountdown()
        } else {
            stopCountdown()
        }
    }

    private fun promptMoney(player: Player) {
        if (!economy.isAvailable) {
            player.sendMessage("§cMoney trading is disabled because no economy provider is registered.")
            return
        }
        awaitingMoneyInput.add(player.uniqueId)
        player.closeInventory()
        player.sendMessage("§aType the amount of money you want to offer (e.g. 1000 or 50000). Type 'cancel' to stop.")
    }

    private fun setMoney(player: Player, amount: Double) {
        if (!economy.isAvailable) {
            player.sendMessage("§cMoney trading is disabled because no economy provider is registered.")
            return
        }
        if (!amount.isFinite() || amount < 0.0) {
            player.sendMessage("§cInvalid amount.")
            return
        }
        val current = moneyOffers[player.uniqueId] ?: 0.0
        val diff = amount - current

        if (diff > 0) {
            val success = economy.withdraw(player, diff)
            if (!success) {
                return
            }
        } else if (diff < 0) {
            economy.deposit(player, -diff)
        }

        moneyOffers[player.uniqueId] = amount
        updateMoneyButtons()
        markChanged()
    }

    private fun startCountdown() {
        if (countdownTask != null) return
        val totalSeconds = countdownSeconds
        countdownTask = object : BukkitRunnable() {
            var secondsLeft = totalSeconds
            override fun run() {
                if (!readyPlayers.contains(left.player.uniqueId) || !readyPlayers.contains(right.player.uniqueId)) {
                    stopCountdown()
                    return
                }
                if (secondsLeft <= 0) {
                    stopCountdown()
                    completeTrade()
                    return
                }
                updateAcceptButtonText(secondsLeft)
                secondsLeft--
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    private fun stopCountdown() {
        countdownTask?.cancel()
        countdownTask = null
        updateAcceptButtonsStatic()
    }

    private fun completeTrade() {
        if (isClosing) return
        isClosing = true

        val leftItems = takeItems(TradeSide.LEFT)
        val rightItems = takeItems(TradeSide.RIGHT)

        giveItems(right.player, leftItems)
        giveItems(left.player, rightItems)

        val leftMoney = moneyOffers[left.player.uniqueId] ?: 0.0
        val rightMoney = moneyOffers[right.player.uniqueId] ?: 0.0

        if (economy.isAvailable) {
            if (leftMoney > 0) economy.deposit(right.player, leftMoney)
            if (rightMoney > 0) economy.deposit(left.player, rightMoney)
        }

        left.player.sendMessage("§aTrade completed!")
        right.player.sendMessage("§aTrade completed!")

        left.player.closeInventory()
        right.player.closeInventory()
        manager.endSession(this)
    }

    private fun returnItems(participant: Participant) {
        val items = takeItems(participant.side)
        giveItems(participant.player, items)
    }

    private fun giveItems(player: Player, items: List<ItemStack>) {
        if (items.isEmpty()) return
        val leftovers = player.inventory.addItem(*items.toTypedArray())
        leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
    }

    private fun takeItems(side: TradeSide): List<ItemStack> {
        val slots = if (side == TradeSide.LEFT) LEFT_SLOTS else RIGHT_SLOTS
        val items = mutableListOf<ItemStack>()
        slots.forEach { slot ->
            val item = inventory.getItem(slot)
            if (item != null && item.type != Material.AIR) {
                items.add(item.clone())
                inventory.clear(slot)
            }
        }
        return items
    }

    private fun markChanged() {
        if (readyPlayers.isNotEmpty()) {
            readyPlayers.clear()
            stopCountdown()
            updateReadyButtons()
            left.player.sendMessage("§eTrade contents changed; readied players reset.")
            right.player.sendMessage("§eTrade contents changed; readied players reset.")
        }
    }

    private fun refundMoney() {
        if (!economy.isAvailable) return
        moneyOffers.forEach { (uuid, amount) ->
            val player = Bukkit.getPlayer(uuid)
            if (player != null && amount > 0) {
                economy.deposit(player, amount)
            }
        }
    }

    private fun setupBoard() {
        val filler = item(Material.GRAY_STAINED_GLASS_PANE, " ")
        for (i in 0 until inventory.size) {
            inventory.setItem(i, filler)
        }

        SEPARATOR_SLOTS.forEach { slot -> inventory.setItem(slot, item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ")) }

        LEFT_SLOTS.forEach { inventory.clear(it) }
        RIGHT_SLOTS.forEach { inventory.clear(it) }

        inventory.setItem(CANCEL_SLOT, item(Material.BARRIER, "§cCancel Trade", listOf("§7Click to cancel and refund everything.")))

        updateReadyButtons()
        updateMoneyButtons()
    }

    private fun updateMoneyButtons() {
        if (!economy.isAvailable) {
            val disabled = item(
                Material.BARRIER,
                "§cMoney disabled",
                listOf("§7No Vault economy detected.")
            )
            inventory.setItem(LEFT_MONEY_SLOT, disabled)
            inventory.setItem(RIGHT_MONEY_SLOT, disabled.clone())
            return
        }
        val leftAmount = moneyOffers[left.player.uniqueId] ?: 0.0
        val rightAmount = moneyOffers[right.player.uniqueId] ?: 0.0

        inventory.setItem(LEFT_MONEY_SLOT, moneyButton("Your money offer", leftAmount))
        inventory.setItem(RIGHT_MONEY_SLOT, moneyButton("Your money offer", rightAmount))
    }

    private fun moneyButton(label: String, amount: Double): ItemStack {
        val lines = listOf(
            "§f$label",
            "§e${economy.format(amount)}",
            "§7Click to set a new amount"
        )
        return item(Material.PAPER, "§6Money", lines)
    }

    private fun updateReadyButtons() {
        updateAcceptButtonsStatic()
    }

    private fun updateAcceptButtonsStatic() {
        val leftReady = readyPlayers.contains(left.player.uniqueId)
        val rightReady = readyPlayers.contains(right.player.uniqueId)
        inventory.setItem(LEFT_ACCEPT_SLOT, acceptButton(leftReady, left.player.name))
        inventory.setItem(RIGHT_ACCEPT_SLOT, acceptButton(rightReady, right.player.name))
    }

    private fun reopenInventory(player: Player) {
        if (!player.isOnline) return
        player.openInventory(inventory)
    }

    private fun updateAcceptButtonText(secondsLeft: Int) {
        val title = "§eFinalizing in ${secondsLeft}s"
        val button = item(Material.YELLOW_STAINED_GLASS_PANE, title, listOf("§7Both players are ready"))
        inventory.setItem(LEFT_ACCEPT_SLOT, button)
        inventory.setItem(RIGHT_ACCEPT_SLOT, button.clone())
    }

    private fun acceptButton(ready: Boolean, ownerName: String): ItemStack {
        val material = if (ready) Material.LIME_STAINED_GLASS_PANE else Material.RED_STAINED_GLASS_PANE
        val title = if (ready) "§aReady" else "§cNot Ready"
        val lore = listOf("§7Click to toggle", "§f$ownerName")
        return item(material, title, lore)
    }

    private fun item(material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
        val stack = ItemStack(material)
        val meta = stack.itemMeta ?: return stack
        meta.setDisplayName(name)
        if (lore.isNotEmpty()) meta.lore = lore
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        stack.itemMeta = meta
        return stack
    }
}
