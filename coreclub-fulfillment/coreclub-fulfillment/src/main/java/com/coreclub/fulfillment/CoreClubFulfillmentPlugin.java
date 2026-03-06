package com.coreclub.fulfillment;

import com.coreclub.fulfillment.commands.CommandExecutorService;
import com.coreclub.fulfillment.config.FulfillmentConfig;
import com.coreclub.fulfillment.http.FulfillmentHttpServer;
import com.coreclub.fulfillment.security.WebhookDecryptor;
import com.coreclub.fulfillment.store.PackageRegistry;
import com.coreclub.fulfillment.store.ProcessedOrderStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CoreClubFulfillmentPlugin hosts a lightweight HTTP server that receives
 * fulfillment requests from the web backend and promotes players by issuing
 * console commands.
 */
public final class CoreClubFulfillmentPlugin extends JavaPlugin {

    private FulfillmentHttpServer httpServer;
    private ProcessedOrderStore processedOrderStore;
    private CommandExecutorService commandExecutorService;
    private Map<String, List<String>> commandMap = Collections.emptyMap();
    private FulfillmentConfig fulfillmentConfig;
    private PackageRegistry packageRegistry;
    private WebhookDecryptor webhookDecryptor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Initializing CoreClub Fulfillment v" + getDescription().getVersion());
        getLogger().info("Using data folder " + getDataFolder().getAbsolutePath());
        this.processedOrderStore = new ProcessedOrderStore(this);
        this.commandExecutorService = new CommandExecutorService(this);
        this.packageRegistry = new PackageRegistry(this);
        reloadFulfillmentComponents();
        getCommand("coreclubreload").setExecutor((sender, command, label, args) -> {
            reloadFulfillmentComponents();
            sender.sendMessage("§a[CoreClub] Fulfillment configuration reloaded.");
            return true;
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down CoreClub fulfillment bridge");
        if (httpServer != null) {
            httpServer.stop();
        }
        if (processedOrderStore != null) {
            processedOrderStore.save();
            getLogger().info("Persisted " + processedOrderStore.snapshot().size() + " processed orders");
        }
    }

    private void reloadFulfillmentComponents() {
        getLogger().info("Reloading fulfillment components from configuration");
        reloadConfig();
        fulfillmentConfig = FulfillmentConfig.fromConfig(getConfig());
        int allowedIpCount = fulfillmentConfig.allowedIpAddresses() == null
            ? 0
            : fulfillmentConfig.allowedIpAddresses().size();
        getLogger().info(MessageFormat.format(
            "Configured to listen on {0}:{1} (allowedIps={2}, logRequests={3})",
            fulfillmentConfig.listenHost(),
            fulfillmentConfig.listenPort(),
            allowedIpCount,
            fulfillmentConfig.logIncomingRequests()
        ));
        webhookDecryptor = new WebhookDecryptor(fulfillmentConfig.encryptionKey());
        if (packageRegistry == null) {
            packageRegistry = new PackageRegistry(this);
        }
        packageRegistry.load();
        refreshCommandMap();

        if (httpServer != null) {
            getLogger().info("Stopping existing fulfillment HTTP server before restart");
            httpServer.stop();
        }

        httpServer = new FulfillmentHttpServer(
            this,
            fulfillmentConfig,
            commandExecutorService,
            processedOrderStore,
            packageRegistry,
            webhookDecryptor,
            this::refreshCommandMap
        );
        httpServer.setCommandMap(commandMap);
        httpServer.start();
        getLogger().info("Fulfillment components reloaded");
    }

    private synchronized void refreshCommandMap() {
        Map<String, List<String>> fileCommands = loadYamlCommandMap();
        Map<String, List<String>> syncedCommands = packageRegistry == null
            ? Collections.emptyMap()
            : packageRegistry.toCommandMap();
        Map<String, List<String>> merged = new HashMap<>();
        merged.putAll(fileCommands);
        merged.putAll(syncedCommands);
        commandMap = Collections.unmodifiableMap(merged);
        if (httpServer != null) {
            httpServer.setCommandMap(commandMap);
        }
        getLogger().info(MessageFormat.format(
            "Active command catalog contains {0} entries (file={1}, registry={2})",
            commandMap.size(),
            fileCommands.size(),
            syncedCommands.size()
        ));
    }

    private Map<String, List<String>> loadYamlCommandMap() {
        return Collections.emptyMap();
    }
}
