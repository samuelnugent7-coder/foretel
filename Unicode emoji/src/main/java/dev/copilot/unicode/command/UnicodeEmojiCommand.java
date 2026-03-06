package dev.copilot.unicode.command;

import dev.copilot.unicode.UnicodeShortcodesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Handles the /unicodeemoji command which currently only exposes the reload action.
 */
public final class UnicodeEmojiCommand implements CommandExecutor, TabCompleter {

    private final UnicodeShortcodesPlugin plugin;

    public UnicodeEmojiCommand(final UnicodeShortcodesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        final String action = args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "reload" -> handleReload(sender);
            case "pack" -> handlePack(sender);
            default -> {
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 1) {
            final String entered = args[0].toLowerCase(Locale.ROOT);
            return List.of("reload", "pack").stream()
                .filter(option -> option.startsWith(entered))
                .toList();
        }
        return Collections.emptyList();
    }

    private boolean handleReload(final CommandSender sender) {
        if (!sender.hasPermission("unicodeemoji.reload")) {
            sender.sendMessage(Component.text("You do not have permission to run this command.", NamedTextColor.RED));
            return true;
        }

        final int loaded = this.plugin.reloadShortcodes();
        sender.sendMessage(Component.text("Reloaded " + loaded + " emoji shortcodes.", NamedTextColor.GREEN));
        return true;
    }

    private boolean handlePack(final CommandSender sender) {
        if (!sender.hasPermission("unicodeemoji.pack")) {
            sender.sendMessage(Component.text("You do not have permission to send the emoji resource pack.", NamedTextColor.RED));
            return true;
        }

        if (!this.plugin.isResourcePackReady()) {
            sender.sendMessage(Component.text("The emoji resource pack is still building. Try again shortly.", NamedTextColor.YELLOW));
            return true;
        }

        if (sender instanceof Player player) {
            this.plugin.getResourcePackManager().sendPack(player);
            sender.sendMessage(Component.text("Emoji resource pack sent to you.", NamedTextColor.GREEN));
        } else {
            this.plugin.getServer().getOnlinePlayers().forEach(p -> this.plugin.getResourcePackManager().sendPack(p));
            sender.sendMessage(Component.text("Emoji resource pack sent to all online players.", NamedTextColor.GREEN));
        }
        return true;
    }

    private void sendUsage(final CommandSender sender, final String label) {
        sender.sendMessage(Component.text("Usage: /" + label + " <reload|pack>", NamedTextColor.RED));
    }
}
