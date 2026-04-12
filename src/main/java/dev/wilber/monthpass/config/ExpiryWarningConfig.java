package dev.wilber.monthpass.config;

import org.bukkit.configuration.ConfigurationSection;

public class ExpiryWarningConfig {

    private int days = 3;
    private String message = "&c你的 {card} 還剩 {days} 天到期！";

    public static ExpiryWarningConfig fromConfig(ConfigurationSection section) {
        ExpiryWarningConfig config = new ExpiryWarningConfig();
        if (section == null) return config;
        config.days = section.getInt("days", 3);
        config.message = section.getString("message", "&c你的 {card} 還剩 {days} 天到期！");
        return config;
    }

    public int getDays() {
        return days;
    }

    public String getMessage() {
        return message;
    }
}
