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
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chest GUI used by the Revive Totem: pick a ghost's head, or use the compass to
 * open the vanilla search dialog (text field + Revive/Cancel). Costs 1 totem +
 * the sacrifice at confirm time.
 */
public final class ReviveMenu implements Listener {

    private static final int SIZE = 54;
    private static final int SEARCH_SLOT = 49;
    private static final int MAX_HEADS = SEARCH_SLOT; // heads take slots 0..48

    private final LifeClockPlugin plugin;

    public ReviveMenu(LifeClockPlugin plugin) { this.plugin = plugin; }

    /** Per-open state so clicks identify themselves without title sniffing. */
    private static final class Menu implements InventoryHolder {
        final UUID opener;
        final Map<Integer, UUID> heads = new ConcurrentHashMap<>();
        boolean used;
        private Inventory inventory;

        Menu(UUID opener) { this.opener = opener; }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    public void open(Player p) {
        long cost = plugin.getConfig().getLong("sacrifice-cost", 18000L);
        if (plugin.store().seconds(p.getUniqueId()) < cost) {
            plugin.send(p, "revive-insufficient");
            return;
        }

        Menu menu = new Menu(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(menu, SIZE,
                plugin.mm(plugin.getConfig().getString("messages.gui.title",
                        "<bold><dark_purple>Select Ghost to Revive</dark_purple></bold>")));
        menu.inventory = inv;

        int slot = 0;
        for (UUID ghostId : plugin.store().ghosts()) {
            if (slot >= MAX_HEADS) break; // shortcut: >48 ghosts need pagination; the search box still works
            OfflinePlayer t = Bukkit.getOfflinePlayer(ghostId);
            if (t.getName() == null) continue;
            inv.setItem(slot, head(t));
            menu.heads.put(slot, ghostId);
            slot++;
        }
        inv.setItem(SEARCH_SLOT, searchItem());

        p.openInventory(inv);
    }

    private ItemStack head(OfflinePlayer t) {
        ItemStack skull = ItemStack.of(Material.PLAYER_HEAD);
        skull.editMeta(SkullMeta.class, m -> {
            Player onlineOwner = t.getPlayer();
            if (onlineOwner != null) m.setOwningPlayer(onlineOwner);
            else m.setPlayerProfile(Bukkit.createProfile(t.getUniqueId()));
            m.displayName(plugin.mm(plugin.getConfig().getString("messages.gui.head-name",
                    "<gold><bold>%player%</bold></gold>").replace("%player%", t.getName())));
            List<String> lore = plugin.getConfig().getStringList("messages.gui.head-lore");
            if (lore.isEmpty()) lore = List.of(
                    "<gray>Status: Dead / Spectator</gray>",
                    "<yellow>Click to revive for 5 Hours</yellow>");
            String cost = Times.format(plugin.getConfig().getLong("sacrifice-cost", 18000L));
            m.lore(lore.stream().map(l -> plugin.mm(l.replace("%time%", cost))).toList());
        });
        return skull;
    }

    private ItemStack searchItem() {
        ItemStack compass = ItemStack.of(Material.COMPASS);
        compass.editMeta(m -> m.displayName(plugin.mm(plugin.getConfig().getString(
                "messages.gui.search-name", "<cyan><bold>Search Player by Name</bold></cyan>"))));
        return compass;
    }

    // ---------- events ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!p.getUniqueId().equals(menu.opener)) {
            e.setCancelled(true);
            return;
        }

        if (e.getClickedInventory() == e.getInventory()) { // clicked inside our menu
            e.setCancelled(true);
            if (!e.getClick().isLeftClick()) return;
            int slot = e.getSlot();
            if (slot < 0 || slot >= SIZE) return;

            if (slot == SEARCH_SLOT) {
                menuSearch(p); // compass → vanilla dialog with a text field (the search bar)
                return;
            }

            UUID targetId = menu.heads.get(slot);
            if (targetId == null || menu.used) return;
            menu.used = true; // one charge per open
            p.closeInventory();
            chargeAndRevive(p, targetId);
            return;
        }

        // No item insertion: block shift-clicks from the player inventory into our menu.
        if (e.getClick().isShiftClick()) e.setCancelled(true);
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

    /** Opens the vanilla dialog screen: a real text field plus Revive/Cancel buttons. */
    private void menuSearch(Player p) {
        Component title = plugin.mm(plugin.getConfig().getString("messages.gui.search-title",
                "<gold><bold>Search Ghost to Revive</bold></gold>"));
        Component label = plugin.mm(plugin.getConfig().getString("messages.gui.search-label",
                "<gray>Type the ghost's exact name:</gray>"));

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .inputs(List.of(DialogInput.text("ghost", label).width(250).maxLength(32).build()))
                        .build())
                .type(DialogType.confirmation(
                        // Submit: carries the typed name back into the revive path.
                        ActionButton.builder(plugin.mm("<gold><bold>Revive</bold></gold>"))
                                .action(DialogAction.customClick((response, audience) -> {
                                    String name = response.getText("ghost");
                                    if (name == null || name.isBlank()) return;
                                    UUID searcherId = p.getUniqueId();
                                    // Resurrection/inventory flow must run on the main thread.
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        Player searcher = Bukkit.getPlayer(searcherId);
                                        if (searcher == null || !searcher.isOnline()) return;
                                        UUID targetId = resolveGhost(name.trim());
                                        if (targetId == null) {
                                            plugin.send(searcher, "not-ghost", "%player%", name.trim());
                                            return;
                                        }
                                        chargeAndRevive(searcher, targetId);
                                    });
                                }, ClickCallback.Options.builder().build()))
                                .build(),
                        // Cancel: plain close, no action.
                        ActionButton.builder(plugin.mm("<red>Cancel</red>")).build())));

        p.showDialog(dialog);
    }

    // ---------- shared revive path ----------

    private UUID resolveGhost(String name) {
        for (UUID id : plugin.store().ghosts()) {
            OfflinePlayer t = Bukkit.getOfflinePlayer(id);
            if (t.getName() != null && t.getName().equalsIgnoreCase(name)) return id;
        }
        return null;
    }

    private void chargeAndRevive(Player p, UUID targetId) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        String targetName = target != null && target.getName() != null ? target.getName() : targetId.toString();

        if (!plugin.store().isGhost(targetId)) {
            plugin.send(p, "not-ghost", "%player%", targetName);
            return;
        }

        long cost = plugin.getConfig().getLong("sacrifice-cost", 18000L);
        if (plugin.store().seconds(p.getUniqueId()) < cost) {
            plugin.send(p, "revive-insufficient");
            return;
        }

        if (!consumeReviveItem(p)) {
            plugin.send(p, "totem-lost");
            return;
        }

        long left = plugin.store().add(p.getUniqueId(), -cost);
        plugin.send(p, "revive-charge", "%time%", Times.format(cost), "%total%", Times.format(left));
        plugin.revive(target, p.getName());
    }

    /** Removes 1 Revive Totem from wherever the player carries it. */
    boolean consumeReviveItem(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && plugin.items().is(it, plugin.items().reviveKey())) {
                if (it.getAmount() == 1) p.getInventory().setItem(i, null);
                else it.setAmount(it.getAmount() - 1);
                return true;
            }
        }
        return false;
    }
}
