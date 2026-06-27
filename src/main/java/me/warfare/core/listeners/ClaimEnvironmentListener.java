package me.warfare.core.listeners;

import me.warfare.core.WarfareCore;
import me.warfare.core.claims.Claim;
import me.warfare.core.claims.ClaimManager;
import me.warfare.core.config.ConfigManager;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Protects claims from environmental and indirect block changes.
 *
 * <p>These rules ensure a claim cannot be bypassed by mechanics rather than by
 * a player directly: pistons cannot push/pull claimed blocks across the border,
 * fluids cannot flow into claims, fire cannot spread or burn claimed blocks, and
 * ordinary (vanilla/creeper/etc.) explosions cannot remove claimed blocks.</p>
 *
 * <p>Custom TNT tiers are handled separately by the explosion system; this
 * listener deliberately covers only "normal" explosions so the two systems do
 * not conflict. Each rule is toggleable under {@code claims.protection.*}.</p>
 */
public final class ClaimEnvironmentListener implements Listener {

    private final ClaimManager claims;
    private final ConfigManager config;

    /**
     * @param plugin owning plugin
     */
    public ClaimEnvironmentListener(@NotNull final WarfareCore plugin) {
        this.claims = plugin.claimManager();
        this.config = plugin.configManager();
    }

    /**
     * Cancels piston extension if any moved block, or the block in front of the
     * piston head, lies inside a claim.
     *
     * @param event piston extend event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(final BlockPistonExtendEvent event) {
        if (!ruleEnabled("pistons")) {
            return;
        }
        for (final Block block : event.getBlocks()) {
            if (isClaimed(block)) {
                event.setCancelled(true);
                return;
            }
            // Also protect the destination the block is being pushed into.
            if (isClaimed(block.getRelative(event.getDirection()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Cancels piston retraction if any pulled block lies inside a claim.
     *
     * @param event piston retract event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(final BlockPistonRetractEvent event) {
        if (!ruleEnabled("pistons")) {
            return;
        }
        for (final Block block : event.getBlocks()) {
            if (isClaimed(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Cancels fluid flow whose destination is inside a claim but whose source is
     * not within the same claim. Flow entirely inside one claim is allowed.
     *
     * @param event fluid flow event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFluidFlow(final BlockFromToEvent event) {
        if (!ruleEnabled("fluids")) {
            return;
        }
        final Claim toClaim = claims.claimAt(event.getToBlock().getLocation());
        if (toClaim == null) {
            return; // destination unclaimed
        }
        final Claim fromClaim = claims.claimAt(event.getBlock().getLocation());
        // Allow flow that stays within the same claim; block flow entering it.
        if (!toClaim.equals(fromClaim)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents fire from spreading onto blocks inside a claim.
     *
     * @param event block spread event (fire is the relevant source)
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(final BlockSpreadEvent event) {
        if (!ruleEnabled("fire")) {
            return;
        }
        if (isClaimed(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents fire from burning (destroying) blocks inside a claim.
     *
     * @param event block burn event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(final BlockBurnEvent event) {
        if (!ruleEnabled("fire")) {
            return;
        }
        if (isClaimed(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /**
     * Removes claimed blocks from an ordinary explosion's affected-block list, so
     * vanilla/creeper/TNT-minecart-less explosions cannot raid claims. Custom TNT
     * tiers are processed by the explosion system instead.
     *
     * @param event entity explosion event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(final EntityExplodeEvent event) {
        if (!ruleEnabled("explosions")) {
            return;
        }
        final List<Block> blocks = event.blockList();
        // Iterate a copy-free removeIf for performance; claimAt is O(claims-in-chunk).
        blocks.removeIf(this::isClaimed);
    }

    // ----- Helpers ---------------------------------------------------------

    /**
     * @param block block to test
     * @return whether the block lies within any claim
     */
    private boolean isClaimed(final Block block) {
        final World world = block.getWorld();
        return claims.claimAt(world, block.getX(), block.getZ()) != null;
    }

    /**
     * @param rule rule name under {@code claims.protection}
     * @return whether that protection rule is enabled (default true)
     */
    private boolean ruleEnabled(final String rule) {
        return config.getBoolean("claims.protection." + rule, true);
    }
}
