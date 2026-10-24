package dev.lifeclock.listener;

import dev.lifeclock.LifeClockPlugin;
import dev.lifeclock.Times;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * PvP deaths: victim pays the configured penalty, a Time Clock ("loot") drops
 * at the death spot. Anyone who picks it up can consume it for the time.
 */
public final class DeathListener implements Listener {

    private final LifeClockPlugin plugin;

    public DeathListener(LifeClockPlugin plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        long steal = plugin.getConfig().getLong("pvp-steal", 7200L);
        long left = plugin.store().add(victim.getUniqueId(), -steal);

        victim.getWorld().dropItemNaturally(victim.getLocation(), plugin.items().timeClock(steal));

        if (left <= 0 && !plugin.store().isGhost(victim.getUniqueId())) {
            plugin.eliminate(victim); // player is dead → gamemode switches on respawn
        } else {
            plugin.broadcast("pvp-kill", "%victim%", victim.getName(), "%killer%", killer.getName(),
                    "%time%", Times.format(steal));
        }
    }
}
