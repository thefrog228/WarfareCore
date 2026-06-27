package me.warfare.core.config;

import me.warfare.core.WarfareCore;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Central access point for {@code config.yml}.
 *
 * <p>Every other system reads configuration through this class rather than
 * touching {@link org.bukkit.plugin.Plugin#getConfig()} directly. Centralising
 * access keeps key names in one place, makes reloads atomic, and gives us a
 * single spot to add validation or migration logic later.</p>
 */
public final class ConfigManager {

    private final WarfareCore plugin;
    private FileConfiguration config;

    /**
     * Creates the manager and performs the initial load.
     *
     * @param plugin owning plugin instance
     */
    public ConfigManager(final WarfareCore plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * (Re)loads {@code config.yml} from disk, writing the bundled default if
     * the file does not yet exist. Safe to call at runtime (e.g. from
     * {@code /warfare reload}).
     */
    public void reload() {
        // saveDefaultConfig() only writes if the file is absent, so it is safe
        // to call on every reload.
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    /**
     * @return the backing {@link FileConfiguration}; never {@code null} after
     *         construction
     */
    public FileConfiguration raw() {
        return config;
    }

    // ----- Convenience accessors -------------------------------------------

    /**
     * Reads a string with a fallback, guarding against a missing key.
     *
     * @param path config path
     * @param def  default returned if the key is absent
     * @return configured value, or {@code def} if not present
     */
    public String getString(final String path, final String def) {
        return config.getString(path, def);
    }

    /**
     * Reads an int with a fallback.
     *
     * @param path config path
     * @param def  default returned if the key is absent
     * @return configured value, or {@code def} if not present
     */
    public int getInt(final String path, final int def) {
        return config.getInt(path, def);
    }

    /**
     * Reads a boolean with a fallback.
     *
     * @param path config path
     * @param def  default returned if the key is absent
     * @return configured value, or {@code def} if not present
     */
    public boolean getBoolean(final String path, final boolean def) {
        return config.getBoolean(path, def);
    }
}
