package com.marketmc.gui

import com.marketmc.model.Company
import com.marketmc.service.EconomyService
import com.marketmc.service.MarketComplianceService
import com.marketmc.service.MarketSignalService
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import java.util.concurrent.ThreadLocalRandom

class MarketAdminGui(
    private val service: MarketGuiService,
    private val economyService: EconomyService,
    private val signalService: MarketSignalService,
    private val complianceService: MarketComplianceService
) : BaseGui(Bukkit.createInventory(null, 45, service.getConfig().guiAdminTitle)) {

    private val securitySlots = mutableMapOf<Int, SecurityField>()

    override fun onOpen(player: Player) {
        render()
    }

    private fun render() {
        inventory.clear()
        securitySlots.clear()
        inventory.setItem(10, economyItem())
        inventory.setItem(11, button(Material.GOLD_BLOCK, "Deposit +50k"))
        inventory.setItem(12, button(Material.COAL_BLOCK, "Withdraw 50k"))
        inventory.setItem(14, button(Material.BLAZE_ROD, "Boost Market"))
        inventory.setItem(15, button(Material.BEACON, "Support Company"))
        inventory.setItem(16, button(Material.TNT, "Shock Sector"))
        renderSecuritySection()
        inventory.setItem(31, button(Material.BOOK, "Market SUS Board"))
    }

    override fun handleClick(player: Player, event: InventoryClickEvent) {
        val securityField = securitySlots[event.rawSlot]
        if (securityField != null) {
            promptSecurityValue(player, securityField)
            return
        }
        when (event.rawSlot) {
            11 -> handleEconomyDeposit(player)
            12 -> handleEconomyWithdraw(player)
            14 -> handleMarketBoost(player)
            15 -> handleTreasurySupport(player)
            16 -> handleShock(player)
            31 -> service.openSusBoard(player)
        }
    }

    private fun renderSecuritySection() {
        val snapshot = service.plugin.getSecuritySettings().snapshot()
        placeSecurityItem(
            slot = 19,
            field = SecurityField.TRADE_COOLDOWN,
            material = Material.CLOCK,
            label = "${ChatColor.GOLD}Trade Cooldown",
            value = snapshot.describeTradeCooldown(),
            detail = "${ChatColor.GRAY}Delay between any trades"
        )
        placeSecurityItem(
            slot = 20,
            field = SecurityField.BUY_SELL_COOLDOWN,
            material = Material.COMPARATOR,
            label = "${ChatColor.GOLD}Buy→Sell Cooldown",
            value = snapshot.describeBuySellCooldown(),
            detail = "${ChatColor.GRAY}Applies right after selling"
        )
        placeSecurityItem(
            slot = 21,
            field = SecurityField.HOLDING_PERIOD,
            material = Material.CHAIN,
            label = "${ChatColor.GOLD}Holding Period",
            value = snapshot.describeHoldingPeriod(),
            detail = "${ChatColor.GRAY}How long shares stay locked"
        )
        placeSecurityItem(
            slot = 22,
            field = SecurityField.RAPID_THRESHOLD,
            material = Material.LEVER,
            label = "${ChatColor.AQUA}Rapid Trade Window",
            value = snapshot.describeRapidThreshold(),
            detail = "${ChatColor.GRAY}Window size for rapid checks"
        )
        placeSecurityItem(
            slot = 23,
            field = SecurityField.RAPID_WINDOW,
            material = Material.HOPPER,
            label = "${ChatColor.AQUA}Rapid Trade Count",
            value = snapshot.describeRapidWindow(),
            detail = "${ChatColor.GRAY}Trades allowed in window"
        )
        placeSecurityItem(
            slot = 24,
            field = SecurityField.VOLUME_PERCENT,
            material = Material.BARREL,
            label = "${ChatColor.AQUA}Volume Per Minute",
            value = snapshot.describeVolumePercent(),
            detail = "${ChatColor.GRAY}Max float per minute"
        )
    }

    private fun placeSecurityItem(
        slot: Int,
        field: SecurityField,
        material: Material,
        label: String,
        value: String,
        detail: String
    ) {
        securitySlots[slot] = field
        inventory.setItem(slot, ItemStack(material).apply {
            applyMeta {
                displayName(GuiText.legacy(label))
                lore(GuiText.legacy(listOf(
                    "${ChatColor.GRAY}Current: ${ChatColor.YELLOW}$value",
                    detail,
                    "${ChatColor.DARK_GRAY}Click to edit"
                )))
            }
        })
    }

    private fun promptSecurityValue(player: Player, field: SecurityField) {
        val settings = service.plugin.getSecuritySettings()
        when (field) {
            SecurityField.TRADE_COOLDOWN -> requestSeconds(
                player,
                "Enter global trade cooldown (seconds):",
                min = 0,
                max = 86_400
            ) { value ->
                settings.setTradeCooldownSeconds(value)
                player.sendMessage("${ChatColor.GREEN}Trade cooldown updated to ${value}s.")
            }
            SecurityField.BUY_SELL_COOLDOWN -> requestSeconds(
                player,
                "Enter cooldown after selling before buys reopen (seconds):",
                min = 0,
                max = 86_400
            ) { value ->
                settings.setBuySellCooldownSeconds(value)
                player.sendMessage("${ChatColor.GREEN}Buy→sell cooldown updated to ${value}s.")
            }
            SecurityField.HOLDING_PERIOD -> requestSeconds(
                player,
                "Enter holding period before shares unlock (seconds):",
                min = 0,
                max = 86_400
            ) { value ->
                settings.setHoldingPeriodSeconds(value)
                player.sendMessage("${ChatColor.GREEN}Holding period updated to ${value}s.")
            }
            SecurityField.RAPID_THRESHOLD -> requestSeconds(
                player,
                "Enter rapid-trade window length (seconds):",
                min = 1,
                max = 86_400
            ) { value ->
                settings.setRapidTradeThresholdSeconds(value)
                player.sendMessage("${ChatColor.GREEN}Rapid-trade window updated to ${value}s.")
            }
            SecurityField.RAPID_WINDOW -> requestInteger(
                player,
                "Enter rapid-trade max entries per window:",
                min = 1,
                max = 200
            ) { value ->
                settings.setRapidTradeMaxPerWindow(value)
                player.sendMessage("${ChatColor.GREEN}Rapid-trade cap updated to $value trades.")
            }
            SecurityField.VOLUME_PERCENT -> requestPercent(
                player,
                "Enter max % of supply per minute (example: 12 for 12%):",
                min = 0.01,
                max = 100.0
            ) { percent ->
                settings.setMaxSharesPerMinutePercent(percent / 100.0)
                player.sendMessage("${ChatColor.GREEN}Volume cap updated to ${String.format("%.2f", percent)}% per minute.")
            }
        }
    }

    private fun requestSeconds(player: Player, prompt: String, min: Int, max: Int, onApply: (Int) -> Unit) {
        service.getInputService().requestLong(
            player = player,
            prompt = GuiText.legacy("${ChatColor.AQUA}$prompt"),
            min = min.toLong(),
            max = max.toLong(),
            onResult = { value ->
                onApply(value.toInt())
                service.openAdmin(player)
            },
            onCancel = { service.openAdmin(player) }
        )
    }

    private fun requestInteger(player: Player, prompt: String, min: Int, max: Int, onApply: (Int) -> Unit) {
        service.getInputService().requestLong(
            player = player,
            prompt = GuiText.legacy("${ChatColor.AQUA}$prompt"),
            min = min.toLong(),
            max = max.toLong(),
            onResult = { value ->
                onApply(value.toInt())
                service.openAdmin(player)
            },
            onCancel = { service.openAdmin(player) }
        )
    }

    private fun requestPercent(player: Player, prompt: String, min: Double, max: Double, onApply: (Double) -> Unit) {
        service.getInputService().requestDouble(
            player = player,
            prompt = GuiText.legacy("${ChatColor.AQUA}$prompt"),
            min = min,
            max = max,
            onResult = { value ->
                onApply(value)
                service.openAdmin(player)
            },
            onCancel = { service.openAdmin(player) }
        )
    }

    private fun handleEconomyDeposit(player: Player) {
        val vault = service.plugin.getVaultHook()
        if (!vault.withdraw(player, 50_000.0)) {
            player.sendMessage("${ChatColor.RED}You need 50k to deposit into the pool.")
        } else {
            economyService.deposit(50_000.0)
            player.sendMessage("${ChatColor.GREEN}Transferred 50k into the economy pool.")
            render()
        }
    }

    private fun handleEconomyWithdraw(player: Player) {
        val withdrawn = economyService.withdraw(50_000.0)
        if (withdrawn <= 0) {
            player.sendMessage("${ChatColor.RED}Pool is empty.")
        } else {
            service.plugin.getVaultHook().deposit(player, withdrawn)
            player.sendMessage("${ChatColor.YELLOW}Received ${formatCurrency(withdrawn)} from the pool.")
            render()
        }
    }

    private fun handleMarketBoost(player: Player) {
        try {
            val amount = economyService.performMarketBoost(0.02)
            signalService.pushShock("*", 0.03, "Admin liquidity boost")
            player.sendMessage("${ChatColor.GREEN}Injected ${formatCurrency(amount)} in liquidity.")
        } catch (ex: IllegalStateException) {
            player.sendMessage("${ChatColor.RED}${ex.message}")
        }
    }

    private fun handleTreasurySupport(player: Player) {
        val company = pickCompany()
        if (company == null) {
            player.sendMessage("${ChatColor.RED}No companies to support.")
        } else {
            val payout = economyService.supportCompany(company)
            player.sendMessage("${ChatColor.GREEN}Sent ${formatCurrency(payout)} to ${company.name} treasury.")
        }
    }

    private fun handleShock(player: Player) {
        val sector = pickSector()
        val magnitude = ThreadLocalRandom.current().nextDouble(-0.05, 0.05)
        signalService.pushShock(sector, magnitude, "Admin shock")
        player.sendMessage("${ChatColor.YELLOW}Applied ${String.format("%.2f", magnitude * 100)}% shock to $sector")
    }

    private fun economyItem(): ItemStack {
        val balance = economyService.getBalance()
        val cooldown = economyService.remainingBoostCooldown() / 1000
        return ItemStack(Material.NETHER_STAR).apply {
            applyMeta {
                displayName(GuiText.legacy("${ChatColor.GOLD}Economy Pool"))
                lore(GuiText.legacy(listOf(
                    "${ChatColor.GRAY}Balance: ${formatCurrency(balance)}",
                    "${ChatColor.GRAY}Boost CD: ${cooldown}s"
                )))
            }
        }
    }

    private fun button(material: Material, label: String): ItemStack = ItemStack(material).apply {
        applyMeta { displayName(GuiText.legacy("${ChatColor.AQUA}$label")) }
    }

    private fun pickCompany(): Company? {
        val companies = service.getCompanyService().getCompanies()
        if (companies.isEmpty()) return null
        return companies.random()
    }

    private fun pickSector(): String {
        val sectors = service.getCompanyService().getCompanies().map { it.sector }.filter { it.isNotBlank() }
        return sectors.randomOrNull() ?: "*"
    }

    private fun ItemStack.applyMeta(block: org.bukkit.inventory.meta.ItemMeta.() -> Unit) {
        val meta = itemMeta ?: return
        meta.block()
        itemMeta = meta
    }

    private fun formatCurrency(value: Double): String = "%,.2f".format(value)

    private enum class SecurityField {
        TRADE_COOLDOWN,
        BUY_SELL_COOLDOWN,
        HOLDING_PERIOD,
        RAPID_THRESHOLD,
        RAPID_WINDOW,
        VOLUME_PERCENT
    }
}
