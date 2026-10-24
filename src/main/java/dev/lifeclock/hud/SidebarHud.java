package dev.lifeclock.hud;

import dev.lifeclock.LifeClockPlugin;
import dev.lifeclock.Times;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sidebar HUD: a 2-row panel top-right — neutral gray header ("TIME") plus one
 * line with the clock numbers, colors on the numbers only (same thresholds as
 * the spec). The row is a team prefix/suffix pair; the entry string is a single
 * space (renders as nothing), and NumberFormat.blank() hides the numeric column.
 * Scoreboard ops are main-thread: the async tick drains timers, then hands a
 * snapshot to the next main-tick for rendering.
 */
public final class SidebarHud implements Listener {

    // Spec thresholds: GREEN > 10h, YELLOW 5h..10h, RED < 5h; red/white flash under 1h
    private static final long GREEN_ABOVE = 10 * 3600L, RED_BELOW = 5 * 3600L, FLASH_BELOW = 1 * 3600L;

    private final LifeClockPlugin plugin;
    private final Map<UUID, Team> boards = new ConcurrentHashMap<>();
    private long ticks;

    public SidebarHud(LifeClockPlugin plugin) { this.plugin = plugin; }

    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tick, 20L, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
    }

    /** Restores online players to the main scoreboard (called on plugin disable). */
    public void shutdown() {
        restoreMains();
    }

    private void tick() {
        ticks++;
        if (ticks % 60 == 0) plugin.store().save();

        Map<UUID, Long> snapshot = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead()) continue;
            UUID id = p.getUniqueId();
            long left = plugin.store().add(id, -1);
            snapshot.put(id, left);

            if (left <= 0 && !plugin.store().isGhost(id)) {
                plugin.store().setGhost(id, true);
                plugin.broadcast("eliminated", "%player%", p.getName());
                plugin.store().save();
                // Gamemode changes must happen on the main thread.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(id);
                    if (online != null && !online.isDead()) online.setGameMode(GameMode.SPECTATOR);
                });
            }
        }

        if (!plugin.getConfig().getBoolean("hud.sidebar.enabled", true)) {
            if (!boards.isEmpty()) Bukkit.getScheduler().runTask(plugin, this::restoreMains);
            return;
        }

        long tick = ticks;
        String title = plugin.getConfig().getString("hud.sidebar.title", "TIME");
        String format = plugin.getConfig().getString("hud.sidebar.format", "%time%");
        // Scoreboard APIs are main-thread only — render the snapshot there.
        Bukkit.getScheduler().runTask(plugin, () -> render(snapshot, tick, title, format));
    }

    private void render(Map<UUID, Long> snapshot, long tick, String title, String format) {
        for (var entry : snapshot.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            if (plugin.store().isGhost(entry.getKey())) {
                dropBoard(entry.getKey()); // ghosts are out of the game: no clock panel
                continue;
            }

            Team line = boards.computeIfAbsent(entry.getKey(), id -> newBoard(title));
            if (p.getScoreboard() != line.getScoreboard()) p.setScoreboard(line.getScoreboard());
            paint(line, entry.getValue(), tick, format);
        }
    }

    /** Removes the player's panel and hands back the main scoreboard. */
    private void dropBoard(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        boards.remove(id);
    }

    private Team newBoard(String title) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        // DEATH_COUNT (non-dummy) is required for sidebar visibility; we override scores manually.
        Objective obj = board.registerNewObjective("lifeclock", Criteria.DEATH_COUNT,
                Component.text(title).color(NamedTextColor.GRAY), RenderType.INTEGER);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // The row = team prefix + entry + suffix. The entry is a single space so the
        // row is pure text (prefix = label, suffix = colored numbers).
        Team team = board.registerNewTeam("lifeclock");
        team.addEntry(" ");
        Score line = obj.getScore(" ");
        line.setScore(1);
        line.numberFormat(NumberFormat.blank()); // hide the "1": the row shows no number column
        return team;
    }

    private void paint(Team team, long left, long tick, String format) {
        String prefix = format;
        String tail = "";
        int at = format.indexOf("%time%");
        if (at >= 0) {
            prefix = format.substring(0, at);
            tail = format.substring(at + "%time%".length());
        }

        NamedTextColor color = left > GREEN_ABOVE ? NamedTextColor.GREEN
                : left >= RED_BELOW ? NamedTextColor.YELLOW : NamedTextColor.RED;
        if (left < FLASH_BELOW && tick % 2 == 0) color = NamedTextColor.WHITE; // red/white heartbeat

        team.prefix(Component.text(prefix).color(NamedTextColor.GRAY));
        team.suffix(Component.text()
                .append(Component.text(Times.clock(left)).color(color))
                .append(Component.text(tail).color(NamedTextColor.GRAY))
                .build());
    }

    private void restoreMains() {
        var main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (UUID id : boards.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setScoreboard(main);
        }
        boards.clear();
    }
}
