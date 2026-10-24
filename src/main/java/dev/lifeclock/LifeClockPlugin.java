package dev.lifeclock;

import dev.lifeclock.Times;
import dev.lifeclock.command.LifeClockCommand;
import dev.lifeclock.command.WithdrawCommand;
import dev.lifeclock.data.TimeStore;
import dev.lifeclock.hud.SidebarHud;
import dev.lifeclock.item.Items;
import dev.lifeclock.item.Recipes;
import dev.lifeclock.listener.DeathListener;
import dev.lifeclock.listener.InteractListener;
import dev.lifeclock.listener.JoinListener;
import dev.lifeclock.ui.ReviveMenu;
import dev.lifeclock.ui.WithdrawMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class LifeClockPlugin extends JavaPlugin {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private TimeStore store;
    private Items items;
    private ReviveMenu reviveMenu;
    private WithdrawMenu withdrawMenu;
    private SidebarHud hud;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        store = new TimeStore(this);
        store.load();
        items = new Items(this);

        new Recipes(this).register();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new JoinListener(this), this);
        pm.registerEvents(new DeathListener(this), this);
        pm.registerEvents(new InteractListener(this), this);
        reviveMenu = new ReviveMenu(this);
        pm.registerEvents(reviveMenu, this);
        withdrawMenu = new WithdrawMenu(this);
        pm.registerEvents(withdrawMenu, this);

        PluginCommand cmd = getCommand("lifeclock");
        if (cmd != null) {
            var exec = new LifeClockCommand(this);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }

        PluginCommand wd = getCommand("withdraw");
        if (wd != null) wd.setExecutor(new WithdrawCommand(this));

        hud = new SidebarHud(this);
        pm.registerEvents(hud, this); // quit cleanup for the per-player boards
        hud.start();
    }

    @Override
    public void onDisable() {
        if (hud != null) hud.shutdown();
        if (store != null) store.save();
    }

    public TimeStore store() { return store; }
    public Items items() { return items; }
    public ReviveMenu reviveMenu() { return reviveMenu; }
    public WithdrawMenu withdrawMenu() { return withdrawMenu; }

    public long startSeconds() { return getConfig().getLong("start-time", 86400L); }

    public Component mm(String raw) { return mm.deserialize(raw); }

    /** Reads messages.<path>, applies %key% → value pairs, prepends the prefix. */
    public Component msg(String path, String... ph) {
        String raw = getConfig().getString("messages.prefix", "")
                + getConfig().getString("messages." + path, "<red>missing message key: " + path + "</red>");
        for (int i = 0; i + 1 < ph.length; i += 2) raw = raw.replace(ph[i], ph[i + 1]);
        return mm(raw);
    }

    public void send(CommandSender to, String path, String... ph) { to.sendMessage(msg(path, ph)); }

    /** Server-wide broadcast; safe from async threads (chat is thread-safe on Paper). */
    public void broadcast(String path, String... ph) { getServer().broadcast(msg(path, ph)); }

    /** Flags the player as a Ghost. Gamemode switches only if they're alive and online. */
    public void eliminate(Player p) {
        store.setGhost(p.getUniqueId(), true);
        broadcast("eliminated", "%player%", p.getName());
        if (!p.isDead()) p.setGameMode(GameMode.SPECTATOR); // dead players get SPECTATOR on respawn (JoinListener)
        store.save();
    }

    /** Revives a ghost (online or offline). Effects apply when they're online. */
    public void revive(OfflinePlayer target, String reviverName) {
        var id = target.getUniqueId();
        store.setGhost(id, false);
        long given = getConfig().getLong("revive-time", 7200L);
        store.add(id, given);
        broadcast("revived", "%player%", target.getName(), "%reviver%", reviverName, "%time%", Times.format(given));
        Player online = target.getPlayer();
        if (online != null) {
            store.setPendingSurvival(id, false);
            online.setGameMode(GameMode.SURVIVAL);
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            online.getWorld().spawnParticle(Particle.FIREWORK,
                    online.getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.05);
        } else {
            // offline revive: their saved gamemode is SPECTATOR; snap to Survival on next join
            store.setPendingSurvival(id, true);
        }
        store.save();
    }
}
