package me.warfare.core.items;

import me.warfare.core.WarfareCore;
import me.warfare.core.config.ConfigManager;
import me.warfare.core.utils.MessageService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates and identifies the plugin's custom items.
 *
 * <p>Two responsibilities, mirrored:</p>
 * <ul>
 *   <li><b>Build</b> — turn a {@link CustomItemType} into a tagged
 *       {@link ItemStack} with display name and CustomModelData resolved from
 *       config.</li>
 *   <li><b>Identify</b> — read the PDC tag back off any {@link ItemStack} to
 *       determine whether it is one of our items and which one. The explosion
 *       and claim systems depend on this in later phases.</li>
 * </ul>
 *
 * <p>Identity lives in the {@link PersistentDataContainer}, not in the display
 * name or model data, so renaming or repacking an item never breaks detection.</p>
 */
public final class ItemManager {

    private final ConfigManager config;
    private final MessageService messages;
    private final ItemKeys keys;

    /**
     * @param plugin owning plugin (provides the namespace for {@link ItemKeys})
     */
    public ItemManager(@NotNull final WarfareCore plugin) {
        this.config = plugin.configManager();
        this.messages = plugin.messageService();
        this.keys = new ItemKeys(plugin);
    }

    /** @return the PDC key holder, for systems that read tags directly */
    public @NotNull ItemKeys keys() {
        return keys;
    }

    /**
     * Creates one unit of the given custom item.
     *
     * @param type custom item type to build
     * @return a freshly built, tagged {@link ItemStack}
     */
    public @NotNull ItemStack create(@NotNull final CustomItemType type) {
        return create(type, 1);
    }

    /**
     * Creates a stack of the given custom item.
     *
     * @param type   custom item type to build
     * @param amount stack size (clamped to at least 1)
     * @return a freshly built, tagged {@link ItemStack}
     */
    public @NotNull ItemStack create(@NotNull final CustomItemType type, final int amount) {
        final Material material = resolveMaterial(type);
        final ItemStack stack = new ItemStack(material, Math.max(1, amount));
        final ItemMeta meta = stack.getItemMeta();
        // getItemMeta() is non-null for all obtainable materials, but guard
        // defensively in case of an air/edge material from a bad config override.
        if (meta == null) {
            return stack;
        }

        // Display name (MiniMessage from config, falling back to the enum default).
        final String namePath = "items." + type.configKey() + ".display-name";
        final String rawName = config.getString(namePath, type.defaultDisplayName());
        meta.displayName(messages.parse(rawName).decoration(
                net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

        // CustomModelData (config override, falling back to the enum default).
        // On 1.21.11 the int setCustomModelData(int) is deprecated in favour of
        // the CustomModelDataComponent, whose float list carries the value(s).
        final int modelData = config.getInt(
                "items." + type.configKey() + ".model-data", type.defaultModelData());
        final org.bukkit.inventory.meta.components.CustomModelDataComponent cmd =
                meta.getCustomModelDataComponent();
        cmd.setFloats(java.util.List.of((float) modelData));
        meta.setCustomModelDataComponent(cmd);

        // Stamp identity into the PDC.
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.itemType(), PersistentDataType.STRING, type.name());

        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Identifies an item stack as one of our custom items.
     *
     * @param stack stack to inspect; may be {@code null}
     * @return the {@link CustomItemType}, or {@code null} if not a custom item
     */
    public @Nullable CustomItemType identify(@Nullable final ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        final String stored = meta.getPersistentDataContainer()
                .get(keys.itemType(), PersistentDataType.STRING);
        return CustomItemType.fromString(stored);
    }

    /**
     * Convenience predicate: whether a stack is the given custom type.
     *
     * @param stack stack to test; may be {@code null}
     * @param type  type to compare against
     * @return {@code true} if the stack is that custom item
     */
    public boolean is(@Nullable final ItemStack stack, @NotNull final CustomItemType type) {
        return identify(stack) == type;
    }

    /**
     * Resolves the base material for a type, honouring an optional config
     * override and falling back to the enum default if the override is invalid.
     *
     * @param type custom item type
     * @return the material to build the item on
     */
    private @NotNull Material resolveMaterial(final CustomItemType type) {
        final String override = config.getString(
                "items." + type.configKey() + ".material", null);
        if (override != null) {
            final Material parsed = Material.matchMaterial(override);
            if (parsed != null) {
                return parsed;
            }
        }
        return type.baseMaterial();
    }
}
