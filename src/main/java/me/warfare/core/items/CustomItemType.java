package me.warfare.core.items;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Every custom item the plugin can produce.
 *
 * <p>Normal TNT is intentionally absent: it stays a vanilla item and is handled
 * by the explosion system via its material, not a PDC tag. The values here carry
 * only <em>defaults</em> (base material, display name, default CustomModelData,
 * and the config path under which overrides live). Actual runtime values are
 * resolved by {@link ItemManager} from config, so server owners can retune them
 * without code changes.</p>
 */
public enum CustomItemType {

    /** Places a normal-radius claim. Visually a bedrock block via the pack. */
    CLAIM_BLOCK(Material.BEDROCK, "<gradient:#55ffff:#5555ff>Claim Block</gradient>", 1001),

    /** Places a larger-radius claim. */
    ADVANCED_CLAIM_BLOCK(Material.BEDROCK,
            "<gradient:#ffaa00:#ff5555>Advanced Claim Block</gradient>", 1002),

    /** Mid-tier TNT able to break obsidian-class blocks. */
    PACKED_TNT(Material.TNT, "<color:#ff8800>Packed TNT</color>", 1003),

    /** Top-tier TNT able to break claim blocks. */
    REINFORCED_TNT(Material.TNT, "<color:#ff2222>Reinforced TNT</color>", 1004);

    private final Material baseMaterial;
    private final String defaultDisplayName;
    private final int defaultModelData;

    /**
     * @param baseMaterial       vanilla material the custom item is built on
     * @param defaultDisplayName default MiniMessage display name
     * @param defaultModelData   default CustomModelData value
     */
    CustomItemType(final Material baseMaterial,
                   final String defaultDisplayName,
                   final int defaultModelData) {
        this.baseMaterial = baseMaterial;
        this.defaultDisplayName = defaultDisplayName;
        this.defaultModelData = defaultModelData;
    }

    /** @return the vanilla material this custom item is based on */
    public @NotNull Material baseMaterial() {
        return baseMaterial;
    }

    /** @return the default MiniMessage display name */
    public @NotNull String defaultDisplayName() {
        return defaultDisplayName;
    }

    /** @return the default CustomModelData value */
    public int defaultModelData() {
        return defaultModelData;
    }

    /**
     * The lowercase config key for this item (e.g. {@code claim_block}), used to
     * build paths like {@code items.claim_block.model-data}.
     *
     * @return lowercase identifier
     */
    public @NotNull String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Resolves a type from its stored PDC name, case-insensitively.
     *
     * @param raw stored type name; may be {@code null}
     * @return the matching type, or {@code null} if unrecognised
     */
    public static @Nullable CustomItemType fromString(final String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return CustomItemType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ex) {
            return null;
        }
    }
}
