package dev.wilber.monthpass.manager;

import dev.wilber.monthpass.MonthPass;
import dev.wilber.monthpass.config.CardDefinition;
import dev.wilber.monthpass.config.ConfigManager;
import dev.wilber.monthpass.database.PlayerCardData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FlyManager {

    private final MonthPass plugin;
    private final CardManager cardManager;
    private final ConfigManager configManager;

    /**
     * 由月卡給予飛行的玩家。撤銷時只撤自己給的：
     * 原本只要「沒有月卡飛行資格」就一律關掉飛行，會把黏液科技（無限矩陣等）、其他插件給的飛行在傳送、坐下後誤關。
     */
    private final Set<UUID> granted = ConcurrentHashMap.newKeySet();

    public FlyManager(MonthPass plugin, CardManager cardManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cardManager = cardManager;
        this.configManager = configManager;
    }

    /**
     * 判斷玩家在其當前世界是否應有飛行能力。
     * 不修改任何狀態，純粹回傳 boolean。
     */
    public boolean shouldHaveFly(Player player) {
        if (!configManager.isFlyModuleEnabled()) return false;
        if (player.hasPermission("monthpass.fly.bypass")) return false;

        String worldName = player.getWorld().getName();
        List<PlayerCardData> cards = cardManager.getPlayerCardsFromCache(player.getUniqueId());

        for (PlayerCardData cardData : cards) {
            if (cardData.isExpired()) continue;
            CardDefinition def = configManager.getCard(cardData.getCardId());
            if (def == null || def.getFly() == null) continue;
            if (def.getFly().isEnable() && def.getFly().getWorlds().contains(worldName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 立即根據月卡狀態設定玩家飛行。
     * Creative / Spectator 完全不干涉。
     */
    public void checkFly(Player player) {
        if (!player.isOnline()) return;

        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;

        if (!configManager.isFlyModuleEnabled()) return;
        if (player.hasPermission("monthpass.fly.bypass")) return;

        boolean canFly = shouldHaveFly(player);

        if (canFly) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
                granted.add(player.getUniqueId());
            }
        } else if (granted.remove(player.getUniqueId()) && player.getAllowFlight()) {
            if (player.isFlying()) player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    /**
     * 延遲 delayTicks ticks 後執行 checkFly。
     * 用於對抗 Multiverse-Core 等插件延遲重置飛行的情況。
     */
    public void checkFlyLater(Player player, long delayTicks) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) checkFly(p);
        }, delayTicks);
    }

    /** 預設 2 tick 延遲 */
    public void checkFlyLater(Player player) {
        checkFlyLater(player, 2L);
    }

    /**
     * 強制補正：只補回「應有飛行卻被關掉」的方向。
     * 定期任務使用，不反向關飛行（避免誤殺 Creative 等）。
     */
    public void enforceFly(Player player) {
        if (!player.isOnline()) return;
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
        if (!configManager.isFlyModuleEnabled()) return;
        if (player.hasPermission("monthpass.fly.bypass")) return;

        if (shouldHaveFly(player) && !player.getAllowFlight()) {
            player.setAllowFlight(true);
            granted.add(player.getUniqueId());
        }
    }

    /** 對所有在線玩家執行強制補正（定期任務呼叫）。 */
    public void enforceAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            enforceFly(player);
        }
    }

    /** 對所有在線玩家完整重新檢查（reload 時使用）。 */
    public void checkFlyForAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkFly(player);
        }
    }

    /**
     * 月卡到期 / 被移除時撤銷飛行。
     * 只有「目前允許飛行且不再有任何卡支援當前世界」時才撤銷。
     */
    public void checkRevokeFlyOnExpiry(Player player) {
        if (!player.getAllowFlight()) return;
        if (!shouldHaveFly(player)) revokeFly(player);
    }

    /**
     * 強制撤銷飛行。Creative / Spectator 與 bypass 不干涉。
     */
    public void revokeFly(Player player) {
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
        if (player.hasPermission("monthpass.fly.bypass")) return;
        if (!granted.remove(player.getUniqueId())) return; // 不是月卡給的飛行就不動
        if (player.isFlying()) player.setFlying(false);
        player.setAllowFlight(false);
    }

    /**
     * 給予玩家飛行（/monthpass fly 指令呼叫）。
     * @return true 代表成功授予，false 代表此世界無飛行資格
     */
    public boolean grantFly(Player player) {
        if (!shouldHaveFly(player)) return false;
        player.setAllowFlight(true);
        granted.add(player.getUniqueId());
        return true;
    }
}
