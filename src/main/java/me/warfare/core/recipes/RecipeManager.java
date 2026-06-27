package me.warfare.core.recipes;

import me.warfare.core.WarfareCore;
import me.warfare.core.config.ConfigManager;
import me.warfare.core.items.CustomItemType;
import me.warfare.core.items.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers and removes the plugin's crafting recipes.
 *
 * <p>Recipes are registered on enable and removed on disable (and on reload) so
 * the server never throws a duplicate-key error. Each recipe can be toggled in
 * config under {@code recipes.&lt;name&gt;.enabled}.</p>
 *
 * <p>Recipes that output custom items use {@link RecipeChoice.ExactChoice} for
 * custom ingredients so that, for example, Reinforced TNT requires actual Packed
 * TNT (PDC-tagged), not just any vanilla TNT. Vanilla ingredients use a plain
 * material choice.</p>
 */
public final class RecipeManager {

    private final WarfareCore plugin;
    private final ConfigManager config;
    private final ItemManager items;

    /** Keys of recipes this manager has registered, for clean removal. */
    private final List<NamespacedKey> registered = new ArrayList<>();

    /**
     * @param plugin owning plugin
     */
    public RecipeManager(@NotNull final WarfareCore plugin) {
        this.plugin = plugin;
        this.config = plugin.configManager();
        this.items = plugin.itemManager();
    }

    /**
     * Registers all enabled recipes. Safe to call again after {@link #unregisterAll()}.
     */
    public void registerAll() {
        registerPackedTnt();
        registerReinforcedTnt();
        plugin.getLogger().info("Registered " + registered.size() + " custom recipe(s).");
    }

    /**
     * Removes every recipe this manager registered. Called on disable/reload to
     * prevent duplicate-key errors on re-registration.
     */
    public void unregisterAll() {
        for (final NamespacedKey key : registered) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();
    }

    /**
     * Packed TNT: nine vanilla TNT filling a 3x3 grid.
     */
    private void registerPackedTnt() {
        if (!config.getBoolean("recipes.packed_tnt.enabled", true)) {
            return;
        }
        final NamespacedKey key = new NamespacedKey(plugin, "packed_tnt");
        final ItemStack result = items.create(CustomItemType.PACKED_TNT);
        final ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("TTT", "TTT", "TTT");
        recipe.setIngredient('T', Material.TNT);
        register(key, recipe);
    }

    /**
     * Reinforced TNT: a configurable shaped recipe whose corners/edges are
     * Packed TNT around a central core. Default shape surrounds an obsidian core
     * with Packed TNT; both the core material and whether the recipe is enabled
     * are configurable.
     */
    private void registerReinforcedTnt() {
        if (!config.getBoolean("recipes.reinforced_tnt.enabled", true)) {
            return;
        }
        final NamespacedKey key = new NamespacedKey(plugin, "reinforced_tnt");
        final ItemStack result = items.create(CustomItemType.REINFORCED_TNT);
        final ShapedRecipe recipe = new ShapedRecipe(key, result);

        // Core material is configurable; defaults to obsidian.
        final String coreName = config.getString(
                "recipes.reinforced_tnt.core-material", "OBSIDIAN");
        Material core = Material.matchMaterial(coreName);
        if (core == null) {
            plugin.getLogger().warning("Invalid reinforced_tnt core-material '"
                    + coreName + "', defaulting to OBSIDIAN.");
            core = Material.OBSIDIAN;
        }

        recipe.shape("PPP", "PCP", "PPP");
        // 'P' must be actual Packed TNT (custom), not vanilla TNT.
        recipe.setIngredient('P',
                new RecipeChoice.ExactChoice(items.create(CustomItemType.PACKED_TNT)));
        recipe.setIngredient('C', core);
        register(key, recipe);
    }

    /**
     * Adds a recipe to the server and records its key for later removal.
     *
     * @param key    recipe key
     * @param recipe recipe to add
     */
    private void register(final NamespacedKey key, final ShapedRecipe recipe) {
        // Remove any stale copy first (e.g. left over from a crash) to be safe.
        Bukkit.removeRecipe(key);
        Bukkit.addRecipe(recipe);
        registered.add(key);
    }
}
