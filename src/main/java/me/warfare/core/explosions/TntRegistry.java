package me.warfare.core.explosions;

import me.warfare.core.WarfareCore;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks the tier of custom TNT through its lifecycle: placed block → primed
 * entity → explosion.
 *
 * <p>Plain blocks cannot hold a {@link PersistentDataContainer}, so when a custom
 * TNT block is placed its tier is remembered in a small {@code location → tier}
 * map keyed by a packed block coordinate. When the corresponding
 * {@link org.bukkit.entity.TNTPrimed} entity spawns at that position, the tier is
 * transferred onto the entity's PDC (which <em>does</em> persist) and the map
 * entry is cleared. At explosion time the tier is read back from the entity.</p>
 *
 * <p>TNT minecarts carry their tier on the minecart entity directly, so they do
 * not use the block map.</p>
 */
public final class TntRegistry {

    /** Tier name stored on primed-TNT / minecart entities. */
    private final NamespacedKey tierKey;

    /** Packed block location -> tier name, for placed custom TNT awaiting prime. */
    private final Map<Long, TntTier> placedBlocks = new HashMap<>();

    /**
     * @param plugin owning plugin (namespace for the PDC key)
     */
    public TntRegistry(@NotNull final WarfareCore plugin) {
        this.tierKey = new NamespacedKey(plugin, "tnt_tier");
    }

    // ----- Placed-block tracking -------------------------------------------

    /**
     * Records that a custom TNT block of the given tier was placed at a position.
     *
     * @param x    block X
     * @param y    block Y
     * @param z    block Z
     * @param tier TNT tier
     */
    public void markPlaced(final int x, final int y, final int z, @NotNull final TntTier tier) {
        placedBlocks.put(packBlock(x, y, z), tier);
    }

    /**
     * Consumes (reads and removes) the tier recorded for a placed block, if any.
     *
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @return the recorded tier, or {@code null} if none
     */
    public @Nullable TntTier consumePlaced(final int x, final int y, final int z) {
        return placedBlocks.remove(packBlock(x, y, z));
    }

    // ----- Entity tagging --------------------------------------------------

    /**
     * Tags an entity (primed TNT or TNT minecart) with a tier.
     *
     * @param entity entity to tag
     * @param tier   tier to store
     */
    public void tagEntity(@NotNull final Entity entity, @NotNull final TntTier tier) {
        entity.getPersistentDataContainer()
                .set(tierKey, PersistentDataType.STRING, tier.name());
    }

    /**
     * Reads the tier tagged on an entity.
     *
     * @param entity entity to inspect
     * @return the tier, or {@code null} if untagged
     */
    public @Nullable TntTier readTier(@NotNull final Entity entity) {
        final String raw = entity.getPersistentDataContainer()
                .get(tierKey, PersistentDataType.STRING);
        return TntTier.fromString(raw);
    }

    // ----- Helpers ---------------------------------------------------------

    /**
     * Packs block coordinates into a single long. Y is limited to 12 bits
     * (-2048..2047 offset), comfortably covering all vanilla world heights.
     *
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @return packed key
     */
    private long packBlock(final int x, final int y, final int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | ((y + 2048) & 0xFFF);
    }
}
