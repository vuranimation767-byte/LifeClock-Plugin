package dev.lifeclock.item;

import dev.lifeclock.LifeClockPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.logging.Level;

/** Builds the two custom items. Their identity lives in the PersistentDataContainer. */
public final class Items {

    private final LifeClockPlugin plugin;
    private final NamespacedKey clockKey;
    private final NamespacedKey reviveKey;

    public Items(LifeClockPlugin plugin) {
        this.plugin = plugin;
        this.clockKey = new NamespacedKey(plugin, "time_clock");
        this.reviveKey = new NamespacedKey(plugin, "revive_item");
    }

    public NamespacedKey clockKey() { return clockKey; }
    public NamespacedKey reviveKey() { return reviveKey; }

    public ItemStack timeClock(long seconds) {
        return custom("time-clock", clockKey, seconds, Material.CLOCK);
    }

    public long clockValue() { return plugin.getConfig().getLong("clock-value", 7200L); }

    public ItemStack reviveItem() {
        return custom("revive-item", reviveKey, 1L, Material.TOTEM_OF_UNDYING);
    }

    /** True when this stack carries the given LifeClock PDC key. */
    public boolean is(ItemStack item, NamespacedKey key) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.LONG);
    }

    /** Seconds stored on a Time Clock item (its PDC value, config as fallback). */
    public long clockSeconds(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return clockValue();
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(clockKey, PersistentDataType.LONG, clockValue());
    }

    private ItemStack custom(String path, NamespacedKey key, long value, Material fallback) {
        Material mat = fallback;
        String matName = plugin.getConfig().getString("items." + path + ".material");
        if (matName != null) {
            try {
                mat = Material.valueOf(matName.toUpperCase().replace(' ', '_'));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid material '" + matName + "' at items." + path
                        + ".material, using " + fallback.name());
            }
        }
        ItemStack item = ItemStack.of(mat);
        item.editMeta(m -> {
            String name = plugin.getConfig().getString("items." + path + ".name");
            if (name != null && !name.isBlank()) m.displayName(plugin.mm(name));
            List<Component> lore = plugin.getConfig().getStringList("items." + path + ".lore")
                    .stream().map(plugin::mm).toList();
            if (!lore.isEmpty()) m.lore(lore);
            m.getPersistentDataContainer().set(key, PersistentDataType.LONG, value);
        });
        return item;
    }
}
