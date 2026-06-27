package me.warfare.core.explosions;

import me.warfare.core.WarfareCore;
import me.warfare.core.claims.Claim;
import me.warfare.core.claims.ClaimManager;
import me.warfare.core.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes and applies custom, tier-aware explosions.
 *
 * <p>Vanilla blast resistance is never consulted. Instead the caller cancels the
 * vanilla explosion and invokes {@link #explode}, which:</p>
 * <ol>
 *   <li>Enumerates candidate blocks in a sphere (or cube) around the center —
 *       only blocks within the configured radius, never a world scan.</li>
 *   <li>First resolves any claim whose <em>center</em> lies in range: if the tier
 *       is REINFORCED the claim is removed (making its area raidable); otherwise
 *       the claim and all blocks it protects are left intact.</li>
 *   <li>Asks {@link BlockBreakPolicy} about every remaining candidate and breaks
 *       those allowed, optionally dropping items like vanilla.</li>
 * </ol>
 *
 * <p>Protected claims short-circuit cheaply: a candidate block inside a still-
 * standing foreign claim is skipped without further checks.</p>
 */
public final class ExplosionService {

    private final WarfareCore plugin;
    private final ConfigManager config;
    private final ClaimManager claims;
    private final BlockBreakPolicy policy;

    /**
     * @param plugin owning plugin
     * @param policy block-break decision policy
     */
    public ExplosionService(@NotNull final WarfareCore plugin,
                            @NotNull final BlockBreakPolicy policy) {
        this.plugin = plugin;
        this.config = plugin.configManager();
        this.claims = plugin.claimManager();
        this.policy = policy;
    }

    /** @return the break policy (exposed for reloads) */
    public @NotNull BlockBreakPolicy policy() {
        return policy;
    }

    /**
     * Performs a custom explosion of the given tier at a center location.
     *
     * @param center world location of the blast center
     * @param tier   TNT tier driving radius and break rules
     */
    public void explode(@NotNull final Location center, @NotNull final TntTier tier) {
        final World world = center.getWorld();
        if (world == null) {
            return;
        }

        final int radius = radiusFor(tier);
        final boolean cube = config.getBoolean("explosions.shape-cube", false);
        final boolean drops = config.getBoolean("explosions.drop-items", true);
        final int radiusSq = radius * radius;

        final int cx = center.getBlockX();
        final int cy = center.getBlockY();
        final int cz = center.getBlockZ();

        // Phase 1: handle any claim centers within range. A REINFORCED blast that
        // reaches a Claim Block removes that claim *before* breaking blocks, so
        // the freshly unprotected area becomes destroyable in phase 2.
        final List<Claim> removedClaims = resolveClaimCenters(world, cx, cy, cz, radius, tier);

        // Phase 2: enumerate candidate blocks and break the allowed ones.
        final List<Block> toBreak = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (!cube && (dx * dx + dy * dy + dz * dz) > radiusSq) {
                        continue; // outside the sphere
                    }
                    final int bx = cx + dx;
                    final int by = cy + dy;
                    final int bz = cz + dz;

                    // Skip blocks still protected by a surviving foreign claim.
                    final Claim covering = claims.claimAt(world, bx, bz);
                    if (covering != null && !removedClaims.contains(covering)) {
                        continue;
                    }

                    final Block block = world.getBlockAt(bx, by, bz);
                    if (policy.canBreak(tier, block)) {
                        toBreak.add(block);
                    }
                }
            }
        }

        applyBreaks(toBreak, drops);
    }

    /**
     * Finds claim centers within the blast and, for REINFORCED tier, removes
     * them. Lower tiers leave claim centers (and thus their protection) intact.
     *
     * @param world  blast world
     * @param cx     center X
     * @param cy     center Y
     * @param cz     center Z
     * @param radius blast radius
     * @param tier   blast tier
     * @return the list of claims removed by this blast (empty for non-REINFORCED)
     */
    private List<Claim> resolveClaimCenters(final World world, final int cx, final int cy,
                                            final int cz, final int radius,
                                            final TntTier tier) {
        final List<Claim> removed = new ArrayList<>();
        if (tier != TntTier.REINFORCED) {
            return removed;
        }
        // Only claim centers physically within the blast box are candidates; we
        // check the claim manager's centered lookup per nearby block position.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    final Claim claim = claims.claimCenteredAt(
                            world, cx + dx, cy + dy, cz + dz);
                    if (claim != null && !removed.contains(claim)) {
                        removed.add(claim);
                    }
                }
            }
        }
        // Remove them now so phase 2 sees the area as unprotected.
        for (final Claim claim : removed) {
            claims.removeClaim(claim);
        }
        return removed;
    }

    /**
     * Breaks all blocks in the list, dropping items if configured. The claim
     * center blocks among them are broken naturally here too (their claims were
     * already removed in phase 1).
     *
     * @param blocks blocks to destroy
     * @param drops  whether to drop items like vanilla
     */
    private void applyBreaks(final List<Block> blocks, final boolean drops) {
        for (final Block block : blocks) {
            if (drops) {
                block.breakNaturally();
            } else {
                block.setType(org.bukkit.Material.AIR, false);
            }
        }
    }

    /**
     * Resolves the configured radius for a tier, with a sane fallback.
     *
     * @param tier TNT tier
     * @return blast radius in blocks
     */
    public int radiusFor(@NotNull final TntTier tier) {
        final int def = switch (tier) {
            case NORMAL -> 5;
            case PACKED -> 7;
            case REINFORCED -> 9;
        };
        return config.getInt("explosions." + tier.configKey() + ".radius", def);
    }
}
