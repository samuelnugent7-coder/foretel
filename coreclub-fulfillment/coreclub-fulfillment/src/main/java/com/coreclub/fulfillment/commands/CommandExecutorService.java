package com.coreclub.fulfillment.commands;

import com.coreclub.fulfillment.model.FulfillmentResult;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Schedules fulfillment commands on the main server thread to keep Bukkit
 * happy while returning a future back to the HTTP handler.
 */
public final class CommandExecutorService {
    private final JavaPlugin plugin;

    public CommandExecutorService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<FulfillmentResult> execute(String player, List<String> commands) {
        CompletableFuture<FulfillmentResult> future = new CompletableFuture<>();
        plugin.getLogger().info(MessageFormat.format(
            "Scheduling {0} commands for player {1}",
            commands.size(),
            player
        ));
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            CommandSender console = plugin.getServer().getConsoleSender();
            try {
                int index = 0;
                for (String rawCommand : commands) {
                    index++;
                    String commandWithPlayer = rawCommand
                        .replace("%player%", player)
                        .replace("%PLAYER%", player);
                    plugin.getLogger().info(MessageFormat.format(
                        "Executing command {0}/{1}: {2}",
                        index,
                        commands.size(),
                        previewCommand(commandWithPlayer)
                    ));
                    boolean success = plugin.getServer().dispatchCommand(console, commandWithPlayer);
                    if (!success) {
                        String failureMessage = MessageFormat.format("Command failed: {0}", rawCommand);
                        plugin.getLogger().warning(failureMessage);
                        future.complete(new FulfillmentResult(false, failureMessage));
                        return;
                    }
                }
                plugin.getLogger().info("All fulfillment commands dispatched for player " + player);
                future.complete(new FulfillmentResult(true, "Commands dispatched"));
            } catch (Exception exception) {
                plugin.getLogger().severe("Command execution threw an exception: " + exception.getMessage());
                future.complete(new FulfillmentResult(false, exception.getMessage()));
            }
        });
        return future;
    }

    private static String previewCommand(String command) {
        if (command == null) {
            return "<empty>";
        }
        final int maxLength = 120;
        if (command.length() <= maxLength) {
            return command;
        }
        return command.substring(0, maxLength - 3) + "...";
    }
}
