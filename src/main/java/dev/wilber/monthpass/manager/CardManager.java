package dev.wilber.monthpass.manager;

import dev.wilber.monthpass.MonthPass;
import dev.wilber.monthpass.config.CardDefinition;
import dev.wilber.monthpass.config.ConfigManager;
import dev.wilber.monthpass.config.RewardItem;
import dev.wilber.monthpass.database.Database;
import dev.wilber.monthpass.database.PlayerCardData;
import dev.wilber.monthpass.util.ColorUtil;
import dev.wilber.monthpass.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CardManager {

    private final MonthPass plugin;
    private final Database database;
    private final ConfigManager configManager;

    private final Map<UUID, List<PlayerCardData>> playerCardsCache = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    // FlyManager 後期注入，避免循環依賴
    private FlyManager flyManager;

    public CardManager(MonthPass plugin, Database database, ConfigManager configManager) {
        this.plugin = plugin;
        this.database = database;
        this.configManager = configManager;
    }

    public void setFlyManager(FlyManager flyManager) {
        this.flyManager = flyManager;
    }

    /**
     * 非同步載入玩家月卡，套用權限，回主線程做後續處理
     */
    public void loadPlayerCards(Player player) {
        UUID uuid = player.getUniqueId();

        database.getPlayerCards(uuid).thenAccept(cards -> {
            // 過濾已過期的卡（先處理 on-expire）
            List<PlayerCardData> valid = new ArrayList<>();
            List<PlayerCardData> expired = new ArrayList<>();

            for (PlayerCardData card : cards) {
                if (card.isExpired()) {
                    expired.add(card);
                } else {
                    valid.add(card);
                }
            }

            // 存入 cache（只存有效的）
            playerCardsCache.put(uuid, new ArrayList<>(valid));

            // 回到主線程套用權限、處理到期
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                // 建立 PermissionAttachment
                PermissionAttachment oldAttachment = attachments.remove(uuid);
                if (oldAttachment != null) {
                    try { player.removeAttachment(oldAttachment); } catch (Exception ignored) {}
                }
                PermissionAttachment attachment = player.addAttachment(plugin);
                attachments.put(uuid, attachment);

                // 套用所有有效卡的 permission
                for (PlayerCardData card : valid) {
                    CardDefinition def = configManager.getCard(card.getCardId());
                    if (def != null) {
                        attachment.setPermission(def.getPermission(), true);
                    }
                }
                player.recalculatePermissions();

                // 處理過期卡
                for (PlayerCardData card : expired) {
                    handleExpiry(player, card.getCardId(), true);
                }

                // 飛行狀態檢查
                if (flyManager != null) {
                    flyManager.checkFly(player);
                }

                // 到期警告
                checkExpiryWarning(player);
            });
        }).exceptionally(e -> {
            plugin.getLogger().severe("載入玩家 " + player.getName() + " 月卡失敗：" + e.getMessage());
            return null;
        });
    }

    /**
     * 卸載玩家月卡 cache
     */
    public void unloadPlayerCards(UUID uuid) {
        playerCardsCache.remove(uuid);
        // 從 attachments map 移除（Bukkit 在玩家離線後自動清理 attachment）
        attachments.remove(uuid);
    }

    /**
     * 給予月卡
     */
    public void giveCard(CommandSender sender, OfflinePlayer target, String cardId, int days) {
        CardDefinition def = configManager.getCard(cardId);
        if (def == null) {
            sender.sendMessage(configManager.getMessage("card-not-found", "card", cardId));
            return;
        }

        UUID uuid = target.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : uuid.toString();

        database.getPlayerCard(uuid, cardId).thenAccept(optCard -> {
            long now = System.currentTimeMillis();
            boolean isNew = !optCard.isPresent();
            PlayerCardData data;

            if (isNew) {
                // 建立新卡
                data = new PlayerCardData();
                data.setPlayerUuid(uuid);
                data.setPlayerName(targetName);
                data.setCardId(cardId);
                data.setExpiryDate(now + (long) days * 86400_000L);
                data.setActivatedAt(now);
                data.setLastClaimDate(null);
            } else {
                // 疊加天數
                data = optCard.get();
                data.setPlayerName(targetName);
                long currentExpiry = data.getExpiryDate();
                long base = Math.max(currentExpiry, now);
                data.setExpiryDate(base + (long) days * 86400_000L);
            }

            // 計算剩餘天數用於顯示
            final long remainingDays = data.getRemainingDays(configManager.getTimezone());
            final boolean wasNew = isNew;
            final PlayerCardData finalData = data;

            database.savePlayerCard(data).thenRun(() -> {
                // 更新 cache（如果玩家在線）
                Player onlinePlayer = Bukkit.getPlayer(uuid);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // 更新 cache
                    List<PlayerCardData> cache = playerCardsCache.computeIfAbsent(uuid, k -> new ArrayList<>());
                    cache.removeIf(c -> c.getCardId().equals(cardId));
                    cache.add(finalData);

                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        // 賦予權限
                        grantPermission(onlinePlayer, def.getPermission());

                        // 如果是新卡，執行 on-activate commands
                        if (wasNew) {
                            for (String cmd : def.getOnActivateCommands()) {
                                executeCommand(cmd, onlinePlayer);
                            }
                        }

                        // 發送訊息給玩家
                        ZoneId tz = configManager.getTimezone();
                        String expireStr = formatDate(finalData.getExpiryDate(), tz);
                        onlinePlayer.sendMessage(configManager.getMessage("receive-card",
                                "card", ColorUtil.color(def.getDisplayName()),
                                "expire", expireStr));

                        // 更新飛行狀態
                        if (flyManager != null) flyManager.checkFly(onlinePlayer);
                    }

                    // 回報給 sender
                    if (isNew) {
                        sender.sendMessage(configManager.getMessage("give-success",
                                "player", targetName,
                                "card", ColorUtil.color(def.getDisplayName()),
                                "days", String.valueOf(days)));
                    } else {
                        sender.sendMessage(configManager.getMessage("give-success-stacked",
                                "player", targetName,
                                "card", ColorUtil.color(def.getDisplayName()),
                                "days", String.valueOf(days),
                                "remaining", String.valueOf(remainingDays)));
                    }
                });
            });
        }).exceptionally(e -> {
            plugin.getLogger().severe("giveCard 失敗：" + e.getMessage());
            return null;
        });
    }

    /**
     * 設定月卡剩餘天數（直接覆蓋）
     */
    public void setCard(CommandSender sender, OfflinePlayer target, String cardId, int days) {
        CardDefinition def = configManager.getCard(cardId);
        if (def == null) {
            sender.sendMessage(configManager.getMessage("card-not-found", "card", cardId));
            return;
        }

        UUID uuid = target.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : uuid.toString();
        long now = System.currentTimeMillis();
        long newExpiry = now + (long) days * 86400_000L;

        database.getPlayerCard(uuid, cardId).thenAccept(optCard -> {
            final boolean isNew = !optCard.isPresent();
            PlayerCardData data;

            if (isNew) {
                data = new PlayerCardData();
                data.setPlayerUuid(uuid);
                data.setPlayerName(targetName);
                data.setCardId(cardId);
                data.setExpiryDate(newExpiry);
                data.setActivatedAt(now);
                data.setLastClaimDate(null);
            } else {
                data = optCard.get();
                data.setPlayerName(targetName);
                data.setExpiryDate(newExpiry);
            }

            final PlayerCardData finalData = data;

            database.savePlayerCard(data).thenRun(() -> {
                Player onlinePlayer = Bukkit.getPlayer(uuid);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    List<PlayerCardData> cache = playerCardsCache.computeIfAbsent(uuid, k -> new ArrayList<>());
                    cache.removeIf(c -> c.getCardId().equals(cardId));
                    cache.add(finalData);

                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        grantPermission(onlinePlayer, def.getPermission());

                        if (isNew) {
                            for (String cmd : def.getOnActivateCommands()) {
                                executeCommand(cmd, onlinePlayer);
                            }
                        }

                        if (flyManager != null) flyManager.checkFly(onlinePlayer);
                    }

                    sender.sendMessage(configManager.getMessage("set-success",
                            "player", targetName,
                            "card", ColorUtil.color(def.getDisplayName()),
                            "days", String.valueOf(days)));
                });
            });
        }).exceptionally(e -> {
            plugin.getLogger().severe("setCard 失敗：" + e.getMessage());
            return null;
        });
    }

    /**
     * 移除月卡（管理員強制移除，不執行 on-expire）
     */
    public void removeCard(CommandSender sender, OfflinePlayer target, String cardId) {
        CardDefinition def = configManager.getCard(cardId);
        if (def == null) {
            sender.sendMessage(configManager.getMessage("card-not-found", "card", cardId));
            return;
        }

        UUID uuid = target.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : uuid.toString();

        // 先確認玩家有這張卡
        database.getPlayerCard(uuid, cardId).thenAccept(optCard -> {
            if (!optCard.isPresent()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(configManager.getMessage("remove-not-found",
                                "player", targetName,
                                "card", ColorUtil.color(def.getDisplayName()))));
                return;
            }

            database.removePlayerCard(uuid, cardId).thenRun(() -> {
                Player onlinePlayer = Bukkit.getPlayer(uuid);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // 更新 cache
                    List<PlayerCardData> cache = playerCardsCache.get(uuid);
                    if (cache != null) {
                        cache.removeIf(c -> c.getCardId().equals(cardId));
                    }

                    if (onlinePlayer != null && onlinePlayer.isOnline()) {
                        revokePermission(onlinePlayer, def.getPermission());
                        // 移除卡後檢查是否需要撤銷飛行（若已無任何卡支援當前世界）
                        if (flyManager != null) flyManager.checkRevokeFlyOnExpiry(onlinePlayer);
                    }

                    sender.sendMessage(configManager.getMessage("remove-success",
                            "player", targetName,
                            "card", ColorUtil.color(def.getDisplayName())));
                });
            });
        }).exceptionally(e -> {
            plugin.getLogger().severe("removeCard 失敗：" + e.getMessage());
            return null;
        });
    }

    /**
     * 玩家簽到，領取每日獎勵
     */
    public void sign(Player player) {
        UUID uuid = player.getUniqueId();
        ZoneId tz = configManager.getTimezone();
        LocalDate today = LocalDate.now(tz);

        List<PlayerCardData> cards = getPlayerCardsFromCache(uuid);

        // 過濾出今天未簽到且有效的卡
        List<PlayerCardData> unclaimedCards = cards.stream()
                .filter(c -> !c.isExpired() && !c.isClaimedToday(tz))
                .collect(Collectors.toList());

        // 所有卡都已簽到
        boolean hasValidCards = cards.stream().anyMatch(c -> !c.isExpired());
        if (!hasValidCards) {
            player.sendMessage(configManager.getMessage("sign-no-cards"));
            return;
        }

        if (unclaimedCards.isEmpty()) {
            player.sendMessage(configManager.getMessage("sign-already"));
            return;
        }

        // 計算需要的 slot 數
        int requiredSlots = 0;
        for (PlayerCardData cardData : unclaimedCards) {
            CardDefinition def = configManager.getCard(cardData.getCardId());
            if (def != null) {
                requiredSlots += countRequiredSlots(def.getDailyItems());
            }
        }

        // 計算背包空格數
        int availableSlots = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                availableSlots++;
            }
        }

        if (availableSlots < requiredSlots) {
            player.sendMessage(configManager.getMessage("sign-inventory-full",
                    "slots", String.valueOf(requiredSlots)));
            return;
        }

        // 給予獎勵
        player.sendMessage(configManager.getMessage("sign-success"));

        for (PlayerCardData cardData : unclaimedCards) {
            CardDefinition def = configManager.getCard(cardData.getCardId());
            if (def == null) continue;

            // 給予物品
            for (RewardItem rewardItem : def.getDailyItems()) {
                ItemStack item = ItemBuilder.build(rewardItem, player.getName(), today);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    // 背包滿了，丟在地上
                    for (ItemStack dropped : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                    }
                }
            }

            // 執行指令
            for (String cmd : def.getDailyCommands()) {
                executeCommand(cmd, player);
            }

            // 更新 cache
            cardData.setLastClaimDate(today);

            // 發送成功訊息
            player.sendMessage(configManager.getMessage("sign-success-card",
                    "card", ColorUtil.color(def.getDisplayName())));
        }

        // 非同步更新 DB
        for (PlayerCardData cardData : unclaimedCards) {
            database.updateLastClaimDate(uuid, cardData.getCardId(), today);
        }
    }

    /**
     * 檢查玩家月卡是否到期
     */
    public void checkExpiry(Player player) {
        List<PlayerCardData> cards = getPlayerCardsFromCache(player.getUniqueId());
        for (PlayerCardData card : new ArrayList<>(cards)) {
            if (card.isExpired()) {
                handleExpiry(player, card.getCardId(), true);
            }
        }
    }

    /**
     * 處理月卡到期
     * @param executeCommands 是否執行 on-expire commands（玩家在線時才執行）
     */
    public void handleExpiry(Player player, String cardId, boolean executeCommands) {
        UUID uuid = player.getUniqueId();
        CardDefinition def = configManager.getCard(cardId);

        // 從 cache 移除
        List<PlayerCardData> cache = playerCardsCache.get(uuid);
        if (cache != null) {
            cache.removeIf(c -> c.getCardId().equals(cardId));
        }

        // 從 DB 刪除
        database.removePlayerCard(uuid, cardId);

        if (def != null) {
            // 執行 on-expire commands
            if (executeCommands) {
                for (String cmd : def.getOnExpireCommands()) {
                    executeCommand(cmd, player);
                }
            }

            // 撤銷權限
            revokePermission(player, def.getPermission());
        }

        // 發送到期訊息
        player.sendMessage(configManager.getMessage("expire-card",
                "card", def != null ? ColorUtil.color(def.getDisplayName()) : cardId));

        // 到期後撤銷飛行（若已無任何卡支援當前世界）
        if (flyManager != null) flyManager.checkRevokeFlyOnExpiry(player);
    }

    /**
     * 處理離線玩家的過期月卡（只刪 DB，不發訊息）
     */
    public void handleOfflineExpiry(UUID uuid, String cardId) {
        database.removePlayerCard(uuid, cardId);
        // 離線玩家不執行 on-expire commands，不發訊息
    }

    /**
     * 到期前警告
     */
    public void checkExpiryWarning(Player player) {
        ZoneId tz = configManager.getTimezone();
        List<PlayerCardData> cards = getPlayerCardsFromCache(player.getUniqueId());

        for (PlayerCardData card : cards) {
            if (card.isExpired()) continue;
            CardDefinition def = configManager.getCard(card.getCardId());
            if (def == null) continue;

            long remainingDays = card.getRemainingDays(tz);
            int warningDays = def.getExpiryWarning() != null ? def.getExpiryWarning().getDays() : 3;

            if (remainingDays <= warningDays) {
                String msg = def.getExpiryWarning() != null
                        ? ColorUtil.color(def.getExpiryWarning().getMessage())
                        : configManager.getMessage("expiry-warning");
                msg = ColorUtil.format(msg,
                        "card", ColorUtil.color(def.getDisplayName()),
                        "days", String.valueOf(remainingDays));
                player.sendMessage(msg);
            }
        }
    }

    /**
     * 賦予玩家 permission
     */
    public void grantPermission(Player player, String permission) {
        PermissionAttachment attachment = attachments.get(player.getUniqueId());
        if (attachment == null) {
            attachment = player.addAttachment(plugin);
            attachments.put(player.getUniqueId(), attachment);
        }
        attachment.setPermission(permission, true);
        player.recalculatePermissions();
    }

    /**
     * 撤銷玩家 permission
     */
    public void revokePermission(Player player, String permission) {
        PermissionAttachment attachment = attachments.get(player.getUniqueId());
        if (attachment != null) {
            attachment.unsetPermission(permission);
            player.recalculatePermissions();
        }
    }

    /**
     * 從 cache 取得玩家月卡（若不在 cache 返回空列表）
     */
    public List<PlayerCardData> getPlayerCardsFromCache(UUID uuid) {
        return playerCardsCache.getOrDefault(uuid, new ArrayList<>());
    }

    /**
     * 取得今天未簽到的有效卡列表
     */
    public List<PlayerCardData> getUnclaimedCards(UUID uuid, ZoneId tz) {
        return getPlayerCardsFromCache(uuid).stream()
                .filter(c -> !c.isExpired() && !c.isClaimedToday(tz))
                .collect(Collectors.toList());
    }

    /**
     * 計算需要幾個 inventory slot
     */
    public int countRequiredSlots(List<RewardItem> items) {
        int slots = 0;
        for (RewardItem item : items) {
            Material mat = Material.getMaterial(item.getMaterial() != null ? item.getMaterial().toUpperCase() : "STONE");
            int maxStack = mat != null ? mat.getMaxStackSize() : 64;
            if (maxStack <= 0) maxStack = 64;
            slots += (int) Math.ceil((double) item.getAmount() / maxStack);
        }
        return slots;
    }

    /**
     * 執行指令（以 Console 身份，替換 {player}）
     */
    public void executeCommand(String command, Player player) {
        String cmd = command.replace("{player}", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    /**
     * 格式化時間戳為日期字串
     */
    public String formatDate(long timestamp, ZoneId tz) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(configManager.getDateFormat());
        return Instant.ofEpochMilli(timestamp)
                .atZone(tz)
                .toLocalDate()
                .format(formatter);
    }

    /**
     * 非同步從 DB 取得玩家月卡（給 command 等使用）
     */
    public java.util.concurrent.CompletableFuture<List<PlayerCardData>> getPlayerCards(UUID uuid) {
        List<PlayerCardData> cached = playerCardsCache.get(uuid);
        if (cached != null) {
            return java.util.concurrent.CompletableFuture.completedFuture(new ArrayList<>(cached));
        }
        return database.getPlayerCards(uuid);
    }

    /**
     * 玩家購買月卡（Vault 扣款已在指令層完成，此方法只負責給卡與訊息）
     * 與 giveCard 不同：發送購買專屬訊息，不發送管理員格式的 give-success
     */
    public void buyCard(Player player, String cardId, int days) {
        CardDefinition def = configManager.getCard(cardId);
        if (def == null) return;

        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        database.getPlayerCard(uuid, cardId).thenAccept(optCard -> {
            long now = System.currentTimeMillis();
            boolean isNew = !optCard.isPresent();
            PlayerCardData data;

            if (isNew) {
                data = new PlayerCardData();
                data.setPlayerUuid(uuid);
                data.setPlayerName(playerName);
                data.setCardId(cardId);
                data.setExpiryDate(now + (long) days * 86400_000L);
                data.setActivatedAt(now);
                data.setLastClaimDate(null);
            } else {
                data = optCard.get();
                data.setPlayerName(playerName);
                long currentExpiry = data.getExpiryDate();
                long base = Math.max(currentExpiry, now);
                data.setExpiryDate(base + (long) days * 86400_000L);
            }

            final boolean wasNew = isNew;
            final PlayerCardData finalData = data;

            database.savePlayerCard(data).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    // 更新 cache
                    List<PlayerCardData> cache = playerCardsCache.computeIfAbsent(uuid, k -> new ArrayList<>());
                    cache.removeIf(c -> c.getCardId().equals(cardId));
                    cache.add(finalData);

                    // 賦予權限
                    grantPermission(player, def.getPermission());

                    // 新卡才執行 on-activate
                    if (wasNew) {
                        for (String cmd : def.getOnActivateCommands()) {
                            executeCommand(cmd, player);
                        }
                    }

                    // 更新飛行狀態
                    if (flyManager != null) flyManager.checkFly(player);

                    // 發送購買成功訊息（已在指令層透過 buy-success 發送，這裡不重複）
                    // 只發 receive-card 讓玩家知道有效期
                    ZoneId tz = configManager.getTimezone();
                    String expireStr = formatDate(finalData.getExpiryDate(), tz);
                    player.sendMessage(configManager.getMessage("receive-card",
                            "card", ColorUtil.color(def.getDisplayName()),
                            "expire", expireStr));
                });
            });
        }).exceptionally(e -> {
            plugin.getLogger().severe("buyCard 失敗：" + e.getMessage());
            return null;
        });
    }

    /**
     * 取得玩家所有有效月卡中最高的 EXP 倍率。
     * 若沒有任何月卡啟用 exp-boost，回傳 1.0（無加成）。
     */
    public double getBestExpMultiplier(Player player) {
        List<PlayerCardData> cards = playerCardsCache.get(player.getUniqueId());
        if (cards == null || cards.isEmpty()) return 1.0;

        double best = 1.0;
        for (PlayerCardData data : cards) {
            CardDefinition def = configManager.getCard(data.getCardId());
            if (def != null && def.isExpBoostEnabled()) {
                double m = def.getExpBoostMultiplier();
                if (m > best) best = m;
            }
        }
        return best;
    }

    public Database getDatabase() {
        return database;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
