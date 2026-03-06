package com.marketmc.task

import com.marketmc.service.MarketEngine
import org.bukkit.scheduler.BukkitRunnable

class MarketTickTask(private val marketEngine: MarketEngine) : BukkitRunnable() {
    override fun run() {
        marketEngine.processTick()
    }
}
