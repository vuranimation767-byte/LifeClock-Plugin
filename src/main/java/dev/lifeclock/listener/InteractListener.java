package dev.lifeclock.listener;

import dev.lifeclock.LifeClockPlugin;
import dev.lifeclock.Times;
import dev.lifeclock.item.Items;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Right-click handling: Time Clock consumes, Revive Totem opens the selection GUI (main hand only). */
public final class InteractListener implements Listener {

    private final LifeClockPlugin plugin;

    public InteractListener(LifeClockPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return; // off-hand duplicates guarded by "main hand only"
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var item = e.getItem();
        if (item == null) return;

        Items items = plugin.items();
        if (items.is(item, items.clockKey())) {
            consumeClock(e);
        } else if (items.is(item, items.reviveKey())) {
            e.setCancelled(true);
            plugin.reviveMenu().open(e.getPlayer());
        }
    }

    private void consumeClock(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        e.setCancelled(true);

        long value = plugin.items().clockSeconds(e.getItem());
        e.getItem().setAmount(e.getItem().getAmount() - 1); // removes the stack at amount 1

        long total = plugin.store().add(p.getUniqueId(), value);
        plugin.send(p, "gained-time", "%time%", Times.format(value), "%total%", Times.format(total));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.25);
        plugin.store().save();
    }
}
