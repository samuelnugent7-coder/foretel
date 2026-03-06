package dev.copilot.unicode;

import dev.copilot.unicode.chat.ShortcodeReplacer;
import dev.copilot.unicode.command.UnicodeEmojiCommand;
import dev.copilot.unicode.pack.EmojiResourcePackManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin entry point that wires the emoji shortcode services together.
 */
public final class UnicodeShortcodesPlugin extends JavaPlugin implements Listener {

    private volatile ShortcodeReplacer shortcodeReplacer;
    private EmojiResourcePackManager resourcePackManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.resourcePackManager = new EmojiResourcePackManager(this);
        reloadShortcodes();
        getServer().getPluginManager().registerEvents(this, this);
        registerCommand();
    }

    @Override
    public void onDisable() {
        if (this.resourcePackManager != null) {
            this.resourcePackManager.shutdown();
        }
    }

    public int reloadShortcodes() {
        reloadConfig();
        this.shortcodeReplacer = ShortcodeReplacer.fromConfig(getConfig());
        if (this.resourcePackManager != null) {
            this.resourcePackManager.reload(getConfig(), this.shortcodeReplacer.shortcodeMap());
        }
        return this.shortcodeReplacer.shortcodeCount();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerChat(final AsyncChatEvent event) {
        final ShortcodeReplacer replacer = this.shortcodeReplacer;
        if (replacer == null) {
            return;
        }

        final Component current = event.message();
        final Component updated = replacer.replace(current);
        if (!current.equals(updated)) {
            event.message(updated);
        }
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        if (this.resourcePackManager != null && this.resourcePackManager.autoSendEnabled()) {
            this.resourcePackManager.sendPack(event.getPlayer());
        }
    }

    public ShortcodeReplacer getShortcodeReplacer() {
        return this.shortcodeReplacer;
    }

    public EmojiResourcePackManager getResourcePackManager() {
        return this.resourcePackManager;
    }

    public boolean isResourcePackReady() {
        return this.resourcePackManager != null && this.resourcePackManager.packReady();
    }

    private void registerCommand() {
        final PluginCommand command = getCommand("unicodeemoji");
        if (command == null) {
            getLogger().warning("Command unicodeemoji is missing from plugin.yml");
            return;
        }

        final UnicodeEmojiCommand executor = new UnicodeEmojiCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
