package me.warfare.core.loot;

import me.warfare.core.items.CustomItemType;
import me.warfare.core.items.ItemManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A single loot entry: an item (vanilla material or a custom item), an amount
 * range, and a selection weight.
 *
 * <p>An entry resolves to either a vanilla {@link Material} or a
 * {@link CustomItemType}; exactly one is set. {@link #roll(ItemManager)}
 * produces a concrete {@link ItemStack} with a random amount in the configured
 * range. The {@code weight} influences how likely this entry is chosen relative
 * to others in the same table.</p>
 */
public final class LootItem {

    /** Set when this entry is a custom item; otherwise {@code null}. */
    private final CustomItemType customType;

    /** Set when this entry is a vanilla item; otherwise {@code null}. */
    private final Material material;

    private final int minAmount;
    private final int maxAmount;
    private final int weight;

    /**
     * @param customType custom item type, or {@code null} for a vanilla item
     * @param material   vanilla material, or {@code null} for a custom item
     * @param minAmount  minimum stack amount (clamped to at least 1)
     * @param maxAmount  maximum stack amount (clamped to at least min)
     * @param weight     relative selection weight (clamped to at least 1)
     */
    public LootItem(@Nullable final CustomItemType customType,
                    @Nullable final Material material,
                    final int minAmount,
                    final int maxAmount,
                    final int weight) {
        this.customType = customType;
        this.material = material;
        this.minAmount = Math.max(1, minAmount);
        this.maxAmount = Math.max(this.minAmount, maxAmount);
        this.weight = Math.max(1, weight);
    }

    /** @return the selection weight (always >= 1) */
    public int weight() {
        return weight;
    }

    /**
     * Rolls this entry into a concrete item stack with a random amount.
     *
     * @param items item manager used to build custom items
     * @return a built {@link ItemStack}, or {@code null} if the entry is invalid
     */
    public @Nullable ItemStack roll(@NotNull final ItemManager items) {
        final int amount = minAmount == maxAmount
                ? minAmount
                : ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);

        if (customType != null) {
            return items.create(customType, amount);
        }
        if (material != null && !material.isAir()) {
            return new ItemStack(material, amount);
        }
        return null;
    }

    /**
     * Parses a loot entry from a config map.
     *
     * <p>Expected keys: {@code item} (a custom type name like {@code PACKED_TNT}
     * or a vanilla material like {@code DIAMOND}), {@code min}, {@code max},
     * {@code weight}. Unknown item names yield {@code null} so the caller can
     * skip the entry with a warning.</p>
     *
     * @param itemName item identifier (custom type or vanilla material)
     * @param min      minimum amount
     * @param max      maximum amount
     * @param weight   selection weight
     * @return a parsed entry, or {@code null} if the item name is unrecognised
     */
    public static @Nullable LootItem parse(final String itemName, final int min,
                                           final int max, final int weight) {
        if (itemName == null) {
            return null;
        }
        // Prefer a custom item type; fall back to a vanilla material.
        final CustomItemType custom = CustomItemType.fromString(itemName);
        if (custom != null) {
            return new LootItem(custom, null, min, max, weight);
        }
        final Material mat = Material.matchMaterial(itemName);
        if (mat != null) {
            return new LootItem(null, mat, min, max, weight);
        }
        return null;
    }
}
