package me.warfare.core.explosions;

import me.warfare.core.items.CustomItemType;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The three TNT tiers and the raiding progression between them.
 *
 * <p>Each tier has a numeric {@code power} used by {@link BlockBreakPolicy} to
 * decide what it may destroy: a higher-power tier can break everything a lower
 * one can, plus its own additional block set. Radii and block sets are resolved
 * from config, not hard-coded here, so the progression can be retuned freely.</p>
 *
 * <p>Tier ordering by power: NORMAL (1) &lt; PACKED (2) &lt; REINFORCED (3).</p>
 */
public enum TntTier {

    /** Vanilla-crafted TNT; larger radius than vanilla but cannot break obsidian. */
    NORMAL(1, "normal"),

    /** Breaks obsidian-class blocks; cannot break claim blocks. */
    PACKED(2, "packed"),

    /** Breaks everything Packed can, plus claim blocks. */
    REINFORCED(3, "reinforced");

    private final int power;
    private final String configKey;

    /**
     * @param power     relative strength (higher breaks more)
     * @param configKey lowercase key used under {@code explosions.<key>}
     */
    TntTier(final int power, final String configKey) {
        this.power = power;
        this.configKey = configKey;
    }

    /** @return relative strength; higher tiers break a superset of lower tiers */
    public int power() {
        return power;
    }

    /** @return the config key segment for this tier (e.g. {@code packed}) */
    public @NotNull String configKey() {
        return configKey;
    }

    /**
     * @param other another tier
     * @return {@code true} if this tier is at least as strong as {@code other}
     */
    public boolean isAtLeast(@NotNull final TntTier other) {
        return this.power >= other.power;
    }

    /**
     * Maps a custom item type to its TNT tier.
     *
     * @param type custom item type (may be {@code null})
     * @return the corresponding tier, or {@code null} if the item is not custom TNT
     */
    public static @Nullable TntTier fromItem(@Nullable final CustomItemType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case PACKED_TNT -> PACKED;
            case REINFORCED_TNT -> REINFORCED;
            default -> null; // claim blocks are not TNT
        };
    }

    /**
     * Resolves a tier from its stored PDC name, case-insensitively.
     *
     * @param raw stored tier name (may be {@code null})
     * @return the matching tier, or {@code null} if unrecognised
     */
    public static @Nullable TntTier fromString(@Nullable final String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return TntTier.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * The tier of a plain vanilla TNT block (no custom tag). Vanilla TNT maps to
     * {@link #NORMAL} so that ordinary TNT also routes through the custom engine
     * with the configured larger-than-vanilla radius.
     *
     * @param material the exploding block/entity's material
     * @return {@link #NORMAL} if this is vanilla TNT, else {@code null}
     */
    public static @Nullable TntTier vanillaTierFor(@Nullable final Material material) {
        return material == Material.TNT ? NORMAL : null;
    }
}
