package dev.lifeclock.listener;

import dev.lifeclock.LifeClockPlugin;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Re-applies Ghost state (or a pending offline-revive) on join/respawn. */
public final class JoinListener implements Listener {

    private final LifeClockPlugin plugin;

    public JoinListener(LifeClockPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        var p = e.getPlayer();
        var id = p.getUniqueId();
        if (plugin.store().isGhost(id)) {
            p.setGameMode(GameMode.SPECTATOR);
            plugin.send(p, "ghost-reconnect");
        } else if (plugin.store().isPendingSurvival(id)) { // revived while offline; their save still says SPECTATOR
            p.setGameMode(GameMode.SURVIVAL);
            plugin.store().setPendingSurvival(id, false);
            plugin.store().save();
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (plugin.store().isGhost(e.getPlayer().getUniqueId())) {
            e.getPlayer().setGameMode(GameMode.SPECTATOR);
        }
    }
}
