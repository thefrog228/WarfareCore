package me.warfare.core.storage;

import me.warfare.core.claims.Claim;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Backend-agnostic persistence contract for plugin data.
 *
 * <p>This is the seam that lets the storage backend change (YAML, SQLite, or a
 * remote database) without rewriting any consumer. The claim manager talks only
 * to this interface; it never knows which implementation is behind it.</p>
 *
 * <p>Implementations should be safe to call from the main thread for the small,
 * cached working set typical of this plugin. Heavier backends may perform I/O
 * asynchronously internally, but must keep this contract's semantics.</p>
 */
public interface StorageProvider {

    /**
     * Initialises the backend (open files, create tables/dirs, etc.). Called
     * once during plugin enable, before any other method.
     *
     * @throws Exception if the backend cannot be initialised
     */
    void init() throws Exception;

    /**
     * Loads every persisted claim. Called once at startup to populate the
     * in-memory cache.
     *
     * @return all stored claims; never {@code null}
     * @throws Exception on read failure
     */
    @NotNull Collection<Claim> loadClaims() throws Exception;

    /**
     * Persists a single claim, inserting or updating as needed (keyed by
     * {@link Claim#key()}).
     *
     * @param claim claim to save
     * @throws Exception on write failure
     */
    void saveClaim(@NotNull Claim claim) throws Exception;

    /**
     * Deletes a claim by its unique key.
     *
     * @param key claim key (see {@link Claim#key()})
     * @throws Exception on write failure
     */
    void deleteClaim(@NotNull String key) throws Exception;

    /**
     * Loads every persisted loot chest.
     *
     * @return all stored loot chests; never {@code null}
     * @throws Exception on read failure
     */
    @NotNull Collection<me.warfare.core.loot.LootChest> loadLootChests() throws Exception;

    /**
     * Persists a single loot chest, inserting or updating as needed (keyed by
     * {@link me.warfare.core.loot.LootChest#key()}).
     *
     * @param chest loot chest to save
     * @throws Exception on write failure
     */
    void saveLootChest(@NotNull me.warfare.core.loot.LootChest chest) throws Exception;

    /**
     * Deletes a loot chest by its unique key.
     *
     * @param key loot chest key
     * @throws Exception on write failure
     */
    void deleteLootChest(@NotNull String key) throws Exception;

    /**
     * Flushes any buffered writes and releases resources. Called during plugin
     * disable. Implementations must tolerate being called even if {@link #init()}
     * partially failed.
     *
     * @throws Exception on close failure
     */
    void close() throws Exception;
}
