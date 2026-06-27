package me.warfare.core.claims;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable-by-convention data model for a single claim.
 *
 * <p>A claim is centred on a placed Claim Block and protects a square radius
 * around it. The model is storage-agnostic: it knows nothing about how it is
 * persisted. {@link me.warfare.core.storage.StorageProvider} implementations are
 * responsible for (de)serialising these fields.</p>
 *
 * <p>Identity is the claim's center block. Because only one Claim Block can
 * occupy a block, the {@code world + x,y,z} of the center uniquely identifies a
 * claim, which we expose as a stable {@link #key() string key} for maps and
 * file sections.</p>
 *
 * <p>The {@code radius} is denormalised onto the claim (rather than always
 * derived from {@link ClaimType}) so a claim keeps the size it was created with
 * even if an admin later retunes the config.</p>
 */
public final class Claim {

    private final UUID owner;
    private final ClaimType type;
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final int radius;

    /** Free-form metadata for future features (flags, members, etc.). */
    private final Map<String, String> metadata;

    /**
     * Full constructor.
     *
     * @param owner    owning player's UUID
     * @param type     claim type
     * @param worldId  UUID of the world the center is in
     * @param x        center block X
     * @param y        center block Y
     * @param z        center block Z
     * @param radius   protection radius in blocks
     * @param metadata mutable metadata map (copied defensively)
     */
    public Claim(@NotNull final UUID owner,
                 @NotNull final ClaimType type,
                 @NotNull final UUID worldId,
                 final int x,
                 final int y,
                 final int z,
                 final int radius,
                 @Nullable final Map<String, String> metadata) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.type = Objects.requireNonNull(type, "type");
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }

    /**
     * Convenience constructor from a Bukkit {@link Location}.
     *
     * @param owner  owning player's UUID
     * @param type   claim type
     * @param center center location (its world and block coords are used)
     * @param radius protection radius in blocks
     */
    public Claim(@NotNull final UUID owner,
                 @NotNull final ClaimType type,
                 @NotNull final Location center,
                 final int radius) {
        this(owner, type,
                Objects.requireNonNull(center.getWorld(), "center world").getUID(),
                center.getBlockX(), center.getBlockY(), center.getBlockZ(),
                radius, null);
    }

    /**
     * Builds a stable string key from a world UUID and block coordinates. Used
     * as the map key and YAML section name for a claim.
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

    /** @return this claim's unique key (see {@link #keyOf}) */
    public @NotNull String key() {
        return keyOf(worldId, x, y, z);
    }

    /**
     * Tests whether a block position lies within this claim's square radius.
     * Comparison is on the X/Z plane only (claims protect full vertical
     * columns), which matches how bases are raided.
     *
     * @param bx block X
     * @param bz block Z
     * @return {@code true} if within radius on both axes
     */
    public boolean contains(final int bx, final int bz) {
        return Math.abs(bx - x) <= radius && Math.abs(bz - z) <= radius;
    }

    // ----- Accessors -------------------------------------------------------

    /** @return owning player's UUID */
    public @NotNull UUID owner() {
        return owner;
    }

    /** @return claim type */
    public @NotNull ClaimType type() {
        return type;
    }

    /** @return UUID of the world the center is in */
    public @NotNull UUID worldId() {
        return worldId;
    }

    /** @return center block X */
    public int x() {
        return x;
    }

    /** @return center block Y */
    public int y() {
        return y;
    }

    /** @return center block Z */
    public int z() {
        return z;
    }

    /** @return protection radius in blocks */
    public int radius() {
        return radius;
    }

    /**
     * @return live metadata map; mutations persist only if the claim is saved
     *         again by the storage layer
     */
    public @NotNull Map<String, String> metadata() {
        return metadata;
    }

    /**
     * Resolves the center as a Bukkit {@link Location}, or {@code null} if the
     * world is not currently loaded.
     *
     * @param worldLookup function resolving a world UUID to a {@link World}
     * @return center location, or {@code null} when the world is unavailable
     */
    public @Nullable Location centerLocation(
            @NotNull final java.util.function.Function<UUID, World> worldLookup) {
        final World world = worldLookup.apply(worldId);
        return world == null ? null : new Location(world, x, y, z);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Claim other)) {
            return false;
        }
        return x == other.x && y == other.y && z == other.z
                && worldId.equals(other.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, x, y, z);
    }

    @Override
    public String toString() {
        return "Claim{owner=" + owner + ", type=" + type + ", key=" + key()
                + ", radius=" + radius + '}';
    }
}
