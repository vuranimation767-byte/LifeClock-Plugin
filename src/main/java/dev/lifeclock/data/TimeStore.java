package dev.lifeclock.data;

import dev.lifeclock.LifeClockPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All player time state, kept in memory and persisted to times.yml.
 * Every edit goes through ConcurrentHashMap#compute, so async HUD ticks and
 * main-thread events can never lose an update.
 */
public final class TimeStore {

    public record PlayerState(long seconds, boolean ghost, boolean pendingSurvival) {}

    private final Map<UUID, PlayerState> players = new ConcurrentHashMap<>();
    private final File file;
    private final LifeClockPlugin plugin;

    public TimeStore(LifeClockPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "times.yml");
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String id : yml.getKeys(false)) {
            players.put(UUID.fromString(id), new PlayerState(
                    yml.getLong(id + ".seconds"),
                    yml.getBoolean(id + ".ghost"),
                    yml.getBoolean(id + ".pendingSurvival")));
        }
    }

    /** Persists everything. Thread-safe; called on changes, every minute, and on disable. */
    public synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (var e : players.entrySet()) {
            String id = e.getKey().toString();
            yml.set(id + ".seconds", e.getValue().seconds());
            yml.set(id + ".ghost", e.getValue().ghost());
            yml.set(id + ".pendingSurvival", e.getValue().pendingSurvival());
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save times.yml: " + ex.getMessage());
        }
    }

    public List<UUID> ghosts() {
        List<UUID> ids = new ArrayList<>();
        for (var e : players.entrySet()) if (e.getValue().ghost()) ids.add(e.getKey());
        return ids;
    }

    public long seconds(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerState(plugin.startSeconds(), false, false)).seconds();
    }

    /** Adds delta (negative to drain), clamped at 0. Returns the new total. */
    public long add(UUID id, long delta) {
        PlayerState after = players.compute(id, (k, st) -> new PlayerState(
                Math.max(0, (st == null ? plugin.startSeconds() : st.seconds()) + delta),
                st == null ? false : st.ghost(),
                st == null ? false : st.pendingSurvival()));
        return after.seconds();
    }

    public void setSeconds(UUID id, long value) {
        players.compute(id, (k, st) -> new PlayerState(
                Math.max(0, value),
                st == null ? false : st.ghost(),
                st == null ? false : st.pendingSurvival()));
    }

    public boolean isGhost(UUID id) {
        PlayerState st = players.get(id);
        return st != null && st.ghost();
    }

    public void setGhost(UUID id, boolean ghost) {
        players.compute(id, (k, st) -> new PlayerState(
                st == null ? (ghost ? 0 : plugin.startSeconds()) : st.seconds(),
                ghost,
                st == null ? false : st.pendingSurvival()));
    }

    /** True when an offline player was revived and still needs the Survival snap on relog. */
    public boolean isPendingSurvival(UUID id) {
        PlayerState st = players.get(id);
        return st != null && st.pendingSurvival();
    }

    public void setPendingSurvival(UUID id, boolean value) {
        players.compute(id, (k, st) -> new PlayerState(
                st == null ? plugin.startSeconds() : st.seconds(),
                st == null ? false : st.ghost(),
                value));
    }
}
