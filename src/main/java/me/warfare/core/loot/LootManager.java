package me.warfare.core.loot;

import me.warfare.core.WarfareCore;
import me.warfare.core.config.ConfigManager;
import me.warfare.core.storage.StorageProvider;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads loot tables and registered chests, and drives their periodic refilling.
 *
 * <p><b>Scheduling.</b> A single repeating task ticks at a configurable cadence
 * and refills every chest that is "due" — never one timer per chest. Refilling
 * only touches chests whose chunk is currently loaded; a chest in an unloaded
 * chunk has its timestamp advanced anyway so refills do not pile up and no chunk
 * is force-loaded. This keeps the system cheap on public servers.</p>
 *
 * <p>Loot tables come from {@code config.yml}; registered chests are persisted
 * through the {@link StorageProvider} seam, identical to claims.</p>
 */
public final class LootManager {

    /** Ticks per second, for converting the config interval into scheduler ticks. */
    private static final long TICKS_PER_SECOND = 20L;

    private final WarfareCore plugin;
    private final ConfigManager config;
    private final StorageProvider storage;

    /** Loaded loot tables by name. */
    private final Map<String, LootTable> tables = new HashMap<>();

    /** Registered chests by key. */
    private final Map<String, LootChest> chests = new HashMap<>();

    /** The single repeating refill task; null until started. */
    private BukkitTask refillTask;

    /**
     * @param plugin owning plugin
     */
    public LootManager(@NotNull final WarfareCore plugin) {
        this.plugin = plugin;
        this.config = plugin.configManager();
        this.storage = plugin.storageManager().provider();
    }

    /**
     * Loads tables from config and chests from storage, then starts the refill
     * scheduler. Called once during enable.
     */
    public void start() {
        loadTables();
        loadChests();
        startScheduler();
    }

    /**
     * Stops the refill scheduler. Called during disable.
     */
    public void stop() {
        if (refillTask != null) {
            refillTask.cancel();
            refillTask = null;
        }
    }

    /**
     * Reloads loot tables from config (chests and their timing are unaffected).
     */
    public void reloadTables() {
        loadTables();
    }

    // ----- Loading ---------------------------------------------------------

    /**
     * Parses all loot tables from the {@code loot.tables} config section.
     */
    private void loadTables() {
        tables.clear();
        final ConfigurationSection root = config.raw().getConfigurationSection("loot.tables");
        if (root == null) {
            plugin.getLogger().info("No loot tables configured.");
            return;
        }
        for (final String tableName : root.getKeys(false)) {
            final ConfigurationSection sec = root.getConfigurationSection(tableName);
            if (sec == null) {
                continue;
            }
            final int rolls = sec.getInt("rolls", 5);
            final List<LootItem> entries = new ArrayList<>();
            final List<Map<?, ?>> rawItems = sec.getMapList("items");
            for (final Map<?, ?> entry : rawItems) {
                final LootItem item = parseEntry(entry);
                if (item != null) {
                    entries.add(item);
                }
            }
            final LootTable table = new LootTable(tableName, entries, rolls);
            tables.put(tableName.toLowerCase(java.util.Locale.ROOT), table);
        }
        plugin.getLogger().info("Loaded " + tables.size() + " loot table(s).");
    }

    /**
     * Parses a single loot entry map from config.
     *
     * @param entry raw map (keys: item, min, max, weight)
     * @return parsed entry, or {@code null} if invalid
     */
    private @Nullable LootItem parseEntry(final Map<?, ?> entry) {
        final Object itemObj = entry.get("item");
        if (itemObj == null) {
            return null;
        }
        final String itemName = itemObj.toString();
        final int min = toInt(entry.get("min"), 1);
        final int max = toInt(entry.get("max"), min);
        final int weight = toInt(entry.get("weight"), 1);
        final LootItem item = LootItem.parse(itemName, min, max, weight);
        if (item == null) {
            plugin.getLogger().warning("Unknown loot item: " + itemName);
        }
        return item;
    }

    /**
     * Loads persisted loot chests into memory.
     */
    private void loadChests() {
        chests.clear();
        try {
            final Collection<LootChest> loaded = storage.loadLootChests();
            for (final LootChest chest : loaded) {
                chests.put(chest.key(), chest);
            }
            plugin.getLogger().info("Loaded " + chests.size() + " loot chest(s).");
        } catch (final Exception ex) {
            plugin.getLogger().severe("Failed to load loot chests: " + ex.getMessage());
        }
    }

    // ----- Scheduler -------------------------------------------------------

    /**
     * Starts the single repeating refill task at the configured cadence.
     */
    private void startScheduler() {
        final int checkSeconds = Math.max(1,
                config.getInt("loot.check-interval-seconds", 30));
        final long period = checkSeconds * TICKS_PER_SECOND;
        this.refillTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tickRefills, period, period);
    }

    /**
     * Refills all chests that are due, skipping those in unloaded chunks (their
     * timestamp still advances so refills do not accumulate).
     */
    private void tickRefills() {
        final long now = System.currentTimeMillis();
        final boolean wipe = config.getBoolean("loot.wipe-on-refill", true);
        for (final LootChest chest : chests.values()) {
            if (!chest.isDue(now)) {
                continue;
            }
            final World world = plugin.getServer().getWorld(chest.worldId());
            if (world == null) {
                continue;
            }
            // Only refill loaded chunks; advance time regardless so we don't
            // force-load chunks or build up a backlog.
            if (!world.isChunkLoaded(chest.x() >> 4, chest.z() >> 4)) {
                chest.markRefilled(now);
                persist(chest);
                continue;
            }
            refill(chest, world, wipe);
            chest.markRefilled(now);
            persist(chest);
        }
    }

    /**
     * Refills one chest from its bound table.
     *
     * @param chest chest to refill
     * @param world resolved world (chunk known loaded)
     * @param wipe  whether to clear existing contents first
     */
    private void refill(final LootChest chest, final World world, final boolean wipe) {
        final Block block = world.getBlockAt(chest.x(), chest.y(), chest.z());
        if (!(block.getState() instanceof Chest chestState)) {
            return; // block is no longer a chest
        }
        final LootTable table = tables.get(chest.tableName().toLowerCase(java.util.Locale.ROOT));
        if (table == null || table.isEmpty()) {
            return;
        }
        table.fill(chestState.getInventory(), plugin.itemManager(), wipe);
    }

    // ----- Registration API (used by commands) -----------------------------

    /**
     * Registers a new loot chest at a block and persists it.
     *
     * @param location  chest block location
     * @param tableName bound loot table name
     * @return the created chest, or {@code null} if the table is unknown
     */
    public @Nullable LootChest register(@NotNull final Location location,
                                        @NotNull final String tableName) {
        if (!tables.containsKey(tableName.toLowerCase(java.util.Locale.ROOT))) {
            return null;
        }
        final World world = location.getWorld();
        if (world == null) {
            return null;
        }
        final int interval = config.getInt("loot.default-interval-seconds", 900);
        final LootChest chest = new LootChest(world.getUID(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                tableName, interval, 0L);
        chests.put(chest.key(), chest);
        persist(chest);
        return chest;
    }

    /**
     * Unregisters the loot chest at a location, if present.
     *
     * @param location chest block location
     * @return {@code true} if a chest was removed
     */
    public boolean unregister(@NotNull final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final String key = LootChest.keyOf(world.getUID(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        final LootChest removed = chests.remove(key);
        if (removed == null) {
            return false;
        }
        try {
            storage.deleteLootChest(key);
        } catch (final Exception ex) {
            plugin.getLogger().severe("Failed to delete loot chest: " + ex.getMessage());
        }
        return true;
    }

    /**
     * Forces an immediate refill of the chest at a location, if registered.
     *
     * @param location chest block location
     * @return {@code true} if a chest was refilled
     */
    public boolean forceRefill(@NotNull final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final String key = LootChest.keyOf(world.getUID(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        final LootChest chest = chests.get(key);
        if (chest == null) {
            return false;
        }
        final boolean wipe = config.getBoolean("loot.wipe-on-refill", true);
        refill(chest, world, wipe);
        chest.markRefilled(System.currentTimeMillis());
        persist(chest);
        return true;
    }

    /**
     * Whether a location is a registered loot chest.
     *
     * @param worldId world UUID
     * @param x       block X
     * @param y       block Y
     * @param z       block Z
     * @return {@code true} if registered
     */
    public boolean isLootChest(final UUID worldId, final int x, final int y, final int z) {
        return chests.containsKey(LootChest.keyOf(worldId, x, y, z));
    }

    /** @return the known loot table names */
    public @NotNull Collection<String> tableNames() {
        return tables.keySet();
    }

    /** @return number of registered loot chests */
    public int chestCount() {
        return chests.size();
    }

    // ----- Helpers ---------------------------------------------------------

    /**
     * Persists a chest, logging any failure.
     *
     * @param chest chest to save
     */
    private void persist(final LootChest chest) {
        try {
            storage.saveLootChest(chest);
        } catch (final Exception ex) {
            plugin.getLogger().severe("Failed to save loot chest: " + ex.getMessage());
        }
    }

    /**
     * Coerces a config value to an int with a fallback.
     *
     * @param value raw value
     * @param def   default
     * @return parsed int or default
     */
    private int toInt(final Object value, final int def) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (final NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }
}
