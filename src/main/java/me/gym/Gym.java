package me.gym;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Gym extends JavaPlugin {

    private static Gym instance;

    private StatManager stats;
    private TrainingManager training;
    private final Set<UUID> debug = new HashSet<>();

    public static Gym get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        stats = new StatManager(this);
        stats.load();
        training = new TrainingManager(this);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        GymCommand cmd = new GymCommand(this);
        PluginCommand pc = getCommand("gym");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        // Daftarkan hook Nexo/Oraxen setelah semua plugin selesai di-enable
        MachineHook hook = new MachineHook(this);
        Bukkit.getScheduler().runTask(this, hook::register);

        for (Player p : Bukkit.getOnlinePlayers()) {
            stats.apply(p);
        }
    }

    @Override
    public void onDisable() {
        if (training != null) training.endAll();
        if (stats != null) stats.save();
    }

    public StatManager stats() {
        return stats;
    }

    public TrainingManager training() {
        return training;
    }

    public Set<UUID> debugSet() {
        return debug;
    }

    /** Cocokkan ID item Nexo/Oraxen dengan alat gym di config. */
    public StatType matchMachine(String id) {
        if (id == null) return null;
        String norm = normalize(id);
        for (StatType type : StatType.values()) {
            for (String cfg : getConfig().getStringList("machines." + type.key() + ".ids")) {
                if (normalize(cfg).equals(norm)) return type;
            }
        }
        return null;
    }

    private static String normalize(String id) {
        String s = id.trim().toLowerCase();
        int idx = s.lastIndexOf(':');
        if (idx >= 0) s = s.substring(idx + 1);
        return s;
    }
}
