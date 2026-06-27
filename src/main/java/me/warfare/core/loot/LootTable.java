package me.warfare.core.loot;

import me.warfare.core.items.ItemManager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A named, weighted collection of {@link LootItem}s that fills a chest.
 *
 * <p>On a refill the table picks {@code rollCount} entries (weighted random,
 * with replacement) and places each rolled stack into a random empty slot of the
 * target inventory. Weighting lets rare items (e.g. Reinforced TNT) appear less
 * often than common ones without separate probability code per item.</p>
 */
public final class LootTable {

    private final String name;
    private final List<LootItem> items;
    private final int rollCount;
    private final int totalWeight;

    /**
     * @param name      table identifier (matches the config key)
     * @param items     loot entries
     * @param rollCount how many stacks to place per refill (clamped to >= 1)
     */
    public LootTable(@NotNull final String name,
                     @NotNull final List<LootItem> items,
                     final int rollCount) {
        this.name = name;
        this.items = List.copyOf(items);
        this.rollCount = Math.max(1, rollCount);
        int sum = 0;
        for (final LootItem item : items) {
            sum += item.weight();
        }
        this.totalWeight = Math.max(1, sum);
    }

    /** @return this table's name */
    public @NotNull String name() {
        return name;
    }

    /** @return {@code true} if the table has no usable entries */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Fills an inventory with freshly rolled loot.
     *
     * @param inventory target inventory (e.g. a chest)
     * @param items     item manager for building custom items
     * @param clearFirst whether to wipe existing contents before filling
     */
    public void fill(@NotNull final Inventory inventory,
                     @NotNull final ItemManager items,
                     final boolean clearFirst) {
        if (this.items.isEmpty()) {
            return;
        }
        if (clearFirst) {
            inventory.clear();
        }

        final int size = inventory.getSize();
        // Collect currently-empty slots so we never overwrite kept items.
        final List<Integer> emptySlots = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            final ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                emptySlots.add(slot);
            }
        }
        if (emptySlots.isEmpty()) {
            return;
        }

        final int placements = Math.min(rollCount, emptySlots.size());
        for (int i = 0; i < placements; i++) {
            final LootItem rolledEntry = pickWeighted();
            if (rolledEntry == null) {
                continue;
            }
            final ItemStack stack = rolledEntry.roll(items);
            if (stack == null) {
                continue;
            }
            // Choose a random remaining empty slot.
            final int idx = ThreadLocalRandom.current().nextInt(emptySlots.size());
            final int slot = emptySlots.remove(idx);
            inventory.setItem(slot, stack);
            if (emptySlots.isEmpty()) {
                break;
            }
        }
    }

    /**
     * Picks one entry using weighted random selection.
     *
     * @return a chosen entry, or {@code null} if the table is empty
     */
    private @Nullable LootItem pickWeighted() {
        if (items.isEmpty()) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (final LootItem item : items) {
            roll -= item.weight();
            if (roll < 0) {
                return item;
            }
        }
        // Fallback (shouldn't happen given totalWeight): last item.
        return items.get(items.size() - 1);
    }
}
