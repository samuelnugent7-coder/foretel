package com.marketmc.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object GuiText {
    private val serializer = LegacyComponentSerializer.legacySection()

    fun legacy(text: String): Component = serializer.deserialize(text)

    fun legacy(lines: List<String>): List<Component> = lines.map { legacy(it) }
}
