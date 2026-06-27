package me.warfare.core.listeners;

import me.warfare.core.WarfareCore;
import me.warfare.core.explosions.ExplosionService;
import me.warfare.core.explosions.TntRegistry;
import me.warfare.core.explosions.TntTier;
import me.warfare.core.items.CustomItemType;
import me.warfare.core.items.ItemManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bridges Minecraft's TNT lifecycle to the custom {@link ExplosionService}.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li><b>Place</b> — when a custom TNT block (Packed/Reinforced) is placed, its
 *       tier is recorded against the block position in {@link TntRegistry}.</li>
 *   <li><b>Prime</b> — when a {@link TNTPrimed} entity spawns, the recorded tier
 *       at that position (if any) is transferred onto the entity so it survives
 *       to detonation. Vanilla TNT with no record is treated as NORMAL.</li>
 *   <li><b>Detonate</b> — on {@link EntityExplodeEvent} the vanilla block damage
 *       is cancelled (the affected-block list is cleared) and the custom engine
 *       runs the tier-appropriate blast instead.</li>
 * </ol>
 *
 * <p>TNT minecarts ({@link ExplosiveMinecart}) carry their tier directly on the
 * entity and route through the same detonation path, giving minecart/TNT parity.</p>
 */
public final class ExplosionListener implements Listener {

    private final ExplosionService explosions;
    private final TntRegistry registry;
    private final ItemManager items;

    /**
     * @param plugin   owning plugin
     * @param registry shared TNT registry for tier tracking
     */
    public ExplosionListener(@NotNull final WarfareCore plugin,
                             @NotNull final TntRegistry registry) {
        this.explosions = plugin.explosionService();
        this.registry = registry;
        this.items = plugin.itemManager();
    }

    /**
     * Records the tier of a placed custom TNT block so it can be applied when the
     * block is later primed.
     *
     * @param event block place event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent event) {
        final ItemStack inHand = event.getItemInHand();
        final TntTier tier = tierOfItem(inHand);
        if (tier == null) {
            return; // not custom TNT
        }
        final Block block = event.getBlockPlaced();
        registry.markPlaced(block.getX(), block.getY(), block.getZ(), tier);
    }

    /**
     * Transfers a recorded block tier onto a freshly spawned primed-TNT entity.
     *
     * @param event entity spawn event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(final EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed primed)) {
            return;
        }
        // Primed TNT spawns at the block centre; floor to block coords.
        final int x = primed.getLocation().getBlockX();
        final int y = primed.getLocation().getBlockY();
        final int z = primed.getLocation().getBlockZ();
        final TntTier tier = registry.consumePlaced(x, y, z);
        if (tier != null) {
            registry.tagEntity(primed, tier);
        }
    }

    /**
     * Intercepts a TNT or TNT-minecart explosion, cancels vanilla block damage,
     * and runs the tier-appropriate custom blast.
     *
     * @param event entity explode event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(final EntityExplodeEvent event) {
        final Entity entity = event.getEntity();

        final TntTier tier = resolveTier(entity);
        if (tier == null) {
            return; // not our concern; leave normal handling to others
        }

        // Take over block destruction completely: clear vanilla's list so it
        // removes nothing, then run our controlled blast.
        event.blockList().clear();
        explosions.explode(event.getLocation(), tier);
    }

    // ----- Helpers ---------------------------------------------------------

    /**
     * Determines the tier driving an exploding entity.
     *
     * @param entity exploding entity
     * @return the tier, or {@code null} if this entity is not custom/vanilla TNT
     */
    private @Nullable TntTier resolveTier(final Entity entity) {
        if (entity instanceof TNTPrimed || entity instanceof ExplosiveMinecart) {
            final TntTier tagged = registry.readTier(entity);
            // Tagged custom tier wins; otherwise treat plain TNT as NORMAL so it
            // also uses the configured (larger) radius via the custom engine.
            return tagged != null ? tagged : TntTier.NORMAL;
        }
        return null;
    }

    /**
     * Maps a held item to its TNT tier, or {@code null} if not custom TNT.
     *
     * @param stack held item
     * @return tier, or {@code null}
     */
    private @Nullable TntTier tierOfItem(@Nullable final ItemStack stack) {
        final CustomItemType type = items.identify(stack);
        return TntTier.fromItem(type);
    }
}
