package dev.wilber.monthpass.listener;

import dev.wilber.monthpass.manager.CardManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;

public class PlayerExpChangeListener implements Listener {

    private final CardManager cardManager;

    public PlayerExpChangeListener(CardManager cardManager) {
        this.cardManager = cardManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onExpChange(PlayerExpChangeEvent event) {
        int amount = event.getAmount();
        // 只處理正向經驗獲取，損失不加成
        if (amount <= 0) return;

        Player player = event.getPlayer();
        double multiplier = cardManager.getBestExpMultiplier(player);
        if (multiplier <= 1.0) return;

        // 四捨五入，最低保留原始值
        int boosted = (int) Math.max(amount, Math.round(amount * multiplier));
        event.setAmount(boosted);
    }
}
