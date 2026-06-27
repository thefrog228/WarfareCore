package me.warfare.core.storage;

import me.warfare.core.WarfareCore;
import me.warfare.core.storage.sqlite.SqliteStorageProvider;
import me.warfare.core.storage.yaml.YamlStorageProvider;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Owns the active {@link StorageProvider} and its lifecycle.
 *
 * <p>Reads {@code storage.type} from config, constructs the matching provider,
 * and initialises it. If an unimplemented backend (currently SQLite) is
 * selected, it logs a warning and falls back to YAML so the server still starts.
 * Consumers obtain the provider via {@link #provider()} and never construct one
 * themselves.</p>
 */
public final class StorageManager {

    private final WarfareCore plugin;
    private StorageProvider provider;
    private StorageType activeType;

    /**
     * @param plugin owning plugin
     */
    public StorageManager(@NotNull final WarfareCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Selects, constructs, and initialises the configured provider. Falls back
     * to YAML if initialisation of the chosen backend fails.
     */
    public void init() {
        final StorageType configured = StorageType.fromString(
                plugin.configManager().getString("storage.type", "YAML"),
                StorageType.YAML);

        StorageType chosen = configured;
        StorageProvider built = build(configured);

        try {
            built.init();
        } catch (final UnsupportedOperationException unsupported) {
            // Stubbed backend (e.g. SQLite): fall back cleanly to YAML.
            plugin.getLogger().warning(unsupported.getMessage());
            plugin.getLogger().warning("Falling back to YAML storage.");
            chosen = StorageType.YAML;
            built = build(StorageType.YAML);
            initOrThrow(built);
        } catch (final Exception ex) {
            // A real failure of the chosen backend: also fall back to YAML so
            // the server is not left without persistence.
            plugin.getLogger().severe("Failed to initialise " + configured
                    + " storage: " + ex.getMessage());
            plugin.getLogger().warning("Falling back to YAML storage.");
            chosen = StorageType.YAML;
            built = build(StorageType.YAML);
            initOrThrow(built);
        }

        this.provider = built;
        this.activeType = chosen;
        plugin.getLogger().info("Storage backend: " + activeType);
    }

    /**
     * Builds (without initialising) a provider for the given type.
     *
     * @param type backend type
     * @return a new provider instance
     */
    private StorageProvider build(final StorageType type) {
        return switch (type) {
            case SQLITE -> new SqliteStorageProvider();
            case YAML -> new YamlStorageProvider(yamlFolder(), plugin.getLogger());
        };
    }

    /**
     * Initialises a provider, rethrowing any checked failure as unchecked. Used
     * only for the YAML fallback, where failure is unrecoverable.
     *
     * @param p provider to initialise
     */
    private void initOrThrow(final StorageProvider p) {
        try {
            p.init();
        } catch (final Exception ex) {
            throw new IllegalStateException("YAML storage fallback failed", ex);
        }
    }

    /**
     * Resolves the YAML storage folder from config, relative to the plugin's
     * data folder.
     *
     * @return folder for flat-file storage
     */
    private File yamlFolder() {
        final String sub = plugin.configManager().getString("storage.yaml.folder", "data");
        return new File(plugin.getDataFolder(), sub);
    }

    /**
     * Shuts the active provider down, swallowing errors so disable never throws.
     */
    public void shutdown() {
        if (provider == null) {
            return;
        }
        try {
            provider.close();
        } catch (final Exception ex) {
            plugin.getLogger().warning("Error closing storage: " + ex.getMessage());
        }
    }

    /**
     * @return the active provider; never {@code null} after {@link #init()}
     */
    public @NotNull StorageProvider provider() {
        return provider;
    }

    /** @return the backend type actually in use (may differ from config on fallback) */
    public StorageType activeType() {
        return activeType;
    }
}
