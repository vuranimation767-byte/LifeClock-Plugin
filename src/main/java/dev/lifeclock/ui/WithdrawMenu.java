package dev.lifeclock.ui;

import dev.lifeclock.LifeClockPlugin;
import dev.lifeclock.Times;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Withdraw UI: chest with preset-amount buttons + a custom-amount dialog button.
 * /withdraw (no args) opens this; /withdraw <time> skips straight to attempt().
 * All paths share attempt(): floor rule — the clock must stay above zero.
 */
public final class WithdrawMenu implements Listener {

    private static final int SIZE = 27;
    private static final int CUSTOM_SLOT = 22;

    private final LifeClockPlugin plugin;

    public WithdrawMenu(LifeClockPlugin plugin) { this.plugin = plugin; }

    /** Per-open state so clicks identify themselves without title sniffing. */
    private static final class Menu implements InventoryHolder {
        final UUID opener;
        final Map<Integer, Long> presets = new ConcurrentHashMap<>();
        private Inventory inventory;

        Menu(UUID opener) { this.opener = opener; }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    public void open(Player p) {
        Menu menu = new Menu(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(menu, SIZE,
                plugin.mm(plugin.getConfig().getString("withdraw.gui-title",
                        "<bold><dark_purple>Withdraw Time</dark_purple></bold>")));
        menu.inventory = inv;

        int slot = 11;
        for (long seconds : plugin.getConfig().getIntegerList("withdraw.presets")) {
            if (seconds <= 0 || slot > 17) break; // presets fill the middle row (11..17)
            inv.setItem(slot, presetItem(seconds));
            menu.presets.put(slot, seconds);
            slot++;
        }
        inv.setItem(CUSTOM_SLOT, customItem());

        p.openInventory(inv);
    }

    private ItemStack presetItem(long seconds) {
        ItemStack item = ItemStack.of(Material.CLOCK);
        item.editMeta(m -> {
            m.displayName(plugin.mm("<gold><bold>Withdraw " + Times.format(seconds) + "</bold></gold>"));
            m.lore(List.of(
                    plugin.mm("<gray>Turns " + Times.format(seconds) + " of your clock</gray>"),
                    plugin.mm("<gray>into a Time Clock item.</gray>")));
        });
        return item;
    }

    private ItemStack customItem() {
        ItemStack item = ItemStack.of(Material.NAME_TAG);
        item.editMeta(m -> {
            m.displayName(plugin.mm("<aqua><bold>Custom Amount…</bold></aqua>"));
            m.lore(List.of(plugin.mm("<gray>Type any time, e.g.</gray> <gold>2h30m</gold>")));
        });
        return item;
    }

    // ---------- events ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true); // the box is buttons-only
        if (!p.getUniqueId().equals(menu.opener) || !e.getClick().isLeftClick()) return;
        int slot = e.getSlot();
        if (e.getClickedInventory() != e.getInventory()) return;

        if (slot == CUSTOM_SLOT) {
            openCustom(p);
            return;
        }
        Long seconds = menu.presets.get(slot);
        if (seconds != null) attempt(p, seconds);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu)) return;
        int topSize = e.getInventory().getSize();
        for (int slot : e.getRawSlots()) {
            if (slot < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // ---------- custom-amount dialog ----------

    private void openCustom(Player p) {
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(plugin.mm(plugin.getConfig().getString("withdraw.dialog-title",
                                "<gold><bold>Withdraw a Custom Amount</bold></gold>")))
                        .inputs(List.of(DialogInput.text("amount", plugin.mm(plugin.getConfig().getString(
                                        "withdraw.dialog-label", "<gray>Type the time, e.g.:</gray> <gold>2h30m</gold>")))
                                .width(250).maxLength(16).build()))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(plugin.mm("<gold><bold>Withdraw</bold></gold>"))
                                .action(DialogAction.customClick((response, audience) -> {
                                    String raw = response.getText("amount");
                                    if (raw == null || raw.isBlank()) return;
                                    UUID openerId = p.getUniqueId();
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        Player player = Bukkit.getPlayer(openerId);
                                        if (player == null || !player.isOnline()) return;
                                        long seconds = Times.parse(raw.trim());
                                        if (seconds <= 0) {
                                            plugin.send(player, "invalid-time", "%input%", raw.trim());
                                            return;
                                        }
                                        attempt(player, seconds);
                                    });
                                }, ClickCallback.Options.builder().build()))
                                .build(),
                        ActionButton.builder(plugin.mm("<red>Cancel</red>")).build())));
        p.closeInventory();
        p.showDialog(dialog);
    }

    // ---------- shared withdraw path ----------

    /** Deducts the amount and hands back a Time Clock item stamped with it. Floor: clock stays > 0. */
    public void attempt(Player p, long seconds) {
        if (seconds <= 0) return;

        long current = plugin.store().seconds(p.getUniqueId());
        if (current <= seconds) {
            plugin.send(p, "withdraw-fail", "%time%", Times.format(seconds), "%total%", Times.format(current));
            return;
        }

        long left = plugin.store().add(p.getUniqueId(), -seconds);
        if (left <= 0) { // a drain tick slipped between check and deduct — put it back
            plugin.store().add(p.getUniqueId(), seconds);
            plugin.send(p, "withdraw-fail", "%time%", Times.format(seconds), "%total%", Times.format(plugin.store().seconds(p.getUniqueId())));
            return;
        }

        ItemStack clock = plugin.items().timeClock(seconds);
        clock.editMeta(m -> m.lore(List.of(
                plugin.mm("<gold>+" + Times.format(seconds) + " on the clock</gold>"),
                plugin.mm("<dark_gray>Taken, not earned.</dark_gray>"))));

        var overflow = p.getInventory().addItem(clock);
        for (ItemStack rest : overflow.values()) p.getWorld().dropItem(p.getLocation(), rest);

        plugin.send(p, "withdraw-done", "%time%", Times.format(seconds), "%total%", Times.format(left));
        plugin.store().save();
    }
}
