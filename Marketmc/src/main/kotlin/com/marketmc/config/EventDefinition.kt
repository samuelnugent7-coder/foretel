package com.marketmc.config

data class EventDefinition(
    val key: String,
    val description: String,
    val multiplier: Double,
    val sectors: List<String>
)
