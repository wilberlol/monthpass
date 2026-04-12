package dev.wilber.monthpass.placeholder;

import dev.wilber.monthpass.MonthPass;
import dev.wilber.monthpass.config.CardDefinition;
import dev.wilber.monthpass.config.ConfigManager;
import dev.wilber.monthpass.database.PlayerCardData;
import dev.wilber.monthpass.manager.CardManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class MonthPassExpansion extends PlaceholderExpansion {

    private final MonthPass plugin;
    private final CardManager cardManager;
    private final ConfigManager configManager;

    public MonthPassExpansion(MonthPass plugin, CardManager cardManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cardManager = cardManager;
        this.configManager = configManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "monthpass";
    }

    @Override
    public @NotNull String getAuthor() {
        return "wilber";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    /**
     * 解析佔位符
     * 格式：%monthpass_{cardId}_{type}%
     * params 是 {cardId}_{type} 部分
     */
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        // 嘗試所有已知 card ID，找出哪個是 params 的前綴
        Set<String> cardIds = configManager.getCards().keySet();
        String matchedCardId = null;
        String type = null;

        for (String cardId : cardIds) {
            String prefix = cardId + "_";
            if (params.startsWith(prefix) && params.length() > prefix.length()) {
                matchedCardId = cardId;
                type = params.substring(prefix.length());
                break;
            }
        }

        if (matchedCardId == null || type == null) {
            return "";
        }

        // 從 cache 或直接查找
        final String finalCardId = matchedCardId;
        List<PlayerCardData> cards = cardManager.getPlayerCardsFromCache(player.getUniqueId());
        Optional<PlayerCardData> optCard = cards.stream()
                .filter(c -> c.getCardId().equals(finalCardId) && !c.isExpired())
                .findFirst();

        ZoneId tz = configManager.getTimezone();
        CardDefinition def = configManager.getCard(matchedCardId);

        switch (type.toLowerCase()) {
            case "active":
                return optCard.isPresent() ? "true" : "false";

            case "days":
                if (!optCard.isPresent()) return "0";
                return String.valueOf(optCard.get().getRemainingDays(tz));

            case "expire":
                if (!optCard.isPresent()) return "無";
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(configManager.getDateFormat());
                return Instant.ofEpochMilli(optCard.get().getExpiryDate())
                        .atZone(tz)
                        .toLocalDate()
                        .format(formatter);

            case "claimed":
                if (!optCard.isPresent()) return "false";
                return optCard.get().isClaimedToday(tz) ? "true" : "false";

            default:
                return "";
        }
    }
}
