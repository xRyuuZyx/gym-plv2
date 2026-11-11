package me.gym;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public final class StatManager {

    private final Gym plugin;
    private final File file;
    private final NamespacedKey strengthKey;
    private final NamespacedKey defenseKey;
    private YamlConfiguration cfg = new YamlConfiguration();

    public StatManager(Gym plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.strengthKey = new NamespacedKey(plugin, "strength_bonus");
        this.defenseKey = new NamespacedKey(plugin, "defense_bonus");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Gagal menyimpan data.yml: " + e.getMessage());
        }
    }

    public int maxLevel() {
        return Math.max(1, plugin.getConfig().getInt("stats.max-level", 50));
    }

    public int getLevel(UUID id, StatType type) {
        int v = cfg.getInt("players." + id + "." + type.key(), 0);
        return Math.max(0, Math.min(maxLevel(), v));
    }

    public int setLevel(UUID id, StatType type, int level) {
        int v = Math.max(0, Math.min(maxLevel(), level));
        cfg.set("players." + id + "." + type.key(), v);
        save();
        return v;
    }

    public int addLevel(UUID id, StatType type, int amount) {
        return setLevel(id, type, getLevel(id, type) + amount);
    }

    public double strengthBonus(int level) {
        return level * plugin.getConfig().getDouble("stats.strength-damage-per-level", 0.1);
    }

    public double defenseBonus(int level) {
        return level * plugin.getConfig().getDouble("stats.defense-armor-per-level", 0.1);
    }

    /** Pasang bonus attribute sesuai level pemain. */
    public void apply(Player p) {
        UUID id = p.getUniqueId();
        applyModifier(p, Attribute.ATTACK_DAMAGE, strengthKey, strengthBonus(getLevel(id, StatType.STRENGTH)));
        applyModifier(p, Attribute.ARMOR, defenseKey, defenseBonus(getLevel(id, StatType.DEFENSE)));
    }

    private void applyModifier(Player p, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        for (AttributeModifier mod : new ArrayList<>(inst.getModifiers())) {
            if (key.equals(mod.getKey())) {
                inst.removeModifier(mod);
            }
        }
        if (amount > 0) {
            inst.addTransientModifier(new AttributeModifier(key, amount,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
    }

    // ---------- data sementara (untuk pemulihan kalau server crash saat latihan) ----------

    public void setPending(UUID id, Location loc, GameMode mode) {
        cfg.set("pending." + id + ".location", loc);
        cfg.set("pending." + id + ".gamemode", mode.name());
        save();
    }

    public Location pendingLocation(UUID id) {
        return cfg.getLocation("pending." + id + ".location");
    }

    public GameMode pendingMode(UUID id) {
        String s = cfg.getString("pending." + id + ".gamemode");
        if (s == null) return null;
        try {
            return GameMode.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void clearPending(UUID id) {
        if (cfg.contains("pending." + id)) {
            cfg.set("pending." + id, null);
            save();
        }
    }
}
