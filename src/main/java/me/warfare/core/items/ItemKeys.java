package me.warfare.core.items;

import me.warfare.core.WarfareCore;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Central registry of {@link NamespacedKey}s used by the item system.
 *
 * <p>Every PersistentDataContainer tag the plugin writes is defined here exactly
 * once, so key strings are never scattered as loose literals across the
 * codebase. Keys are created from the owning {@link Plugin} so the namespace is
 * always the plugin's, avoiding collisions with other plugins.</p>
 */
public final class ItemKeys {

    /**
     * PDC key under which a custom item stores its {@link CustomItemType} name.
     * Reading this tag back is how any {@link org.bukkit.inventory.ItemStack} is
     * identified as one of our custom items.
     */
    private final NamespacedKey itemType;

    /**
     * @param plugin owning plugin, used as the key namespace
     */
    public ItemKeys(@NotNull final WarfareCore plugin) {
        this.itemType = new NamespacedKey(plugin, "item_type");
    }

    /** @return the key storing a custom item's type identifier */
    public @NotNull NamespacedKey itemType() {
        return itemType;
    }
}
