package com.coreclub.fulfillment.store;

import com.coreclub.fulfillment.model.ManagedPackage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class PackageRegistry {
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    private final JavaPlugin plugin;
    private final File storageFile;
    private Map<String, ManagedPackage> packages = Collections.emptyMap();

    public PackageRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "web-packages.json");
    }

    public synchronized void load() {
        if (!storageFile.exists()) {
            packages = Collections.emptyMap();
            plugin.getLogger().info("web-packages.json not found; starting with an empty registry");
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(storageFile.toPath(), StandardCharsets.UTF_8)) {
            RegistryPayload payload = GSON.fromJson(reader, RegistryPayload.class);
            if (payload == null || payload.packages == null) {
                packages = Collections.emptyMap();
                return;
            }
            packages = payload.packages.stream()
                .filter(pkg -> pkg != null && pkg.isValid())
                .collect(Collectors.toUnmodifiableMap(
                    pkg -> pkg.sku().toLowerCase(Locale.ROOT),
                    pkg -> pkg
                ));
            plugin.getLogger().info("Loaded " + packages.size() + " managed packages from web-packages.json");
        } catch (IOException exception) {
            plugin.getLogger().warning("Unable to read web-packages.json: " + exception.getMessage());
            packages = Collections.emptyMap();
        }
    }

    public synchronized void replacePackages(List<ManagedPackage> incoming) throws IOException {
        Map<String, ManagedPackage> updated = new HashMap<>();
        if (incoming != null) {
            for (ManagedPackage pkg : incoming) {
                if (pkg != null && pkg.isValid()) {
                    updated.put(pkg.sku().toLowerCase(Locale.ROOT), pkg);
                }
            }
        }
        packages = Collections.unmodifiableMap(updated);
        plugin.getLogger().info("Replacing package catalog with " + packages.size() + " entries");
        persist();
    }

    public synchronized Map<String, List<String>> toCommandMap() {
        Map<String, List<String>> map = new HashMap<>();
        packages.forEach((sku, pkg) -> map.put(sku, pkg.commandList()));
        return map;
    }

    public synchronized List<ManagedPackage> snapshot() {
        return new ArrayList<>(packages.values());
    }

    private void persist() throws IOException {
        if (!storageFile.getParentFile().exists()) {
            storageFile.getParentFile().mkdirs();
        }
        RegistryPayload payload = new RegistryPayload();
        payload.packages = new ArrayList<>(packages.values());
        payload.updatedAt = Instant.now().toString();
        try (BufferedWriter writer = Files.newBufferedWriter(storageFile.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(payload, writer);
        }
        plugin.getLogger().info("Persisted updated package catalog to web-packages.json");
    }

    private static final class RegistryPayload {
        List<ManagedPackage> packages = new ArrayList<>();
        String updatedAt;
    }
}
