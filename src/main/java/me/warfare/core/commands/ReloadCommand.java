package me.warfare.core.commands;

import me.warfare.core.WarfareCore;
import me.warfare.core.config.ConfigManager;
import me.warfare.core.utils.MessageService;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /warfare reload} — reloads {@code config.yml} at runtime.
 *
 * <p>Serves as the reference implementation of {@link SubCommand} and proves the
 * command pipeline end to end. Its required permission node is read from config
 * ({@code commands.reload.permission}) so it can be renamed by server owners.</p>
 */
public final class ReloadCommand implements SubCommand {

    private final WarfareCore plugin;
    private final ConfigManager config;
    private final MessageService messages;

    /**
     * @param plugin   owning plugin
     * @param config   config manager to reload
     * @param messages message service for feedback
     */
    public ReloadCommand(final WarfareCore plugin,
                         final ConfigManager config,
                         final MessageService messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
    }

    @Override
    public @NotNull String name() {
        return "reload";
    }

    @Override
    public @NotNull String permission() {
        return config.getString("commands.reload.permission", "warfare.command.reload");
    }

    @Override
    public void execute(@NotNull final CommandSender sender, @NotNull final String[] args) {
        try {
            config.reload();
            // Rebuild cached explosion material sets from the new config.
            plugin.explosionService().policy().reload();
            // Reload loot tables (chests/timing keep their state).
            plugin.lootManager().reloadTables();
            messages.send(sender, "messages.reload-success",
                    "<green>Configuration reloaded successfully.");
        } catch (final Exception ex) {
            messages.send(sender, "messages.reload-failed",
                    "<red>Reload failed. Check the console for details.");
            plugin.getLogger().severe("Failed to reload configuration: " + ex.getMessage());
        }
    }
}
