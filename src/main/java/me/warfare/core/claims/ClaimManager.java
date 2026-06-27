package me.warfare.core.claims;

import me.warfare.core.WarfareCore;
import me.warfare.core.config.ConfigManager;
import me.warfare.core.storage.StorageProvider;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory authority for all claims, backed by persistent storage.
 *
 * <p><b>Performance model.</b> Protection checks happen on extremely hot paths
 * (every block break/place, fluid tick, piston push). To keep them cheap this
 * class never iterates all claims and never scans worlds. It maintains two
 * structures:</p>
 * <ul>
 *   <li>{@code claimsByKey} — every claim, keyed by {@link Claim#key()}, for
 *       create/remove and owner counting.</li>
 *   <li>{@code chunkIndex} — a map from a packed chunk coordinate to the set of
 *       claim keys whose protected square overlaps that chunk. A point lookup
 *       therefore inspects only the few claims registered to that one chunk.</li>
 * </ul>
 *
 * <p>Because a claim's square radius can spill across chunk borders, each claim
 * is registered into <em>every</em> chunk its square touches, so a single-chunk
 * lookup at query time is sufficient and correct.</p>
 *
 * <p>All public methods are main-thread only (Bukkit listeners run there);
 * the structures are plain {@link HashMap}s for speed, not concurrency.</p>
 */
public final class ClaimManager {

    /** Number of bits to shift a block coordinate to get its chunk coordinate. */
    private static final int CHUNK_SHIFT = 4;

    private final WarfareCore plugin;
    private final ConfigManager config;
    private final StorageProvider storage;

    /** All claims by unique key. */
    private final Map<String, Claim> claimsByKey = new HashMap<>();

    /** Chunk key -> set of claim keys overlapping that chunk. */
    private final Map<Long, Set<String>> chunkIndex = new HashMap<>();

    /** Cached count of claims per owner, to enforce limits without scanning. */
    private final Map<UUID, Integer> countByOwner = new HashMap<>();

    /**
     * @param plugin owning plugin (provides config + storage)
     */
    public ClaimManager(@NotNull final WarfareCore plugin) {
        this.plugin = plugin;
        this.config = plugin.configManager();
        this.storage = plugin.storageManager().provider();
    }

    /**
     * Loads all persisted claims into memory and builds the indexes. Called once
     * during enable, after storage is initialised.
     */
    public void loadAll() {
        claimsByKey.clear();
        chunkIndex.clear();
        countByOwner.clear();
        try {
            final Collection<Claim> loaded = storage.loadClaims();
            for (final Claim claim : loaded) {
                index(claim);
            }
            plugin.getLogger().info("Loaded " + claimsByKey.size() + " claim(s).");
        } catch (final Exception ex) {
            plugin.getLogger().severe("Failed to load claims: " + ex.getMessage());
        }
    }

    // ----- Configuration helpers -------------------------------------------

    /**
     * Resolves the configured radius for a claim type.
     *
     * @param type claim type
     * @return radius in blocks from config, with sane fallbacks
     */
    public int radiusFor(@NotNull final ClaimType type) {
        return switch (type) {
            case NORMAL -> config.getInt("claims.radius.normal", 16);
            case ADVANCED -> config.getInt("claims.radius.advanced", 32);
        };
    }

    /** @return the configured maximum claims a single player may own */
    public int maxPerPlayer() {
        return config.getInt("claims.max-per-player", 3);
    }

    /**
     * @param owner player UUID
     * @return how many claims the player currently owns
     */
    public int ownedCount(@NotNull final UUID owner) {
        return countByOwner.getOrDefault(owner, 0);
    }

    // ----- Queries ---------------------------------------------------------

    /**
     * Finds the claim covering a location, if any. Only claims registered to the
     * location's chunk are examined, so this is O(claims-in-chunk), not O(all).
     *
     * @param world world of the location
     * @param x     block X
     * @param z     block Z
     * @return the covering claim, or {@code null} if unclaimed
     */
    public @Nullable Claim claimAt(@NotNull final World world, final int x, final int z) {
        final UUID worldId = world.getUID();
        final Set<String> keys = chunkIndex.get(chunkKey(worldId,
                x >> CHUNK_SHIFT, z >> CHUNK_SHIFT));
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        for (final String key : keys) {
            final Claim claim = claimsByKey.get(key);
            // Verify world identity explicitly: the packed chunk key folds in a
            // world hash, so a (rare) cross-world hash collision could place a
            // foreign-world claim in this bucket. Checking the stored world UUID
            // makes a collision harmless rather than a false protection match.
            if (claim != null
                    && claim.worldId().equals(worldId)
                    && claim.contains(x, z)) {
                return claim;
            }
        }
        return null;
    }

    /**
     * Convenience overload taking a {@link Location}.
     *
     * @param loc location to test
     * @return the covering claim, or {@code null}
     */
    public @Nullable Claim claimAt(@NotNull final Location loc) {
        final World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        return claimAt(world, loc.getBlockX(), loc.getBlockZ());
    }

    /**
     * Builds a {@link ClaimQuery} describing control of a location for an actor.
     *
     * @param loc   location to test
     * @param actor acting player UUID, or {@code null} for environmental checks
     * @return a populated query result (claim may be {@code null})
     */
    public @NotNull ClaimQuery query(@NotNull final Location loc, @Nullable final UUID actor) {
        return new ClaimQuery(claimAt(loc), actor);
    }

    /**
     * Looks up the claim centred exactly on the given block (i.e. the Claim
     * Block itself), if one exists there.
     *
     * @param world world
     * @param x     block X
     * @param y     block Y
     * @param z     block Z
     * @return the claim centred here, or {@code null}
     */
    public @Nullable Claim claimCenteredAt(@NotNull final World world,
                                           final int x, final int y, final int z) {
        return claimsByKey.get(Claim.keyOf(world.getUID(), x, y, z));
    }

    // ----- Mutations -------------------------------------------------------

    /**
     * Tests whether a proposed claim of the given type, centred at the location,
     * would overlap any claim not owned by {@code owner}.
     *
     * @param owner  prospective owner
     * @param type   claim type (determines radius)
     * @param center proposed center
     * @return {@code true} if it would overlap a foreign claim
     */
    public boolean wouldOverlapForeign(@NotNull final UUID owner,
                                       @NotNull final ClaimType type,
                                       @NotNull final Location center) {
        final World world = center.getWorld();
        if (world == null) {
            return false;
        }
        final int radius = radiusFor(type);
        final int cx = center.getBlockX();
        final int cz = center.getBlockZ();

        // Examine every chunk the proposed square touches.
        final int minCX = (cx - radius) >> CHUNK_SHIFT;
        final int maxCX = (cx + radius) >> CHUNK_SHIFT;
        final int minCZ = (cz - radius) >> CHUNK_SHIFT;
        final int maxCZ = (cz + radius) >> CHUNK_SHIFT;

        for (int chunkX = minCX; chunkX <= maxCX; chunkX++) {
            for (int chunkZ = minCZ; chunkZ <= maxCZ; chunkZ++) {
                final Set<String> keys = chunkIndex.get(
                        chunkKey(world.getUID(), chunkX, chunkZ));
                if (keys == null) {
                    continue;
                }
                for (final String key : keys) {
                    final Claim other = claimsByKey.get(key);
                    if (other == null
                            || !other.worldId().equals(world.getUID())
                            || other.owner().equals(owner)) {
                        continue;
                    }
                    if (squaresOverlap(cx, cz, radius, other)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Creates, indexes, and persists a new claim.
     *
     * @param owner  owning player UUID
     * @param type   claim type
     * @param center center location (the placed Claim Block)
     * @return the created claim
     */
    public @NotNull Claim createClaim(@NotNull final UUID owner,
                                      @NotNull final ClaimType type,
                                      @NotNull final Location center) {
        final Claim claim = new Claim(owner, type, center, radiusFor(type));
        index(claim);
        try {
            storage.saveClaim(claim);
        } catch (final Exception ex) {
            plugin.getLogger().severe("Failed to save claim " + claim.key()
                    + ": " + ex.getMessage());
        }
        return claim;
    }

    /**
     * Removes a claim from memory, indexes, and storage.
     *
     * @param claim claim to remove
     */
    public void removeClaim(@NotNull final Claim claim) {
        unIndex(claim);
        try {
            storage.deleteClaim(claim.key());
        } catch (final Exception ex) {
            plugin.getLogger().severe("Failed to delete claim " + claim.key()
                    + ": " + ex.getMessage());
        }
    }

    /**
     * @return an unmodifiable snapshot of all claims (for admin/debug use; not
     *         for hot paths)
     */
    public @NotNull Collection<Claim> all() {
        return Collections.unmodifiableCollection(claimsByKey.values());
    }

    // ----- Indexing internals ----------------------------------------------

    /**
     * Adds a claim to all in-memory indexes.
     *
     * @param claim claim to index
     */
    private void index(final Claim claim) {
        claimsByKey.put(claim.key(), claim);
        for (final long chunk : touchedChunks(claim)) {
            chunkIndex.computeIfAbsent(chunk, k -> new HashSet<>()).add(claim.key());
        }
        countByOwner.merge(claim.owner(), 1, Integer::sum);
    }

    /**
     * Removes a claim from all in-memory indexes.
     *
     * @param claim claim to remove
     */
    private void unIndex(final Claim claim) {
        claimsByKey.remove(claim.key());
        for (final long chunk : touchedChunks(claim)) {
            final Set<String> keys = chunkIndex.get(chunk);
            if (keys != null) {
                keys.remove(claim.key());
                if (keys.isEmpty()) {
                    chunkIndex.remove(chunk);
                }
            }
        }
        final Integer current = countByOwner.get(claim.owner());
        if (current != null) {
            if (current <= 1) {
                countByOwner.remove(claim.owner());
            } else {
                countByOwner.put(claim.owner(), current - 1);
            }
        }
    }

    /**
     * Computes the packed keys of every chunk a claim's square overlaps.
     *
     * @param claim claim to inspect
     * @return list of packed chunk keys
     */
    private List<Long> touchedChunks(final Claim claim) {
        final int minCX = (claim.x() - claim.radius()) >> CHUNK_SHIFT;
        final int maxCX = (claim.x() + claim.radius()) >> CHUNK_SHIFT;
        final int minCZ = (claim.z() - claim.radius()) >> CHUNK_SHIFT;
        final int maxCZ = (claim.z() + claim.radius()) >> CHUNK_SHIFT;
        final List<Long> out = new ArrayList<>();
        for (int chunkX = minCX; chunkX <= maxCX; chunkX++) {
            for (int chunkZ = minCZ; chunkZ <= maxCZ; chunkZ++) {
                out.add(chunkKey(claim.worldId(), chunkX, chunkZ));
            }
        }
        return out;
    }

    /**
     * Packs a world UUID and chunk coordinates into a single long key.
     *
     * <p>The world's {@code hashCode} is folded into the high bits so claims in
     * different worlds with the same chunk coordinates do not collide in the
     * common case. Exact world identity is still verified by the claim's stored
     * world UUID during {@link Claim#contains}, so a rare hash collision cannot
     * cause incorrect protection — only a slightly larger candidate set.</p>
     *
     * @param worldId world UUID
     * @param chunkX  chunk X
     * @param chunkZ  chunk Z
     * @return packed key
     */
    private long chunkKey(final UUID worldId, final int chunkX, final int chunkZ) {
        final long world = worldId.hashCode() & 0xFFFFFFFFL;
        final long coords = ((long) chunkX & 0x3FFFFFFL) << 26 | ((long) chunkZ & 0x3FFFFFFL);
        return world << 32 ^ coords;
    }

    /**
     * Square-vs-square overlap test on the X/Z plane.
     *
     * @param cx     proposed center X
     * @param cz     proposed center Z
     * @param radius proposed radius
     * @param other  existing claim
     * @return {@code true} if the squares overlap
     */
    private boolean squaresOverlap(final int cx, final int cz, final int radius,
                                   final Claim other) {
        final int dx = Math.abs(cx - other.x());
        final int dz = Math.abs(cz - other.z());
        final int reach = radius + other.radius();
        return dx <= reach && dz <= reach;
    }
}
