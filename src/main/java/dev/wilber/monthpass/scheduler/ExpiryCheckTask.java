package dev.wilber.monthpass.scheduler;

import dev.wilber.monthpass.MonthPass;
import dev.wilber.monthpass.database.PlayerCardData;
import dev.wilber.monthpass.manager.CardManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;

public class ExpiryCheckTask extends BukkitRunnable {

    private final MonthPass plugin;
    private final CardManager cardManager;

    public ExpiryCheckTask(MonthPass plugin, CardManager cardManager) {
        this.plugin = plugin;
        this.cardManager = cardManager;
    }

    @Override
    public void run() {
        // 對所有在線玩家從 cache 檢查到期（此任務是非同步執行，需回主線程操作 Bukkit API）
        // 注意：checkExpiry 會修改 cache，需在主線程執行
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                cardManager.checkExpiry(player);
            }
        });

        // 非同步從 DB 撈所有過期卡片，處理離線玩家
        cardManager.getDatabase().getAllExpiredCards().thenAccept(expiredList -> {
            for (PlayerCardData card : expiredList) {
                UUID uuid = card.getPlayerUuid();
                Player onlinePlayer = Bukkit.getPlayer(uuid);

                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    // 在線玩家回到主線程處理
                    // （可能已由上面的 checkExpiry 處理，handleExpiry 會重複刪 DB，但不會有問題）
                    final Player p = onlinePlayer;
                    final String cardId = card.getCardId();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (p.isOnline()) {
                            cardManager.handleExpiry(p, cardId, true);
                        }
                    });
                } else {
                    // 離線玩家：只刪 DB 記錄
                    cardManager.handleOfflineExpiry(uuid, card.getCardId());
                }
            }
        });
    }
}
