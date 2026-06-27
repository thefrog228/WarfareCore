package me.warfare.core.storage;

import java.util.Locale;

/**
 * Supported persistence backends, selected via {@code storage.type} in config.
 *
 * <p>{@link #YAML} is fully implemented. {@link #SQLITE} is stubbed behind the
 * same {@link StorageProvider} interface and can be completed without touching
 * any consumer of the storage layer.</p>
 */
public enum StorageType {

    /** Flat-file storage in a single {@code claims.yml}. */
    YAML,

    /** SQLite database storage (planned; currently stubbed). */
    SQLITE;

    /**
     * Parses a storage type case-insensitively.
     *
     * @param raw input text; may be {@code null}
     * @param def fallback when {@code raw} is null or unrecognised
     * @return matching type, or {@code def}
     */
    public static StorageType fromString(final String raw, final StorageType def) {
        if (raw == null) {
            return def;
        }
        try {
            return StorageType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ex) {
            return def;
        }
    }
}
