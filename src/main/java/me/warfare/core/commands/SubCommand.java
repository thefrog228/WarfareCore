package me.warfare.core.commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Contract for a single {@code /warfare <sub>} subcommand.
 *
 * <p>Each subcommand is a small, self-contained class. {@link CommandManager}
 * handles registration, permission checks, and dispatch, so implementations
 * only worry about their own behaviour. Adding a feature command later means
 * writing one class and registering it — no edits to the dispatcher.</p>
 */
public interface SubCommand {

    /**
     * @return the literal name typed after {@code /warfare} (e.g. {@code reload}).
     *         Must be lowercase and unique among registered subcommands.
     */
    @NotNull String name();

    /**
     * The permission node required to run this subcommand. The returned string
     * is typically read from config so server owners can rename nodes.
     *
     * @return permission node, or empty string for no permission requirement
     */
    @NotNull String permission();

    /**
     * Executes the subcommand. Permission has already been verified by the
     * dispatcher before this is called.
     *
     * @param sender the command source
     * @param args   arguments after the subcommand name
     */
    void execute(@NotNull CommandSender sender, @NotNull String[] args);

    /**
     * Supplies tab-completion suggestions for this subcommand's arguments.
     * Default returns no suggestions.
     *
     * @param sender the command source
     * @param args   current arguments after the subcommand name
     * @return suggestion list; never {@code null}
     */
    default @NotNull List<String> tabComplete(@NotNull CommandSender sender,
                                              @NotNull String[] args) {
        return Collections.emptyList();
    }
}
