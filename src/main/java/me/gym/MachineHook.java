package me.gym;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Menyambungkan klik kanan pada furniture/block Nexo atau Oraxen ke sistem latihan.
 * Memakai reflection sehingga plugin ini tidak perlu dependensi compile ke Nexo/Oraxen
 * dan tetap jalan walau salah satunya tidak terpasang.
 */
public final class MachineHook {

    private static final String[][] CANDIDATES = {
            // Nexo
            {"Nexo", "com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent"},
            {"Nexo", "com.nexomc.nexo.api.events.custom_block.noteblock.NexoNoteBlockInteractEvent"},
            {"Nexo", "com.nexomc.nexo.api.events.custom_block.stringblock.NexoStringBlockInteractEvent"},
            // Oraxen
            {"Oraxen", "io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureInteractEvent"},
            {"Oraxen", "io.th0rgal.oraxen.api.events.noteblock.OraxenNoteBlockInteractEvent"},
            {"Oraxen", "io.th0rgal.oraxen.api.events.stringblock.OraxenStringBlockInteractEvent"},
    };

    private final Gym plugin;
    private final Listener listener = new Listener() {
    };

    public MachineHook(Gym plugin) {
        this.plugin = plugin;
    }

    public void register() {
        int count = 0;
        for (String[] c : CANDIDATES) {
            if (tryRegister(c[0], c[1])) count++;
        }
        if (count == 0) {
            plugin.getLogger().warning("Tidak ada event Nexo/Oraxen yang berhasil di-hook. "
                    + "Pastikan Nexo atau Oraxen terpasang.");
        } else {
            plugin.getLogger().info("Hook Nexo/Oraxen aktif (" + count + " event).");
        }
    }

    private boolean tryRegister(String pluginName, String className) {
        Plugin other = Bukkit.getPluginManager().getPlugin(pluginName);
        if (other == null || !other.isEnabled()) return false;
        try {
            Class<?> c = Class.forName(className, true, other.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(c)) return false;
            Class<? extends Event> eventClass = c.asSubclass(Event.class);
            Bukkit.getPluginManager().registerEvent(eventClass, listener, EventPriority.NORMAL,
                    (l, e) -> handle(e), plugin, true);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable t) {
            plugin.getLogger().warning("Gagal hook " + className + ": " + t);
            return false;
        }
    }

    private void handle(Event event) {
        try {
            Object playerObj = invoke(event, "getPlayer");
            if (!(playerObj instanceof Player player)) return;

            String id = null;
            Object mechanic = invoke(event, "getMechanic");
            if (mechanic != null) {
                id = asString(invoke(mechanic, "getItemID", "getItemId"));
            }
            if (id == null) {
                id = asString(invoke(event, "getItemID", "getItemId"));
            }
            if (id == null) return;

            if (plugin.debugSet().contains(player.getUniqueId())) {
                Msg.send(player, "debug-id", "{id}", id);
            }

            StatType type = plugin.matchMachine(id);
            if (type == null) return;

            // jalankan di tick berikutnya agar tidak mengganggu proses event Nexo/Oraxen
            Bukkit.getScheduler().runTask(plugin, () -> plugin.training().start(player, type));
        } catch (Throwable t) {
            plugin.getLogger().warning("Error saat memproses klik alat gym: " + t);
        }
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Object invoke(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = target.getClass().getMethod(name);
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                return m.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // coba nama berikutnya
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
