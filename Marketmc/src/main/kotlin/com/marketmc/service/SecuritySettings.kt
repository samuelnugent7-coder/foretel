package com.marketmc.service

import com.marketmc.config.PluginConfig

/**
 * Holds runtime-adjustable security and cooldown settings that can be tuned via the admin GUI
 * without touching config.yml. Values are clamped to reasonable ranges to protect gameplay.
 */
class SecuritySettings(config: PluginConfig) {
    @Volatile private var tradeCooldownSeconds = config.tradeCooldownSeconds
    @Volatile private var buySellCooldownSeconds = config.buySellCooldownSeconds
    @Volatile private var holdingPeriodSeconds = config.holdingPeriodSeconds
    @Volatile private var rapidTradeThresholdSeconds = config.rapidTradeThresholdSeconds
    @Volatile private var rapidTradeMaxPerWindow = config.rapidTradeMaxPerWindow
    @Volatile private var maxSharesPerMinutePercent = config.maxSharesPerMinutePercent

    fun snapshot(): SecuritySettingsSnapshot = SecuritySettingsSnapshot(
        tradeCooldownSeconds = tradeCooldownSeconds,
        buySellCooldownSeconds = buySellCooldownSeconds,
        holdingPeriodSeconds = holdingPeriodSeconds,
        rapidTradeThresholdSeconds = rapidTradeThresholdSeconds,
        rapidTradeMaxPerWindow = rapidTradeMaxPerWindow,
        maxSharesPerMinutePercent = maxSharesPerMinutePercent
    )

    fun setTradeCooldownSeconds(value: Int) {
        tradeCooldownSeconds = value.coerceIn(0, MAX_SECONDS)
    }

    fun setBuySellCooldownSeconds(value: Int) {
        buySellCooldownSeconds = value.coerceIn(0, MAX_SECONDS)
    }

    fun setHoldingPeriodSeconds(value: Int) {
        holdingPeriodSeconds = value.coerceIn(0, MAX_SECONDS)
    }

    fun setRapidTradeThresholdSeconds(value: Int) {
        rapidTradeThresholdSeconds = value.coerceIn(1, MAX_SECONDS)
    }

    fun setRapidTradeMaxPerWindow(value: Int) {
        rapidTradeMaxPerWindow = value.coerceIn(1, 200)
    }

    fun setMaxSharesPerMinutePercent(value: Double) {
        maxSharesPerMinutePercent = value.coerceIn(0.0001, 1.0)
    }

    fun tradeCooldownSeconds(): Int = tradeCooldownSeconds

    fun buySellCooldownSeconds(): Int = buySellCooldownSeconds

    fun holdingPeriodSeconds(): Int = holdingPeriodSeconds

    fun rapidTradeThresholdSeconds(): Int = rapidTradeThresholdSeconds

    fun rapidTradeMaxPerWindow(): Int = rapidTradeMaxPerWindow

    fun maxSharesPerMinutePercent(): Double = maxSharesPerMinutePercent

    companion object {
        private const val MAX_SECONDS = 86_400 // 24h cap keeps values sane
    }
}

data class SecuritySettingsSnapshot(
    val tradeCooldownSeconds: Int,
    val buySellCooldownSeconds: Int,
    val holdingPeriodSeconds: Int,
    val rapidTradeThresholdSeconds: Int,
    val rapidTradeMaxPerWindow: Int,
    val maxSharesPerMinutePercent: Double
) {
    fun describeTradeCooldown(): String = "${tradeCooldownSeconds}s"
    fun describeBuySellCooldown(): String = "${buySellCooldownSeconds}s"
    fun describeHoldingPeriod(): String = "${holdingPeriodSeconds}s"
    fun describeRapidThreshold(): String = "${rapidTradeThresholdSeconds}s"
    fun describeRapidWindow(): String = "${rapidTradeMaxPerWindow} trades"
    fun describeVolumePercent(): String = "${"%.2f".format(maxSharesPerMinutePercent * 100)}%"
}
