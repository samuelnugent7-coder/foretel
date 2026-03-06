package com.marketmc.task

import com.marketmc.config.PluginConfig
import com.marketmc.service.EventService
import org.bukkit.scheduler.BukkitRunnable
import java.util.concurrent.ThreadLocalRandom

class EconomicEventTask(
    private val eventService: EventService,
    private val config: PluginConfig
) : BukkitRunnable() {
    private var nextTriggerAt: Long = 0

    init {
        scheduleNext(System.currentTimeMillis())
    }

    override fun run() {
        val now = System.currentTimeMillis()
        if (now >= nextTriggerAt) {
            eventService.triggerRandomEvent()
            scheduleNext(now)
        }
        eventService.cleanExpiredEvents()
    }

    private fun scheduleNext(now: Long) {
        val minMs = config.eventMinIntervalMinutes.coerceAtLeast(1) * 60L * 1000L
        val maxMs = config.eventMaxIntervalMinutes.coerceAtLeast(config.eventMinIntervalMinutes) * 60L * 1000L
        val range = (maxMs - minMs).coerceAtLeast(0)
        val jitter = if (range > 0) ThreadLocalRandom.current().nextLong(range + 1) else 0
        nextTriggerAt = now + minMs + jitter
    }
}
