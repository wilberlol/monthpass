package dev.wilber.monthpass.config;

import dev.wilber.monthpass.MonthPass;
import dev.wilber.monthpass.util.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConfigManager {

    private final MonthPass plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private Map<String, CardDefinition> cards = new HashMap<>();

    public ConfigManager(MonthPass plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        // 載入 config.yml
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // 載入 messages.yml
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // 載入月卡定義
        cards.clear();
        org.bukkit.configuration.ConfigurationSection cardsSec = config.getConfigurationSection("cards");
        if (cardsSec != null) {
            Set<String> keys = cardsSec.getKeys(false);
            for (String key : keys) {
                org.bukkit.configuration.ConfigurationSection cardSec = cardsSec.getConfigurationSection(key);
                if (cardSec != null) {
                    cards.put(key, CardDefinition.fromConfig(key, cardSec));
                }
            }
        }

        plugin.getLogger().info("已載入 " + cards.size() + " 個月卡定義。");
    }

    /**
     * 取得訊息（加上 prefix）
     */
    public String getMessage(String key) {
        String prefix = ColorUtil.color(messages.getString("prefix", "&8[&6月卡&8] "));
        String msg = messages.getString(key, "&c找不到訊息：" + key);
        return prefix + ColorUtil.color(msg);
    }

    /**
     * 取得訊息並替換佔位符（加上 prefix）
     */
    public String getMessage(String key, String... keyValues) {
        String msg = getMessage(key);
        return ColorUtil.format(msg, keyValues);
    }

    /**
     * 取得訊息（不加 prefix）
     */
    public String getMessageNoPrefix(String key) {
        String msg = messages.getString(key, "&c找不到訊息：" + key);
        return ColorUtil.color(msg);
    }

    /**
     * 取得訊息並替換佔位符（不加 prefix）
     */
    public String getMessageNoPrefix(String key, String... keyValues) {
        String msg = getMessageNoPrefix(key);
        return ColorUtil.format(msg, keyValues);
    }

    /**
     * 取得原始訊息字串（未處理顏色）
     */
    public String getRawMessage(String key) {
        return messages.getString(key, "");
    }

    public Map<String, CardDefinition> getCards() {
        return cards;
    }

    public CardDefinition getCard(String cardId) {
        return cards.get(cardId);
    }

    public boolean cardExists(String cardId) {
        return cards.containsKey(cardId);
    }

    public ZoneId getTimezone() {
        String tz = config.getString("timezone", "Asia/Taipei");
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("Asia/Taipei");
        }
    }

    public String getDateFormat() {
        return config.getString("date-format", "yyyy/MM/dd");
    }

    public boolean isSignReminderEnabled() {
        return config.getBoolean("sign-reminder.enable", true);
    }

    public long getSignReminderInterval() {
        return config.getLong("sign-reminder.interval", 60L);
    }

    public String getSignReminderMessage() {
        return config.getString("sign-reminder.message", "&e你有月卡獎勵尚未領取！&6[點此簽到]");
    }

    public boolean isFlyModuleEnabled() {
        return config.getBoolean("fly.enable", true);
    }

    /**
     * 飛行強制補正的間隔（ticks）。
     * 預設 40 ticks = 2 秒。建議範圍 20-100。
     */
    public long getFlyEnforceInterval() {
        long val = config.getLong("fly.enforce-interval-ticks", 40L);
        return Math.max(10L, val); // 最小 10 ticks，防止設太短拖慢伺服器
    }

    /** PlayerPoints 積分的顯示單位，預設「點」 */
    public String getPointsUnit() {
        return config.getString("points-display-unit", "點");
    }

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite").toLowerCase();
    }

    public String getSQLiteFile() {
        return config.getString("database.sqlite.file", "plugins/MonthPass/data.db");
    }

    public String getH2File() {
        return config.getString("database.h2.file", "plugins/MonthPass/data");
    }

    public String getMySQLHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getMySQLPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getMySQLDatabase() {
        return config.getString("database.mysql.database", "monthpass");
    }

    public String getMySQLUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getMySQLPassword() {
        return config.getString("database.mysql.password", "");
    }

    public int getMySQLPoolSize() {
        return config.getInt("database.mysql.pool-size", 10);
    }

    public long getMySQLConnectionTimeout() {
        return config.getLong("database.mysql.connection-timeout", 30000L);
    }

    public FileConfiguration getRawConfig() {
        return config;
    }

    public FileConfiguration getRawMessages() {
        return messages;
    }

    /**
     * 取得插件實例（供 Scheduler 使用）
     */
    public MonthPass plugin() {
        return plugin;
    }
}
