package me.warfare.core.commands;

import me.warfare.core.WarfareCore;
import me.warfare.core.config.ConfigManager;
import me.warfare.core.items.CustomItemType;
import me.warfare.core.items.ItemManager;
import me.warfare.core.utils.MessageService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /warfare give <type> [amount]} — gives the sender a custom item.
 *
 * <p>Primarily an admin/testing tool for obtaining Claim Blocks and custom TNT
 * before loot chests exist. Type names are the lowercase {@link CustomItemType}
 * identifiers (e.g. {@code packed_tnt}). Tab completion lists valid types.</p>
 */
public final class GiveCommand implements SubCommand {

    /** Default amount when none is supplied. */
    private static final int DEFAULT_AMOUNT = 1;

    /** Upper bound to avoid accidental inventory flooding. */
    private static final int MAX_AMOUNT = 64;

    private final ConfigManager config;
    private final ItemManager items;
    private final MessageService messages;

    /**
     * @param plugin owning plugin
     */
    public GiveCommand(@NotNull final WarfareCore plugin) {
        this.config = plugin.configManager();
        this.items = plugin.itemManager();
        this.messages = plugin.messageService();
    }

    @Override
    public @NotNull String name() {
        return "give";
    }

    @Override
    public @NotNull String permission() {
        return config.getString("commands.give.permission", "warfare.command.give");
    }

    @Override
    public void execute(@NotNull final CommandSender sender, @NotNull final String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "messages.player-only",
                    "<red>This command can only be used by players.");
            return;
        }
        if (args.length < 1) {
            messages.sendRaw(sender, "<red>Usage: /warfare give <type> [amount]");
            return;
        }

        final CustomItemType type = CustomItemType.fromString(args[0]);
        if (type == null) {
            messages.sendRaw(sender, "<red>Unknown item type: <yellow>" + args[0]);
            return;
        }

        int amount = DEFAULT_AMOUNT;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (final NumberFormatException ex) {
                messages.sendRaw(sender, "<red>Amount must be a number.");
                return;
            }
        }
        amount = Math.max(1, Math.min(MAX_AMOUNT, amount));

        final ItemStack stack = items.create(type, amount);
        player.getInventory().addItem(stack);
        messages.sendRaw(sender, "<green>Gave you <yellow>" + amount
                + "x " + type.configKey() + "<green>.");
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull final CommandSender sender,
                                            @NotNull final String[] args) {
        if (args.length == 1) {
            final String partial = args[0].toLowerCase(Locale.ROOT);
            final List<String> out = new ArrayList<>();
            for (final CustomItemType type : CustomItemType.values()) {
                if (type.configKey().startsWith(partial)) {
                    out.add(type.configKey());
                }
            }
            return out;
        }
        return List.of();
    }
}
