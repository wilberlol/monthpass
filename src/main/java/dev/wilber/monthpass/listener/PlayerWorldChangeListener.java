package dev.wilber.monthpass.listener;

import dev.wilber.monthpass.MonthPass;
import dev.wilber.monthpass.manager.FlyManager;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

public class PlayerWorldChangeListener implements Listener {

    private final MonthPass plugin;
    private final FlyManager flyManager;

    public PlayerWorldChangeListener(MonthPass plugin, FlyManager flyManager) {
        this.plugin = plugin;
        this.flyManager = flyManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        flyManager.checkFly(event.getPlayer());
        flyManager.checkFlyLater(event.getPlayer(), 2L);
        flyManager.checkFlyLater(event.getPlayer(), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) return;
        flyManager.checkFlyLater(event.getPlayer(), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        flyManager.checkFlyLater(event.getPlayer(), 3L);
    }
}
