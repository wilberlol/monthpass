package dev.wilber.monthpass.database;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Database {

    /**
     * 初始化資料庫（建立連線池、建立資料表）
     */
    void init() throws Exception;

    /**
     * 關閉資料庫連線池
     */
    void close();

    /**
     * 取得玩家所有月卡資料
     */
    CompletableFuture<List<PlayerCardData>> getPlayerCards(UUID playerUuid);

    /**
     * 取得玩家指定月卡資料
     */
    CompletableFuture<Optional<PlayerCardData>> getPlayerCard(UUID playerUuid, String cardId);

    /**
     * 儲存（新增或更新）月卡資料
     */
    CompletableFuture<Void> savePlayerCard(PlayerCardData data);

    /**
     * 移除玩家月卡資料
     */
    CompletableFuture<Void> removePlayerCard(UUID playerUuid, String cardId);

    /**
     * 更新最後簽到日期
     */
    CompletableFuture<Void> updateLastClaimDate(UUID playerUuid, String cardId, LocalDate date);

    /**
     * 更新到期時間
     */
    CompletableFuture<Void> updateExpiryDate(UUID playerUuid, String cardId, long expiryDate);

    /**
     * 取得持有指定月卡的所有玩家
     */
    CompletableFuture<List<PlayerCardData>> getCardHolders(String cardId);

    /**
     * 取得所有已過期的月卡
     */
    CompletableFuture<List<PlayerCardData>> getAllExpiredCards();
}
