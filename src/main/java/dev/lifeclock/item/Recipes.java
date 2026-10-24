package dev.lifeclock.item;

import dev.lifeclock.LifeClockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

public final class Recipes {

    private final LifeClockPlugin plugin;

    public Recipes(LifeClockPlugin plugin) { this.plugin = plugin; }

    public void register() {
        // Time Clock: a plain clock wrapped in gold (8 ingots).
        var clockId = new NamespacedKey(plugin, "time_clock_recipe");
        var clock = new ShapedRecipe(clockId, plugin.items().timeClock(plugin.items().clockValue()));
        clock.shape("GGG", "GCG", "GGG");
        clock.setIngredient('G', Material.GOLD_INGOT);
        clock.setIngredient('C', new RecipeChoice.ExactChoice(new ItemStack(Material.CLOCK)));
        Bukkit.addRecipe(clock);

        // Revive Totem: totem + golden apple + diamond + blaze rod (shapeless).
        var reviveId = new NamespacedKey(plugin, "revive_item_recipe");
        var revive = new ShapelessRecipe(reviveId, plugin.items().reviveItem());
        revive.addIngredient(Material.TOTEM_OF_UNDYING);
        revive.addIngredient(Material.GOLDEN_APPLE);
        revive.addIngredient(Material.DIAMOND);
        revive.addIngredient(Material.BLAZE_ROD);
        Bukkit.addRecipe(revive);
    }
}
