package me.warfare.core;

import me.warfare.core.claims.ClaimManager;
import me.warfare.core.commands.CommandManager;
import me.warfare.core.commands.GiveCommand;
import me.warfare.core.commands.ReloadCommand;
import me.warfare.core.config.ConfigManager;
import me.warfare.core.explosions.BlockBreakPolicy;
import me.warfare.core.explosions.ExplosionService;
import me.warfare.core.explosions.TntRegistry;
import me.warfare.core.items.ItemManager;
import me.warfare.core.listeners.ClaimEnvironmentListener;
import me.warfare.core.listeners.ClaimPlacementListener;
import me.warfare.core.listeners.ClaimProtectionListener;
import me.warfare.core.listeners.ExplosionListener;
import me.warfare.core.listeners.LootListener;
import me.warfare.core.loot.LootManager;
import me.warfare.core.commands.LootCommand;
import me.warfare.core.recipes.RecipeManager;
import me.warfare.core.storage.StorageManager;
import me.warfare.core.utils.MessageService;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * WarfareCore plugin entry point.
 *
 * <p>This class is intentionally thin: it owns the plugin lifecycle and wires
 * the major systems together in dependency order, exposing them via accessors.
 * Business logic lives in the dedicated managers, not here. As new phases add
 * systems (storage, items, claims, explosions), each is constructed here in a
 * clear, predictable order.</p>
 */
public final class WarfareCore extends JavaPlugin {

    private ConfigManager configManager;
    private MessageService messageService;
    private StorageManager storageManager;
    private ItemManager itemManager;
    private RecipeManager recipeManager;
    private ClaimManager claimManager;
    private ExplosionService explosionService;
    private TntRegistry tntRegistry;
    private LootManager lootManager;
    private CommandManager commandManager;

    @Override
    public void onEnable() {
        // 1. Configuration must come first; everything else reads from it.
        this.configManager = new ConfigManager(this);

        // 2. Messaging depends only on config.
        this.messageService = new MessageService(configManager);

        // 3. Storage depends on config; brought up before systems that persist.
        this.storageManager = new StorageManager(this);
        this.storageManager.init();

        // 4. Items depend on config + messaging.
        this.itemManager = new ItemManager(this);

        // 5. Recipes depend on items.
        this.recipeManager = new RecipeManager(this);
        this.recipeManager.registerAll();

        // 6. Claims depend on config + storage; load persisted data now.
        this.claimManager = new ClaimManager(this);
        this.claimManager.loadAll();

        // 7. Explosions depend on config + claims (for the break policy).
        final BlockBreakPolicy policy = new BlockBreakPolicy(configManager, claimManager);
        this.explosionService = new ExplosionService(this, policy);
        this.tntRegistry = new TntRegistry(this);

        // 8. Loot depends on config + storage + items; load tables/chests and
        //    start the single refill scheduler.
        this.lootManager = new LootManager(this);
        this.lootManager.start();

        // 9. Listeners (claim placement/protection) depend on the systems above.
        registerListeners();

        // 10. Commands depend on config + messaging.
        this.commandManager = new CommandManager(this, messageService);
        registerCommands();

        getLogger().info("WarfareCore enabled.");
    }

    @Override
    public void onDisable() {
        if (lootManager != null) {
            lootManager.stop();
        }
        if (recipeManager != null) {
            recipeManager.unregisterAll();
        }
        if (storageManager != null) {
            storageManager.shutdown();
        }
        getLogger().info("WarfareCore disabled.");
    }

    /**
     * Registers the root command binding and all subcommands. New subcommands
     * are added here as the plugin grows.
     */
    private void registerCommands() {
        commandManager.register(); // bind to plugin.yml's /warfare
        commandManager.register(new ReloadCommand(this, configManager, messageService));
        commandManager.register(new GiveCommand(this));
        commandManager.register(new LootCommand(this));
    }

    /**
     * Registers all Bukkit event listeners. New listeners are added here as the
     * plugin grows.
     */
    private void registerListeners() {
        final var pm = getServer().getPluginManager();
        pm.registerEvents(new ClaimPlacementListener(this), this);
        pm.registerEvents(new ClaimProtectionListener(this), this);
        pm.registerEvents(new ClaimEnvironmentListener(this), this);
        pm.registerEvents(new ExplosionListener(this, tntRegistry), this);
        pm.registerEvents(new LootListener(this), this);
    }

    // ----- Accessors for other systems -------------------------------------

    /** @return the configuration manager */
    public ConfigManager configManager() {
        return configManager;
    }

    /** @return the message service */
    public MessageService messageService() {
        return messageService;
    }

    /** @return the storage manager */
    public StorageManager storageManager() {
        return storageManager;
    }

    /** @return the item manager */
    public ItemManager itemManager() {
        return itemManager;
    }

    /** @return the recipe manager */
    public RecipeManager recipeManager() {
        return recipeManager;
    }

    /** @return the claim manager */
    public ClaimManager claimManager() {
        return claimManager;
    }

    /** @return the explosion service */
    public ExplosionService explosionService() {
        return explosionService;
    }

    /** @return the TNT registry (tier tracking) */
    public TntRegistry tntRegistry() {
        return tntRegistry;
    }

    /** @return the loot manager */
    public LootManager lootManager() {
        return lootManager;
    }

    /** @return the command manager */
    public CommandManager commandManager() {
        return commandManager;
    }
}
