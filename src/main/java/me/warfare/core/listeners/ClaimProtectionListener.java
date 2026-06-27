package me.warfare.core.listeners;

import me.warfare.core.WarfareCore;
import me.warfare.core.claims.Claim;
import me.warfare.core.claims.ClaimManager;
import me.warfare.core.config.ConfigManager;
import me.warfare.core.utils.MessageService;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Enforces player-action protection inside claims.
 *
 * <p>Covers block breaking, block placing, bucket use, and interaction with
 * blocks. Each rule can be toggled in config under {@code claims.protection.*}.
 * Players with the bypass permission ({@code claims.bypass-permission}) ignore
 * all of these checks, which is useful for staff.</p>
 *
 * <p>Environmental protection (pistons, fluids, fire, explosions) lives in
 * {@link ClaimEnvironmentListener} to keep each listener focused and readable.</p>
 */
public final class ClaimProtectionListener implements Listener {

    private final ClaimManager claims;
    private final ConfigManager config;
    private final MessageService messages;

    /**
     * @param plugin owning plugin
     */
    public ClaimProtectionListener(@NotNull final WarfareCore plugin) {
        this.claims = plugin.claimManager();
        this.config = plugin.configManager();
        this.messages = plugin.messageService();
    }

    /**
     * Prevents non-owners breaking blocks inside a claim.
     *
     * @param event block break event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event) {
        if (!ruleEnabled("break")) {
            return;
        }
        if (denied(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            notifyProtected(event.getPlayer());
        }
    }

    /**
     * Prevents non-owners placing blocks inside a claim. Claim Block creation is
     * handled at HIGH priority in {@link ClaimPlacementListener}; this LOW-priority
     * handler runs first and would cancel a foreign placement before creation is
     * attempted, but a Claim Block placed on an unclaimed edge still reaches the
     * placement listener normally.
     *
     * @param event block place event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        if (!ruleEnabled("place")) {
            return;
        }
        if (denied(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            notifyProtected(event.getPlayer());
        }
    }

    /**
     * Prevents non-owners emptying buckets (placing fluids) inside a claim.
     *
     * @param event bucket empty event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(final PlayerBucketEmptyEvent event) {
        if (!ruleEnabled("buckets")) {
            return;
        }
        if (denied(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            notifyProtected(event.getPlayer());
        }
    }

    /**
     * Prevents non-owners filling buckets (removing fluids) inside a claim.
     *
     * @param event bucket fill event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(final PlayerBucketFillEvent event) {
        if (!ruleEnabled("buckets")) {
            return;
        }
        if (denied(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            notifyProtected(event.getPlayer());
        }
    }

    /**
     * Prevents non-owners interacting with blocks (chests, doors, etc.) inside a
     * claim. Only right-clicks on blocks are gated; this does not affect normal
     * walking or item use in open air.
     *
     * @param event interact event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(final PlayerInteractEvent event) {
        if (!ruleEnabled("interact")) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        final Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        if (denied(event.getPlayer(), clicked.getLocation())) {
            event.setCancelled(true);
            notifyProtected(event.getPlayer());
        }
    }

    // ----- Helpers ---------------------------------------------------------

    /**
     * Whether a player is denied an action at a location: the spot is in a claim
     * they do not own and they lack the bypass permission.
     *
     * @param player acting player
     * @param loc    target location
     * @return {@code true} if the action should be cancelled
     */
    private boolean denied(final Player player, final Location loc) {
        if (player.hasPermission(bypassPermission())) {
            return false;
        }
        final Claim claim = claims.claimAt(loc);
        return claim != null && !claim.owner().equals(player.getUniqueId());
    }

    /**
     * @param rule rule name under {@code claims.protection}
     * @return whether that protection rule is enabled (default true)
     */
    private boolean ruleEnabled(final String rule) {
        return config.getBoolean("claims.protection." + rule, true);
    }

    /** @return the configured bypass permission node */
    private String bypassPermission() {
        return config.getString("claims.bypass-permission", "warfare.claims.bypass");
    }

    /**
     * Sends the "area protected" message to a player.
     *
     * @param player player to notify
     */
    private void notifyProtected(final Player player) {
        messages.send(player, "messages.claim-protected",
                "<red>This area is protected by a claim.");
    }
}
