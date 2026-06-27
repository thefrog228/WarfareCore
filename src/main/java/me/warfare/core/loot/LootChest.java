package me.warfare.core.loot;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * A registered loot chest: a block location bound to a {@link LootTable}, with
 * its own refill interval and the timestamp of its last refill.
 *
 * <p>This is the persistent unit of the loot system, stored through the same
 * {@link me.warfare.core.storage.StorageProvider} seam as claims. Identity is
 * the block position, exposed as a stable {@link #key()} for maps and storage
 * sections (mirroring how {@link me.warfare.core.claims.Claim} keys work).</p>
 */
public final class LootChest {

    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final String tableName;

    /** Refill interval in seconds. */
    private final int intervalSeconds;

    /** Epoch millis of the last refill; 0 means "never refilled". */
    private long lastRefillMillis;

    /**
     * @param worldId         world UUID
     * @param x               block X
     * @param y               block Y
     * @param z               block Z
     * @param tableName       name of the bound loot table
     * @param intervalSeconds refill interval in seconds
     * @param lastRefillMillis epoch millis of last refill (0 = never)
     */
    public LootChest(@NotNull final UUID worldId, final int x, final int y, final int z,
                     @NotNull final String tableName, final int intervalSeconds,
                     final long lastRefillMillis) {
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.x = x;
        this.y = y;
        this.z = z;
        this.tableName = Objects.requireNonNull(tableName, "tableName");
        this.intervalSeconds = Math.max(1, intervalSeconds);
        this.lastRefillMillis = lastRefillMillis;
    }

    /**
     * Builds a stable key from world UUID and block coordinates.
     *
     * @param worldId world UUID
     * @param x       block X
     * @param y       block Y
     * @param z       block Z
     * @return unique key string
     */
    public static String keyOf(final UUID worldId, final int x, final int y, final int z) {
        return worldId + ";" + x + ";" + y + ";" + z;
    }

    /** @return this chest's unique key */
    public @NotNull String key() {
        return keyOf(worldId, x, y, z);
    }

    /**
     * Whether this chest is due for a refill at the given time.
     *
     * @param nowMillis current epoch millis
     * @return {@code true} if at least one interval has elapsed since last refill
     */
    public boolean isDue(final long nowMillis) {
        return nowMillis - lastRefillMillis >= intervalSeconds * 1000L;
    }

    /**
     * Marks this chest as refilled at the given time.
     *
     * @param nowMillis current epoch millis
     */
    public void markRefilled(final long nowMillis) {
        this.lastRefillMillis = nowMillis;
    }

    /**
     * Resolves the chest's location, or {@code null} if the world is unloaded.
     *
     * @param worldLookup function resolving a world UUID to a {@link World}
     * @return the chest location, or {@code null}
     */
    public @Nullable Location location(@NotNull final Function<UUID, World> worldLookup) {
        final World world = worldLookup.apply(worldId);
        return world == null ? null : new Location(world, x, y, z);
    }

    // ----- Accessors -------------------------------------------------------

    /** @return world UUID */
    public @NotNull UUID worldId() {
        return worldId;
    }

    /** @return block X */
    public int x() {
        return x;
    }

    /** @return block Y */
    public int y() {
        return y;
    }

    /** @return block Z */
    public int z() {
        return z;
    }

    /** @return bound loot table name */
    public @NotNull String tableName() {
        return tableName;
    }

    /** @return refill interval in seconds */
    public int intervalSeconds() {
        return intervalSeconds;
    }

    /** @return epoch millis of last refill (0 = never) */
    public long lastRefillMillis() {
        return lastRefillMillis;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LootChest other)) {
            return false;
        }
        return x == other.x && y == other.y && z == other.z
                && worldId.equals(other.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, x, y, z);
    }
}
