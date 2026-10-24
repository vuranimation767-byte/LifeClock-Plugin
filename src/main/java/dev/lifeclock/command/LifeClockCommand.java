package dev.lifeclock.command;

import dev.lifeclock.LifeClockPlugin;
import dev.lifeclock.Times;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/** /lifeclock add|remove|set <player> <time> · check <player> · revive <player> */
public final class LifeClockCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("add", "remove", "set", "check", "revive");
    private final LifeClockPlugin plugin;

    public LifeClockCommand(LifeClockPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            plugin.send(sender, "usage");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean needsTime = !sub.equals("check") && !sub.equals("revive");
        if (needsTime && args.length < 3) {
            plugin.send(sender, "usage");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            plugin.send(sender, "player-not-found", "%player%", args[1]);
            return true;
        }
        var id = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : args[1];

        switch (sub) {
            case "add", "remove" -> {
                long amount = Times.parse(args[2]);
                if (amount < 0) {
                    plugin.send(sender, "invalid-time", "%input%", args[2]);
                    return true;
                }
                long total = plugin.store().add(id, sub.equals("add") ? amount : -amount);
                plugin.send(sender, sub + "-done", "%player%", name, "%time%", Times.format(amount),
                        "%total%", Times.format(total));
                plugin.store().save();
            }
            case "set" -> {
                long amount = Times.parse(args[2]);
                if (amount < 0) {
                    plugin.send(sender, "invalid-time", "%input%", args[2]);
                    return true;
                }
                plugin.store().setSeconds(id, amount);
                plugin.send(sender, "set-done", "%player%", name, "%time%", Times.format(amount));
                plugin.store().save();
            }
            case "check" -> {
                boolean ghost = plugin.store().isGhost(id);
                plugin.send(sender, "check", "%player%", name, "%time%", Times.format(plugin.store().seconds(id)),
                        "%ghost%", ghost ? " <red>☠</red>" : "");
            }
            case "revive" -> {
                if (!plugin.store().isGhost(id)) {
                    plugin.send(sender, "not-ghost", "%player%", name);
                    return true;
                }
                long given = plugin.getConfig().getLong("revive-time", 7200L);
                plugin.revive(target, "the admins");
                plugin.send(sender, "revive-done", "%player%", name, "%time%", Times.format(given));
            }
            default -> plugin.send(sender, "usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1)
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return null; // let Bukkit complete player names
    }
}
