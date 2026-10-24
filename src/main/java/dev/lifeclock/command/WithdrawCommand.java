package dev.lifeclock.command;

import dev.lifeclock.LifeClockPlugin;
import dev.lifeclock.Times;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /withdraw — opens the time-withdrawal UI; /withdraw <time> converts directly. Everyone may use it. */
public final class WithdrawCommand implements CommandExecutor {

    private final LifeClockPlugin plugin;

    public WithdrawCommand(LifeClockPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.send(sender, "player-only");
            return true;
        }

        if (args.length == 0) {
            plugin.withdrawMenu().open(p); // no amount → the selection UI
            return true;
        }

        long seconds = Times.parse(args[0]);
        if (seconds <= 0) {
            plugin.send(p, "invalid-time", "%input%", args[0]);
            return true;
        }
        plugin.withdrawMenu().attempt(p, seconds);
        return true;
    }
}
