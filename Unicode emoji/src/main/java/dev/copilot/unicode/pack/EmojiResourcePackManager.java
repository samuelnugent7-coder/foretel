package dev.copilot.unicode.pack;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class EmojiResourcePackManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final TwemojiDownloader downloader;
    private final PackHttpServer httpServer;
    private volatile ResourcePackConfig config;
    private volatile PackBuildResult currentPack;
    private volatile String currentUrl;
    private CompletableFuture<Void> buildFuture;

    public EmojiResourcePackManager(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.downloader = new TwemojiDownloader(plugin.getDataFolder().toPath().resolve("twemoji-cache"), this.logger);
        this.httpServer = new PackHttpServer(this.logger);
        this.config = ResourcePackConfig.from(plugin.getConfig());
        this.downloader.updateSettings(this.config.downloadSettings());
    }

    public void reload(final FileConfiguration configuration, final Map<String, String> emojiMap) {
        this.config = ResourcePackConfig.from(configuration);
        this.downloader.updateSettings(this.config.downloadSettings());
        if (!this.config.enabled()) {
            shutdownHttp();
            this.currentPack = null;
            this.currentUrl = null;
            return;
        }
        scheduleBuild(emojiMap);
    }

    public void sendPack(final Player player) {
        final PackBuildResult pack = this.currentPack;
        final String url = this.currentUrl;
        if (!this.config.enabled() || pack == null || url == null) {
            return;
        }
        try {
            player.setResourcePack(url, pack.sha1(), ComponentFactory.prompt(this.config), this.config.force());
        } catch (final IllegalArgumentException ex) {
            this.logger.log(Level.WARNING, "Failed to send resource pack to " + player.getName(), ex);
        }
    }

    private void scheduleBuild(final Map<String, String> emojiMap) {
        final Map<String, String> snapshot = Map.copyOf(emojiMap);
        final Path dataFolder = this.plugin.getDataFolder().toPath();
        cancelIfRunning();
        this.buildFuture = CompletableFuture.runAsync(() -> buildPack(snapshot, dataFolder), runnable ->
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, runnable)
        );
    }

    private void buildPack(final Map<String, String> emojiMap, final Path dataFolder) {
        try {
            final PackBuildResult result = EmojiResourcePackBuilder.build(dataFolder, this.config, emojiMap, this.downloader, this.logger);
            this.currentPack = result;
            if (this.config.hostConfig().enabled()) {
                ensureHttpServer(result.zipPath());
            } else {
                shutdownHttp();
            }
            updateCurrentUrl();
            this.logger.info("Emoji resource pack ready: " + result.zipPath());
        } catch (final Exception ex) {
            this.logger.log(Level.SEVERE, "Failed to build emoji resource pack", ex);
        }
    }

    private void ensureHttpServer(final Path packPath) throws IOException {
        final ResourcePackConfig.HostConfig host = this.config.hostConfig();
        if (!host.enabled()) {
            shutdownHttp();
            return;
        }
        final InetSocketAddress address = new InetSocketAddress(host.bindAddress(), host.port());
        this.httpServer.start(address, packPath, host.route());
    }

    public void shutdown() {
        cancelIfRunning();
        shutdownHttp();
    }

    private void cancelIfRunning() {
        final CompletableFuture<Void> future = this.buildFuture;
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    private void shutdownHttp() {
        this.httpServer.stop();
    }

    private void updateCurrentUrl() {
        String url = this.config.hostConfig().publicUrl();
        if ((url == null || url.isBlank()) && this.config.hostConfig().enabled()) {
            url = "http://" + this.config.hostConfig().bindAddress() + ":" + this.config.hostConfig().port() + this.config.hostConfig().route();
        }
        if (url == null || url.isBlank()) {
            this.logger.warning("resource-pack.host.public-url is empty; players cannot download the emoji pack.");
            this.currentUrl = null;
        } else {
            this.currentUrl = url;
        }
    }

    public boolean autoSendEnabled() {
        return this.config.enabled() && this.config.autoSend();
    }

    public ResourcePackConfig config() {
        return this.config;
    }

    public boolean packReady() {
        return this.currentPack != null && this.currentUrl != null;
    }
}
