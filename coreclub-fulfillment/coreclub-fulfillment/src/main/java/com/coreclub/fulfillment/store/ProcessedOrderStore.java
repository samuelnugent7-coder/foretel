package com.coreclub.fulfillment.store;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks fulfilled order IDs to provide idempotency.
 */
public final class ProcessedOrderStore {
    private final JavaPlugin plugin;
    private final File storageFile;
    private final Set<String> processedOrderIds = new HashSet<>();

    public ProcessedOrderStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "processed-orders.txt");
        load();
    }

    public synchronized boolean isProcessed(String orderId) {
        return processedOrderIds.contains(orderId);
    }

    public synchronized void markProcessed(String orderId) {
        if (processedOrderIds.add(orderId)) {
            plugin.getLogger().info("Recording fulfilled order " + orderId);
            save();
        }
    }

    public synchronized void load() {
        if (!storageFile.exists()) {
            return;
        }
        try {
            List<String> saved = Files.readAllLines(storageFile.toPath(), StandardCharsets.UTF_8);
            processedOrderIds.clear();
            for (String line : saved) {
                if (line != null && !line.isBlank()) {
                    processedOrderIds.add(line.trim());
                }
            }
            plugin.getLogger().info("Loaded " + processedOrderIds.size() + " processed orders from disk");
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to read processed-orders.txt: " + exception.getMessage());
        }
    }

    public synchronized void save() {
        try {
            if (!storageFile.getParentFile().exists()) {
                storageFile.getParentFile().mkdirs();
            }
            Files.write(storageFile.toPath(), processedOrderIds, StandardCharsets.UTF_8);
            plugin.getLogger().info("Persisted processed-orders.txt with " + processedOrderIds.size() + " entries");
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to write processed-orders.txt: " + exception.getMessage());
        }
    }

    public synchronized Set<String> snapshot() {
        return Collections.unmodifiableSet(new HashSet<>(processedOrderIds));
    }
}
