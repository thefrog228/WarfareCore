package me.warfare.core.listeners;

import me.warfare.core.WarfareCore;
import me.warfare.core.claims.ClaimManager;
import me.warfare.core.claims.ClaimType;
import me.warfare.core.config.ConfigManager;
import me.warfare.core.items.CustomItemType;
import me.warfare.core.items.ItemManager;
import me.warfare.core.utils.MessageService;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a placed Claim Block into a claim.
 *
 * <p>When a player places an item identified as a {@link CustomItemType#CLAIM_BLOCK}
 * or {@link CustomItemType#ADVANCED_CLAIM_BLOCK}, this listener validates the
 * placement and either creates the claim or cancels the event. Cancelling a
 * {@link BlockPlaceEvent} returns the item to the player's hand automatically, so
 * a rejected placement never costs the player their Claim Block.</p>
 *
 * <p>Validation order: world allowed → per-player limit → no overlap with a
 * foreign claim. Each failure sends a configurable message and cancels.</p>
 */
public final class ClaimPlacementListener implements Listener {

    private final ClaimManager claims;
    private final ItemManager items;
    private final MessageService messages;
    private final ConfigManager config;

    /**
     * @param plugin owning plugin
     */
    public ClaimPlacementListener(@NotNull final WarfareCore plugin) {
        this.claims = plugin.claimManager();
        this.items = plugin.itemManager();
        this.messages = plugin.messageService();
        this.config = plugin.configManager();
    }

    /**
     * Validates and creates a claim when a Claim Block is placed.
     *
     * @param event the block place event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        final ItemStack inHand = event.getItemInHand();
        final ClaimType type = claimTypeOf(inHand);
        if (type == null) {
            return; // Not a claim block; nothing to do.
        }

        final Player player = event.getPlayer();
        final Block block = event.getBlockPlaced();

        // 1. World must be enabled for claims.
        if (!isWorldEnabled(block.getWorld().getName())) {
            messages.sendRaw(player, config.getString(
                    "messages.claim-world-disabled",
                    "<red>Claims are disabled in this world."));
            event.setCancelled(true);
            return;
        }

        // 2. Per-player limit.
        if (claims.ownedCount(player.getUniqueId()) >= claims.maxPerPlayer()) {
            messages.sendRaw(player, config.getString(
                    "messages.claim-limit-reached",
                    "<red>You have reached your claim limit (<yellow>"
                            + claims.maxPerPlayer() + "</yellow>)."));
            event.setCancelled(true);
            return;
        }

        // 3. No overlap with a claim the player does not own.
        if (claims.wouldOverlapForeign(player.getUniqueId(), type, block.getLocation())) {
            messages.sendRaw(player, config.getString(
                    "messages.claim-overlap",
                    "<red>That would overlap someone else's claim."));
            event.setCancelled(true);
            return;
        }

        // All checks passed: create the claim centred on the placed block.
        claims.createClaim(player.getUniqueId(), type, block.getLocation());
        messages.sendRaw(player, config.getString(
                "messages.claim-created",
                "<green>Claim created. Radius: <yellow>"
                        + claims.radiusFor(type) + "</yellow> blocks."));
    }

    /**
     * Maps a held item to the claim type it would create, or {@code null} if the
     * item is not a claim block.
     *
     * @param stack held item
     * @return claim type, or {@code null}
     */
    private @Nullable ClaimType claimTypeOf(@Nullable final ItemStack stack) {
        final CustomItemType custom = items.identify(stack);
        if (custom == CustomItemType.CLAIM_BLOCK) {
            return ClaimType.NORMAL;
        }
        if (custom == CustomItemType.ADVANCED_CLAIM_BLOCK) {
            return ClaimType.ADVANCED;
        }
        return null;
    }

    /**
     * Whether claims are allowed in a world. If the config list is absent or
     * empty, all worlds are allowed.
     *
     * @param worldName world name
     * @return {@code true} if claims may be created here
     */
    private boolean isWorldEnabled(final String worldName) {
        final var enabled = config.raw().getStringList("claims.enabled-worlds");
        return enabled.isEmpty() || enabled.contains(worldName);
    }
}
