package com.marketmc.util

import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64

object ItemSerializer {
    @JvmStatic
    fun serialize(item: ItemStack): String {
        try {
            ByteArrayOutputStream().use { outputStream ->
                BukkitObjectOutputStream(outputStream).use { dataOutput ->
                    dataOutput.writeObject(item)
                }
                return Base64.getEncoder().encodeToString(outputStream.toByteArray())
            }
        } catch (ex: IOException) {
            throw IllegalStateException("Failed to serialize item", ex)
        }
    }

    @JvmStatic
    fun deserialize(data: String): ItemStack {
        val bytes = Base64.getDecoder().decode(data)
        try {
            ByteArrayInputStream(bytes).use { inputStream ->
                BukkitObjectInputStream(inputStream).use { objectInput ->
                    val obj = objectInput.readObject()
                    return obj as ItemStack
                }
            }
        } catch (ex: IOException) {
            throw IllegalStateException("Failed to deserialize item", ex)
        } catch (ex: ClassNotFoundException) {
            throw IllegalStateException("Failed to deserialize item", ex)
        }
    }
}
