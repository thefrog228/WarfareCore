package me.warfare.core.commands;

import me.warfare.core.WarfareCore;
import me.warfare.core.config.ConfigManager;
import me.warfare.core.loot.LootManager;
import me.warfare.core.utils.MessageService;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /warfare loot <create|remove|refill|list>} — manages loot chests.
 *
 * <p>Sub-actions operate on the chest block the player is looking at (within a
 * few blocks), except {@code list} which reports configured tables and chest
 * count. This is the in-game tool for binding chests to loot tables.</p>
 *
 * <ul>
 *   <li>{@code create <table>} — register the targeted chest with a loot table.</li>
 *   <li>{@code remove} — unregister the targeted chest.</li>
 *   <li>{@code refill} — immediately refill the targeted chest.</li>
 *   <li>{@code list} — list loot tables and the registered chest count.</li>
 * </ul>
 */
public final class LootCommand implements SubCommand {

    /** How far ahead to look for a targeted chest block. */
    private static final int TARGET_RANGE = 6;

    private final ConfigManager config;
    private final LootManager loot;
    private final MessageService messages;

    /**
     * @param plugin owning plugin
     */
    public LootCommand(@NotNull final WarfareCore plugin) {
        this.config = plugin.configManager();
        this.loot = plugin.lootManager();
        this.messages = plugin.messageService();
    }

    @Override
    public @NotNull String name() {
        return "loot";
    }

    @Override
    public @NotNull String permission() {
        return config.getString("commands.loot.permission", "warfare.command.loot");
    }

    @Override
    public void execute(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (args.length < 1) {
            messages.sendRaw(sender, "<red>Usage: /warfare loot <create|remove|refill|list>");
            return;
        }
        final String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            handleList(sender);
            return;
        }

        // All remaining actions are player-only and operate on a targeted chest.
        if (!(sender instanceof Player player)) {
            messages.send(sender, "messages.player-only",
                    "<red>This command can only be used by players.");
            return;
        }
        switch (action) {
            case "create" -> handleCreate(player, args);
            case "remove" -> handleRemove(player);
            case "refill" -> handleRefill(player);
            default -> messages.sendRaw(sender,
                    "<red>Unknown action. Use create, remove, refill, or list.");
        }
    }

    /**
     * Registers the targeted chest with a named loot table.
     *
     * @param player acting player
     * @param args   command args (args[1] = table name)
     */
    private void handleCreate(final Player player, final String[] args) {
        if (args.length < 2) {
            messages.sendRaw(player, "<red>Usage: /warfare loot create <table>");
            return;
        }
        final Block chest = targetedChest(player);
        if (chest == null) {
            messages.sendRaw(player, "<red>Look at a chest within range.");
            return;
        }
        final String table = args[1];
        if (loot.register(chest.getLocation(), table) == null) {
            messages.sendRaw(player, "<red>Unknown loot table: <yellow>" + table);
            return;
        }
        messages.sendRaw(player, "<green>Loot chest created with table <yellow>"
                + table + "<green>.");
    }

    /**
     * Unregisters the targeted chest.
     *
     * @param player acting player
     */
    private void handleRemove(final Player player) {
        final Block chest = targetedChest(player);
        if (chest == null) {
            messages.sendRaw(player, "<red>Look at a chest within range.");
            return;
        }
        if (loot.unregister(chest.getLocation())) {
            messages.sendRaw(player, "<green>Loot chest removed.");
        } else {
            messages.sendRaw(player, "<red>That chest is not a registered loot chest.");
        }
    }

    /**
     * Immediately refills the targeted chest.
     *
     * @param player acting player
     */
    private void handleRefill(final Player player) {
        final Block chest = targetedChest(player);
        if (chest == null) {
            messages.sendRaw(player, "<red>Look at a chest within range.");
            return;
        }
        if (loot.forceRefill(chest.getLocation())) {
            messages.sendRaw(player, "<green>Loot chest refilled.");
        } else {
            messages.sendRaw(player, "<red>That chest is not a registered loot chest.");
        }
    }

    /**
     * Lists configured loot tables and the registered chest count.
     *
     * @param sender command source
     */
    private void handleList(final CommandSender sender) {
        final var names = loot.tableNames();
        messages.sendRaw(sender, "<yellow>Loot tables:<white> "
                + (names.isEmpty() ? "(none)" : String.join(", ", names)));
        messages.sendRaw(sender, "<yellow>Registered chests:<white> " + loot.chestCount());
    }

    /**
     * Finds the chest block the player is looking at, within {@link #TARGET_RANGE}.
     *
     * @param player acting player
     * @return the targeted chest block, or {@code null} if none
     */
    private Block targetedChest(final Player player) {
        final Block target = player.getTargetBlockExact(TARGET_RANGE);
        if (target != null && target.getState() instanceof Chest) {
            return target;
        }
        return null;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull final CommandSender sender,
                                            @NotNull final String[] args) {
        if (args.length == 1) {
            final String partial = args[0].toLowerCase(Locale.ROOT);
            final List<String> out = new ArrayList<>();
            for (final String action : List.of("create", "remove", "refill", "list")) {
                if (action.startsWith(partial)) {
                    out.add(action);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            final String partial = args[1].toLowerCase(Locale.ROOT);
            final List<String> out = new ArrayList<>();
            for (final String table : loot.tableNames()) {
                if (table.startsWith(partial)) {
                    out.add(table);
                }
            }
            return out;
        }
        return List.of();
    }
}
