package dev.wilber.monthpass.listener;

import dev.wilber.monthpass.config.ConfigManager;
import dev.wilber.monthpass.manager.CardManager;
import dev.wilber.monthpass.manager.FlyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final CardManager cardManager;
    private final FlyManager flyManager;
    private final ConfigManager configManager;

    public PlayerJoinListener(CardManager cardManager, FlyManager flyManager, ConfigManager configManager) {
        this.cardManager = cardManager;
        this.flyManager = flyManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // loadPlayerCards 完成後內部會呼叫 checkFly
        // 額外補一個 10-tick 延遲，應對 join 流程中其他插件的重置
        cardManager.loadPlayerCards(event.getPlayer());
        flyManager.checkFlyLater(event.getPlayer(), 10L);
    }
}
