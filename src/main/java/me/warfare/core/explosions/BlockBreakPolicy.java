package me.warfare.core.explosions;

import me.warfare.core.claims.ClaimManager;
import me.warfare.core.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Decides, for a given TNT tier, whether a specific block may be destroyed.
 *
 * <p>Implements the three progression rules from the design:</p>
 * <ol>
 *   <li><b>Claim blocks</b> (the center block of a claim) may be broken only by
 *       {@link TntTier#REINFORCED}.</li>
 *   <li><b>Strong blocks</b> (obsidian, crying obsidian, respawn anchor, and any
 *       others configured) may be broken only by {@link TntTier#PACKED} or
 *       stronger.</li>
 *   <li><b>Everything else</b> that is breakable may be broken by any tier,
 *       except blocks on a configurable global blacklist (e.g. bedrock) which no
 *       tier may break.</li>
 * </ol>
 *
 * <p>The strong-block and never-break sets are cached as {@link EnumSet}s and
 * rebuilt on {@link #reload()} so lookups during an explosion are O(1) with no
 * per-block config parsing.</p>
 */
public final class BlockBreakPolicy {

    private final ConfigManager config;
    private final ClaimManager claims;

    /** Blocks requiring PACKED or stronger (obsidian-class). */
    private Set<Material> strongBlocks = EnumSet.noneOf(Material.class);

    /** Blocks no tier may ever break (e.g. bedrock). */
    private Set<Material> neverBreak = EnumSet.noneOf(Material.class);

    /**
     * @param config plugin config
     * @param claims claim manager, used to detect claim center blocks
     */
    public BlockBreakPolicy(@NotNull final ConfigManager config,
                            @NotNull final ClaimManager claims) {
        this.config = config;
        this.claims = claims;
        reload();
    }

    /**
     * Rebuilds the cached material sets from config. Call after a config reload.
     */
    public void reload() {
        this.strongBlocks = parseMaterials(config.raw().getStringList(
                "explosions.strong-blocks"), defaultStrongBlocks());
        this.neverBreak = parseMaterials(config.raw().getStringList(
                "explosions.never-break"), defaultNeverBreak());
    }

    /**
     * Decides whether a tier may break a block at its current location.
     *
     * @param tier  the exploding TNT tier
     * @param block the candidate block
     * @return {@code true} if this tier is allowed to destroy the block
     */
    public boolean canBreak(@NotNull final TntTier tier, @NotNull final Block block) {
        final Material type = block.getType();

        // Air is never "broken" by us; let it be.
        if (type.isAir()) {
            return false;
        }

        // Rule: a claim's center block (the Claim Block) only yields to REINFORCED.
        // Checked BEFORE the never-break material list because the claim block is
        // visually bedrock by default, and bedrock is otherwise globally
        // unbreakable — claim identity must win over the raw material here.
        if (isClaimCenter(block)) {
            return tier == TntTier.REINFORCED;
        }

        // Rule: globally unbreakable blocks are off-limits to every tier.
        if (neverBreak.contains(type)) {
            return false;
        }

        // Rule: obsidian-class strong blocks require PACKED or stronger.
        if (strongBlocks.contains(type)) {
            return tier.isAtLeast(TntTier.PACKED);
        }

        // Everything else breakable: any tier may destroy it.
        return true;
    }

    /**
     * Whether a block is the exact center of an existing claim (its Claim Block).
     *
     * @param block candidate block
     * @return {@code true} if a claim is centred on this block
     */
    public boolean isClaimCenter(@NotNull final Block block) {
        return claims.claimCenteredAt(block.getWorld(),
                block.getX(), block.getY(), block.getZ()) != null;
    }

    // ----- Parsing helpers -------------------------------------------------

    /**
     * Parses a list of material names into an {@link EnumSet}, falling back to a
     * default set if the configured list is empty. Invalid names are skipped.
     *
     * @param names    configured material names
     * @param fallback default set when {@code names} is empty
     * @return parsed material set
     */
    private Set<Material> parseMaterials(final List<String> names,
                                         final Set<Material> fallback) {
        if (names == null || names.isEmpty()) {
            return fallback;
        }
        final Set<Material> set = EnumSet.noneOf(Material.class);
        for (final String name : names) {
            final Material material = Material.matchMaterial(name);
            if (material != null) {
                set.add(material);
            }
        }
        return set.isEmpty() ? fallback : set;
    }

    /** @return default obsidian-class blocks when none are configured */
    private Set<Material> defaultStrongBlocks() {
        return EnumSet.of(
                Material.OBSIDIAN,
                Material.CRYING_OBSIDIAN,
                Material.RESPAWN_ANCHOR);
    }

    /** @return default never-break blocks when none are configured */
    private Set<Material> defaultNeverBreak() {
        return EnumSet.of(
                Material.BEDROCK,
                Material.BARRIER,
                Material.END_PORTAL_FRAME);
    }
}
