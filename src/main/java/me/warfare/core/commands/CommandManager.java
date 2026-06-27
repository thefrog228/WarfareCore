package me.warfare.core.commands;

import me.warfare.core.WarfareCore;
import me.warfare.core.utils.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dispatcher for the root {@code /warfare} command.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Acts as the single {@link CommandExecutor}/{@link TabCompleter} for the
 *       root command declared in {@code plugin.yml}.</li>
 *   <li>Holds a registry of {@link SubCommand}s keyed by name.</li>
 *   <li>Performs permission checks and routes to the matching subcommand.</li>
 *   <li>Provides tab completion for subcommand names and their arguments.</li>
 * </ul>
 *
 * <p>The registry uses a {@link LinkedHashMap} so tab completion lists
 * subcommands in registration order.</p>
 */
public final class CommandManager implements CommandExecutor, TabCompleter {

    /** Name of the root command as declared in plugin.yml. */
    private static final String ROOT_COMMAND = "warfare";

    private final WarfareCore plugin;
    private final MessageService messages;

    /** Registered subcommands, keyed by lowercase name, insertion-ordered. */
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    /**
     * @param plugin   owning plugin
     * @param messages message service for permission/usage feedback
     */
    public CommandManager(final WarfareCore plugin, final MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    /**
     * Binds this manager to the root command from {@code plugin.yml}. Logs a
     * severe message if the command is missing (a packaging error).
     */
    public void register() {
        final PluginCommand command = plugin.getCommand(ROOT_COMMAND);
        if (command == null) {
            plugin.getLogger().severe(
                    "Root command '" + ROOT_COMMAND + "' missing from plugin.yml.");
            return;
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    /**
     * Adds a subcommand to the registry.
     *
     * @param sub subcommand to register
     */
    public void register(final SubCommand sub) {
        subCommands.put(sub.name().toLowerCase(Locale.ROOT), sub);
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender,
                             @NotNull final Command command,
                             @NotNull final String label,
                             @NotNull final String[] args) {
        if (args.length == 0) {
            messages.send(sender, "messages.unknown-command",
                    "<red>Unknown subcommand.");
            return true;
        }

        final SubCommand sub = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            messages.send(sender, "messages.unknown-command",
                    "<red>Unknown subcommand.");
            return true;
        }

        final String perm = sub.permission();
        if (!perm.isEmpty() && !sender.hasPermission(perm)) {
            messages.send(sender, "messages.no-permission",
                    "<red>You don't have permission to do that.");
            return true;
        }

        // Strip the subcommand name; pass the remaining args along.
        final String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        sub.execute(sender, subArgs);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull final CommandSender sender,
                                                @NotNull final Command command,
                                                @NotNull final String alias,
                                                @NotNull final String[] args) {
        if (args.length == 1) {
            // Suggest visible subcommand names matching what's typed so far.
            final List<String> names = new ArrayList<>();
            for (final SubCommand sub : subCommands.values()) {
                final String perm = sub.permission();
                if (perm.isEmpty() || sender.hasPermission(perm)) {
                    names.add(sub.name());
                }
            }
            final List<String> out = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], names, out);
            return out;
        }

        // Delegate deeper completion to the subcommand itself.
        final SubCommand sub = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            return List.of();
        }
        final String perm = sub.permission();
        if (!perm.isEmpty() && !sender.hasPermission(perm)) {
            return List.of();
        }
        final String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        return sub.tabComplete(sender, subArgs);
    }
}
