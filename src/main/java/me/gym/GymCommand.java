package me.gym;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class GymCommand implements CommandExecutor, TabCompleter {

    private final Gym plugin;

    public GymCommand(Gym plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gym.use")) {
            Msg.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "stats" -> handleStats(sender, args);
            case "stop" -> {
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, "players-only");
                    return true;
                }
                if (!plugin.training().isTraining(p)) {
                    Msg.send(p, "not-training");
                } else {
                    plugin.training().cancel(p);
                }
            }
            case "set" -> handleSet(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("gym.admin")) {
                    Msg.send(sender, "no-permission");
                    return true;
                }
                plugin.reloadConfig();
                for (Player p : Bukkit.getOnlinePlayers()) plugin.stats().apply(p);
                Msg.send(sender, "reloaded");
            }
            case "debug" -> {
                if (!sender.hasPermission("gym.admin")) {
                    Msg.send(sender, "no-permission");
                    return true;
                }
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, "players-only");
                    return true;
                }
                UUID id = p.getUniqueId();
                if (plugin.debugSet().remove(id)) {
                    Msg.send(p, "debug-off");
                } else {
                    plugin.debugSet().add(id);
                    Msg.send(p, "debug-on");
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        Msg.sendPlain(sender, "help-header");
        Msg.sendPlain(sender, "help-stats");
        Msg.sendPlain(sender, "help-stop");
        if (sender.hasPermission("gym.admin")) {
            Msg.sendPlain(sender, "help-admin");
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("gym.admin")) {
                Msg.send(sender, "no-permission");
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Msg.send(sender, "player-not-found");
                return;
            }
        } else {
            if (!(sender instanceof Player p)) {
                Msg.send(sender, "players-only");
                return;
            }
            target = p;
        }

        StatManager sm = plugin.stats();
        int max = sm.maxLevel();
        int str = sm.getLevel(target.getUniqueId(), StatType.STRENGTH);
        int def = sm.getLevel(target.getUniqueId(), StatType.DEFENSE);

        Msg.sendPlain(sender, "stats-header", "{player}", target.getName());
        Msg.sendPlain(sender, "stats-strength",
                "{level}", String.valueOf(str),
                "{max}", String.valueOf(max),
                "{bonus}", fmt(sm.strengthBonus(str)));
        Msg.sendPlain(sender, "stats-defense",
                "{level}", String.valueOf(def),
                "{max}", String.valueOf(max),
                "{bonus}", fmt(sm.defenseBonus(def)));
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gym.admin")) {
            Msg.send(sender, "no-permission");
            return;
        }
        if (args.length < 4) {
            Msg.send(sender, "invalid-usage", "{usage}", "/gym set <pemain> <strength|defense> <level>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Msg.send(sender, "player-not-found");
            return;
        }
        StatType type = StatType.fromString(args[2]);
        if (type == null) {
            Msg.send(sender, "invalid-stat");
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            Msg.send(sender, "invalid-number");
            return;
        }
        int applied = plugin.stats().setLevel(target.getUniqueId(), type, level);
        plugin.stats().apply(target);
        Msg.send(sender, "set-done",
                "{stat}", TrainingManager.statName(type),
                "{player}", target.getName(),
                "{level}", String.valueOf(applied));
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("gym.admin");

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("stats", "stop", "help"));
            if (admin) subs.addAll(List.of("set", "reload", "debug"));
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (admin && args.length == 2
                && (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("set"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        } else if (admin && args.length == 3 && args[0].equalsIgnoreCase("set")) {
            for (String s : List.of("strength", "defense")) {
                if (s.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(s);
            }
        }
        return out;
    }
}
