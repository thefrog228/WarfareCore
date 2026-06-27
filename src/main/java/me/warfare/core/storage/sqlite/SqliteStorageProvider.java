package me.warfare.core.storage.sqlite;

import me.warfare.core.claims.Claim;
import me.warfare.core.storage.StorageProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Placeholder SQLite {@link StorageProvider}.
 *
 * <p>This stub exists so the storage abstraction is complete and selectable in
 * config from day one. It is intentionally <em>not</em> wired as a usable
 * backend yet: {@link me.warfare.core.storage.StorageManager} guards against
 * selecting it and falls back to YAML with a warning. When implemented, only the
 * bodies below change — no consumer of {@link StorageProvider} is affected.</p>
 *
 * <p>Planned implementation: a bundled JDBC SQLite driver (shaded via the
 * existing Shadow setup), a {@code claims} table keyed by claim key, and
 * prepared statements for save/delete.</p>
 */
public final class SqliteStorageProvider implements StorageProvider {

    /** Shared message for all unimplemented operations. */
    private static final String NOT_IMPLEMENTED =
            "SQLite storage is not implemented yet. Use storage.type: YAML.";

    @Override
    public void init() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public @NotNull Collection<Claim> loadClaims() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void saveClaim(@NotNull final Claim claim) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void deleteClaim(@NotNull final String key) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public @NotNull Collection<me.warfare.core.loot.LootChest> loadLootChests() {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void saveLootChest(@NotNull final me.warfare.core.loot.LootChest chest) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void deleteLootChest(@NotNull final String key) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void close() {
        // Nothing to close in the stub.
    }
}
