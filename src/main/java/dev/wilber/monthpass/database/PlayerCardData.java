package dev.wilber.monthpass.database;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerCardData {

    private UUID playerUuid;
    private String playerName;
    private String cardId;
    private long expiryDate;        // Unix ms timestamp
    private LocalDate lastClaimDate; // null = 從未簽到
    private long activatedAt;

    public PlayerCardData() {}

    public PlayerCardData(UUID playerUuid, String playerName, String cardId,
                          long expiryDate, LocalDate lastClaimDate, long activatedAt) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.cardId = cardId;
        this.expiryDate = expiryDate;
        this.lastClaimDate = lastClaimDate;
        this.activatedAt = activatedAt;
    }

    /**
     * 計算剩餘天數（至少 1 天，使用 ceil）
     */
    public long getRemainingDays(ZoneId timezone) {
        long now = System.currentTimeMillis();
        if (expiryDate <= now) return 0;
        return (long) Math.ceil((expiryDate - now) / 86400000.0);
    }

    /**
     * 是否已過期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiryDate;
    }

    /**
     * 今天是否已簽到
     */
    public boolean isClaimedToday(ZoneId timezone) {
        if (lastClaimDate == null) return false;
        LocalDate today = LocalDate.now(timezone);
        return lastClaimDate.equals(today);
    }

    // Getters and Setters

    public UUID getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(UUID playerUuid) { this.playerUuid = playerUuid; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }

    public long getExpiryDate() { return expiryDate; }
    public void setExpiryDate(long expiryDate) { this.expiryDate = expiryDate; }

    public LocalDate getLastClaimDate() { return lastClaimDate; }
    public void setLastClaimDate(LocalDate lastClaimDate) { this.lastClaimDate = lastClaimDate; }

    public long getActivatedAt() { return activatedAt; }
    public void setActivatedAt(long activatedAt) { this.activatedAt = activatedAt; }
}
