package me.gym;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerStopSpectatingEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class PlayerListener implements Listener {

    private final Gym plugin;

    public PlayerListener(Gym plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            plugin.training().restorePending(p);
            plugin.stats().apply(p);
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.training().cancel(p);
        plugin.debugSet().remove(p.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.stats().apply(p);
        }, 1L);
    }

    /** Pemain menekan Shift saat mode kamera -> latihan dibatalkan. */
    @EventHandler
    public void onStopSpectating(PlayerStopSpectatingEntityEvent e) {
        Player p = e.getPlayer();
        if (!plugin.training().isTraining(p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> plugin.training().cancel(p));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!plugin.training().isTraining(e.getPlayer())) return;
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!plugin.training().isTraining(p)) return;
        String msg = e.getMessage().toLowerCase();
        if (msg.equals("/gym") || msg.startsWith("/gym ")) return;
        e.setCancelled(true);
        Msg.send(p, "command-blocked");
    }
}
