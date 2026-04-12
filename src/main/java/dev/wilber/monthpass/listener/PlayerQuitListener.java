package dev.wilber.monthpass.listener;

import dev.wilber.monthpass.manager.CardManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final CardManager cardManager;

    public PlayerQuitListener(CardManager cardManager) {
        this.cardManager = cardManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cardManager.unloadPlayerCards(event.getPlayer().getUniqueId());
    }
}
