package me.warfare.core.listeners;

import me.warfare.core.WarfareCore;
import me.warfare.core.config.ConfigManager;
import me.warfare.core.loot.LootManager;
import me.warfare.core.utils.MessageService;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Protects registered loot chests from destruction.
 *
 * <p>Loot chests are fixtures (e.g. in a PvP arena), so by default they cannot
 * be broken by players or explosions. Both protections are individually
 * toggleable under {@code loot.protection.*} for servers that want destructible
 * loot chests.</p>
 */
public final class LootListener implements Listener {

    private final LootManager loot;
    private final ConfigManager config;
    private final MessageService messages;

    /**
     * @param plugin owning plugin
     */
    public LootListener(@NotNull final WarfareCore plugin) {
        this.loot = plugin.lootManager();
        this.config = plugin.configManager();
        this.messages = plugin.messageService();
    }

    /**
     * Prevents players breaking a registered loot chest.
     *
     * @param event block break event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent event) {
        if (!config.getBoolean("loot.protection.break", true)) {
            return;
        }
        if (isLootChest(event.getBlock())) {
            event.setCancelled(true);
            messages.sendRaw(event.getPlayer(),
                    "<red>This loot chest cannot be broken.");
        }
    }

    /**
     * Removes registered loot chests from any explosion's affected-block list.
     *
     * @param event entity explode event
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(final EntityExplodeEvent event) {
        if (!config.getBoolean("loot.protection.explosions", true)) {
            return;
        }
        event.blockList().removeIf(this::isLootChest);
    }

    /**
     * @param block block to test
     * @return whether the block is a registered loot chest
     */
    private boolean isLootChest(final Block block) {
        return loot.isLootChest(block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ());
    }
}
