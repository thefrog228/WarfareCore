package me.warfare.core.storage.yaml;

import me.warfare.core.claims.Claim;
import me.warfare.core.claims.ClaimType;
import me.warfare.core.storage.StorageProvider;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Flat-file {@link StorageProvider} backed by a single {@code claims.yml}.
 *
 * <p>Layout (one section per claim, keyed by {@link Claim#key()}):</p>
 * <pre>
 * claims:
 *   "&lt;worldId&gt;;&lt;x&gt;;&lt;y&gt;;&lt;z&gt;":
 *     owner: &lt;uuid&gt;
 *     type: NORMAL
 *     world: &lt;uuid&gt;
 *     x: 100
 *     y: 64
 *     z: -200
 *     radius: 16
 *     meta:
 *       somekey: somevalue
 * </pre>
 *
 * <p>Suited to the modest claim counts expected on an anarchy server. For very
 * large datasets, switch {@code storage.type} to SQLITE — no consumer code
 * changes because both sit behind {@link StorageProvider}.</p>
 */
public final class YamlStorageProvider implements StorageProvider {

    private static final String ROOT = "claims";
    private static final String LOOT_ROOT = "chests";

    private final File file;
    private final File lootFile;
    private final Logger logger;
    private YamlConfiguration yaml;
    private YamlConfiguration lootYaml;

    /**
     * @param dataFolder folder to place storage files in (created if absent)
     * @param logger     plugin logger for diagnostics
     */
    public YamlStorageProvider(@NotNull final File dataFolder, @NotNull final Logger logger) {
        this.file = new File(dataFolder, "claims.yml");
        this.lootFile = new File(dataFolder, "loot-chests.yml");
        this.logger = logger;
    }

    @Override
    public void init() throws IOException {
        final File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create storage folder: " + parent);
        }
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Could not create claims file: " + file);
        }
        this.yaml = YamlConfiguration.loadConfiguration(file);

        if (!lootFile.exists() && !lootFile.createNewFile()) {
            throw new IOException("Could not create loot file: " + lootFile);
        }
        this.lootYaml = YamlConfiguration.loadConfiguration(lootFile);
    }

    @Override
    public @NotNull Collection<Claim> loadClaims() {
        final Collection<Claim> result = new ArrayList<>();
        final ConfigurationSection root = yaml.getConfigurationSection(ROOT);
        if (root == null) {
            return result;
        }
        for (final String key : root.getKeys(false)) {
            final ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            final Claim claim = read(sec);
            if (claim != null) {
                result.add(claim);
            } else {
                logger.warning("Skipping malformed claim entry: " + key);
            }
        }
        return result;
    }

    @Override
    public void saveClaim(@NotNull final Claim claim) throws IOException {
        final String path = ROOT + "." + claim.key();
        yaml.set(path + ".owner", claim.owner().toString());
        yaml.set(path + ".type", claim.type().name());
        yaml.set(path + ".world", claim.worldId().toString());
        yaml.set(path + ".x", claim.x());
        yaml.set(path + ".y", claim.y());
        yaml.set(path + ".z", claim.z());
        yaml.set(path + ".radius", claim.radius());

        // Persist metadata only when present, to keep the file tidy.
        if (!claim.metadata().isEmpty()) {
            for (final Map.Entry<String, String> e : claim.metadata().entrySet()) {
                yaml.set(path + ".meta." + e.getKey(), e.getValue());
            }
        }
        yaml.save(file);
    }

    @Override
    public void deleteClaim(@NotNull final String key) throws IOException {
        yaml.set(ROOT + "." + key, null);
        yaml.save(file);
    }

    @Override
    public void close() {
        // Nothing buffered: every save() writes through immediately. Method
        // retained to satisfy the contract and for symmetry with DB backends.
    }

    @Override
    public @NotNull Collection<me.warfare.core.loot.LootChest> loadLootChests() {
        final Collection<me.warfare.core.loot.LootChest> result = new ArrayList<>();
        final ConfigurationSection root = lootYaml.getConfigurationSection(LOOT_ROOT);
        if (root == null) {
            return result;
        }
        for (final String key : root.getKeys(false)) {
            final ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            final me.warfare.core.loot.LootChest chest = readChest(sec);
            if (chest != null) {
                result.add(chest);
            } else {
                logger.warning("Skipping malformed loot chest entry: " + key);
            }
        }
        return result;
    }

    @Override
    public void saveLootChest(@NotNull final me.warfare.core.loot.LootChest chest)
            throws IOException {
        final String path = LOOT_ROOT + "." + chest.key();
        lootYaml.set(path + ".world", chest.worldId().toString());
        lootYaml.set(path + ".x", chest.x());
        lootYaml.set(path + ".y", chest.y());
        lootYaml.set(path + ".z", chest.z());
        lootYaml.set(path + ".table", chest.tableName());
        lootYaml.set(path + ".interval", chest.intervalSeconds());
        lootYaml.set(path + ".last-refill", chest.lastRefillMillis());
        lootYaml.save(lootFile);
    }

    @Override
    public void deleteLootChest(@NotNull final String key) throws IOException {
        lootYaml.set(LOOT_ROOT + "." + key, null);
        lootYaml.save(lootFile);
    }

    /**
     * Deserialises one loot chest section. Returns {@code null} on malformed
     * required fields so the caller can skip it.
     *
     * @param sec configuration section for a single loot chest
     * @return parsed loot chest, or {@code null} if invalid
     */
    private me.warfare.core.loot.LootChest readChest(final ConfigurationSection sec) {
        try {
            final UUID world = UUID.fromString(sec.getString("world", ""));
            final int x = sec.getInt("x");
            final int y = sec.getInt("y");
            final int z = sec.getInt("z");
            final String table = sec.getString("table");
            if (table == null) {
                return null;
            }
            final int interval = sec.getInt("interval", 900);
            final long last = sec.getLong("last-refill", 0L);
            return new me.warfare.core.loot.LootChest(
                    world, x, y, z, table, interval, last);
        } catch (final IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Deserialises one claim section. Returns {@code null} on any missing or
     * malformed required field so the caller can skip it gracefully.
     *
     * @param sec configuration section for a single claim
     * @return the parsed claim, or {@code null} if invalid
     */
    private Claim read(final ConfigurationSection sec) {
        try {
            final UUID owner = UUID.fromString(sec.getString("owner", ""));
            final ClaimType type = ClaimType.fromString(sec.getString("type"), ClaimType.NORMAL);
            final UUID world = UUID.fromString(sec.getString("world", ""));
            final int x = sec.getInt("x");
            final int y = sec.getInt("y");
            final int z = sec.getInt("z");
            final int radius = sec.getInt("radius");

            final java.util.Map<String, String> meta = new java.util.HashMap<>();
            final ConfigurationSection metaSec = sec.getConfigurationSection("meta");
            if (metaSec != null) {
                for (final String mk : metaSec.getKeys(false)) {
                    meta.put(mk, metaSec.getString(mk));
                }
            }
            return new Claim(owner, type, world, x, y, z, radius, meta);
        } catch (final IllegalArgumentException ex) {
            // Covers UUID parse failures and similar.
            return null;
        }
    }
}
