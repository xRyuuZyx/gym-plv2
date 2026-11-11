package me.gym;

import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TrainingManager {

    private final Gym plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    private static final class Session {
        final Player player;
        final StatType type;
        final Location origin;
        final GameMode mode;
        final int total;
        Entity camera;
        Entity npc;
        BukkitTask task;
        int elapsed = 0;
        boolean ending = false;

        Session(Player player, StatType type, Location origin, GameMode mode, int total) {
            this.player = player;
            this.type = type;
            this.origin = origin;
            this.mode = mode;
            this.total = total;
        }
    }

    public TrainingManager(Gym plugin) {
        this.plugin = plugin;
    }

    public boolean isTraining(Player p) {
        return sessions.containsKey(p.getUniqueId());
    }

    // ------------------------------------------------------------------ start

    public void start(Player p, StatType type) {
        UUID id = p.getUniqueId();
        if (sessions.containsKey(id)) return;
        if (!p.isOnline() || p.isDead()) return;

        if (p.getGameMode() == GameMode.SPECTATOR) {
            Msg.send(p, "spectator-deny");
            return;
        }

        if (plugin.stats().getLevel(id, type) >= plugin.stats().maxLevel()) {
            Msg.send(p, "already-max", "{stat}", statName(type));
            return;
        }

        if (p.isInsideVehicle()) {
            p.leaveVehicle();
        }

        Location origin = p.getLocation().clone();
        World world = origin.getWorld();
        if (world == null) return;

        int total = Math.max(1, plugin.getConfig().getInt("machines." + type.key() + ".duration-seconds", 40));

        // --- posisi kamera: di depan pemain, menghadap balik ke pemain
        double distance = plugin.getConfig().getDouble("camera.distance", 3.0);
        double height = plugin.getConfig().getDouble("camera.height", 1.3);
        double lookHeight = plugin.getConfig().getDouble("camera.look-height", 1.0);
        double yawOffset = plugin.getConfig().getDouble("camera.yaw-offset", 0.0);

        Vector dir = origin.getDirection().setY(0);
        if (dir.lengthSquared() < 1.0E-6) {
            dir = new Vector(0, 0, 1);
        } else {
            dir.normalize();
        }
        dir.rotateAroundY(Math.toRadians(yawOffset));

        Location camLoc = origin.clone().add(dir.clone().multiply(distance));
        camLoc.setY(origin.getY() + height);
        Vector look = origin.toVector().add(new Vector(0, lookHeight, 0)).subtract(camLoc.toVector());
        camLoc.setDirection(look);

        ArmorStand camera;
        try {
            camera = world.spawn(camLoc, ArmorStand.class, a -> {
                a.setVisible(false);
                a.setGravity(false);
                a.setMarker(true);
                a.setInvulnerable(true);
                a.setSilent(true);
                a.setSmall(true);
                a.setPersistent(false);
            });
        } catch (Throwable t) {
            plugin.getLogger().warning("Gagal membuat kamera: " + t);
            Msg.send(p, "error");
            return;
        }

        Entity npc = spawnNpc(p, origin, type);

        Session s = new Session(p, type, origin, p.getGameMode(), total);
        s.camera = camera;
        s.npc = npc;
        sessions.put(id, s);

        // simpan data pemulihan (kalau server mati saat latihan)
        plugin.stats().setPending(id, origin, s.mode);

        p.setGameMode(GameMode.SPECTATOR);
        if (p.getGameMode() != GameMode.SPECTATOR) {
            // gamemode ditolak plugin lain
            end(s, false);
            Msg.send(p, "error");
            return;
        }

        // target spectator harus di-set setelah gamemode berubah
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (sessions.get(id) != s || s.ending || !p.isOnline()) return;
            try {
                p.setSpectatorTarget(s.camera);
            } catch (Throwable t) {
                plugin.getLogger().warning("Gagal set kamera spectator: " + t);
                cancel(p);
            }
        }, 2L);

        showProgress(s);
        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(s), 20L, 20L);
    }

    /** Model pemain pengganti (Mannequin) di posisi alat, karena tubuh asli pemain ikut kamera. */
    private Entity spawnNpc(Player p, Location origin, StatType type) {
        try {
            Location loc = origin.clone();
            loc.setPitch(0f);
            World world = loc.getWorld();
            if (world == null) return null;

            Mannequin m = world.spawn(loc, Mannequin.class, e -> {
                e.setAI(false);
                e.setGravity(false);
                e.setInvulnerable(true);
                e.setSilent(true);
                e.setPersistent(false);
                e.setCustomNameVisible(false);
            });

            String poseName = plugin.getConfig().getString("machines." + type.key() + ".pose", "STANDING");
            if (poseName != null && !poseName.equalsIgnoreCase("STANDING")) {
                try {
                    m.setPose(Pose.valueOf(poseName.toUpperCase()), true);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Pose tidak valid di config: " + poseName);
                }
            }

            // Skin pemain lewat command data (tidak butuh lookup ke server Mojang)
            String nbt = buildProfileNbt(p);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "data merge entity " + m.getUniqueId() + " {hide_description:1b,immovable:1b," + nbt + "}");
            return m;
        } catch (Throwable t) {
            plugin.getLogger().warning("Gagal membuat model pemain (kamera tetap jalan): " + t);
            return null;
        }
    }

    private String buildProfileNbt(Player p) {
        String name = esc(p.getName());
        ProfileProperty textures = null;
        try {
            for (ProfileProperty prop : p.getPlayerProfile().getProperties()) {
                if ("textures".equals(prop.getName())) {
                    textures = prop;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        if (textures == null) {
            return "profile:\"" + name + "\"";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("profile:{name:\"").append(name).append("\",properties:[{name:\"textures\",value:\"")
                .append(esc(textures.getValue())).append("\"");
        if (textures.getSignature() != null) {
            sb.append(",signature:\"").append(esc(textures.getSignature())).append("\"");
        }
        sb.append("}]}");
        return sb.toString();
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ------------------------------------------------------------------ tick

    private void tick(Session s) {
        if (s.ending) return;
        Player p = s.player;
        if (!p.isOnline()) {
            end(s, false);
            return;
        }
        // pemain keluar dari mode kamera (mis. gamemode diubah / target hilang)
        if (p.getGameMode() != GameMode.SPECTATOR || p.getSpectatorTarget() == null) {
            cancel(p);
            return;
        }

        s.elapsed++;
        if (s.elapsed >= s.total) {
            end(s, true);
        } else {
            showProgress(s);
        }
    }

    private void showProgress(Session s) {
        int percent = (int) Math.min(100, Math.round(100.0 * s.elapsed / s.total));
        int remaining = Math.max(0, s.total - s.elapsed);

        int len = 20;
        int filled = Math.min(len, (int) Math.round(len * (double) s.elapsed / s.total));
        StringBuilder bar = new StringBuilder("&a");
        for (int i = 0; i < filled; i++) bar.append('█');
        bar.append("&7");
        for (int i = filled; i < len; i++) bar.append('░');

        String title = Msg.raw("title-" + s.type.key());
        String sub = Msg.raw("progress-subtitle",
                "{bar}", bar.toString(),
                "{percent}", String.valueOf(percent),
                "{remaining}", String.valueOf(remaining));

        s.player.showTitle(Title.title(Msg.c(title), Msg.c(sub),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ZERO)));
    }

    // ------------------------------------------------------------------ end

    /** Batalkan latihan tanpa hadiah. */
    public void cancel(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.ending) return;
        boolean online = p.isOnline();
        end(s, false);
        if (online) {
            Msg.send(p, "session-cancel");
        }
    }

    public void endAll() {
        for (Session s : new ArrayList<>(sessions.values())) {
            end(s, false);
        }
    }

    private void end(Session s, boolean success) {
        if (s.ending) return;
        s.ending = true;

        UUID id = s.player.getUniqueId();
        sessions.remove(id); // harus sebelum setSpectatorTarget(null) agar event stop-spectating diabaikan

        if (s.task != null) s.task.cancel();
        if (s.camera != null && s.camera.isValid()) s.camera.remove();
        if (s.npc != null && s.npc.isValid()) s.npc.remove();

        Player p = s.player;
        if (p.isOnline()) {
            try {
                p.setSpectatorTarget(null);
            } catch (Throwable ignored) {
            }
            p.setGameMode(s.mode);
            p.teleport(s.origin);
            p.setFallDistance(0f);
            p.clearTitle();

            if (success) {
                int gained = Math.max(1, plugin.getConfig().getInt("training.levels-per-session", 1));
                int level = plugin.stats().addLevel(id, s.type, gained);
                plugin.stats().apply(p);

                p.showTitle(Title.title(
                        Msg.c(Msg.raw("done-title")),
                        Msg.c(Msg.raw("done-subtitle", "{stat}", statName(s.type), "{level}", String.valueOf(level))),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
                Msg.send(p, "session-done", "{stat}", statName(s.type), "{level}", String.valueOf(level));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        }
        plugin.stats().clearPending(id);
    }

    /** Pulihkan pemain jika server sempat mati saat mereka latihan. */
    public void restorePending(Player p) {
        if (isTraining(p)) return;
        UUID id = p.getUniqueId();
        GameMode gm = plugin.stats().pendingMode(id);
        if (gm == null) return;
        Location loc = plugin.stats().pendingLocation(id);
        p.setGameMode(gm);
        if (loc != null && loc.getWorld() != null) {
            p.teleport(loc);
        }
        plugin.stats().clearPending(id);
    }

    public static String statName(StatType type) {
        return Msg.raw("stat-" + type.key());
    }
}
