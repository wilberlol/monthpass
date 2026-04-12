package dev.wilber.monthpass.config;

import org.bukkit.configuration.ConfigurationSection;

public class ShopConfig {

    /** 貨幣種類 */
    public enum CurrencyType {
        /** Vault 經濟（EssentialsX eco 等） */
        VAULT,
        /** PlayerPoints 積分 */
        POINTS;

        public static CurrencyType fromString(String s) {
            if (s != null && s.equalsIgnoreCase("points")) return POINTS;
            return VAULT; // 預設 vault
        }
    }

    private boolean enable = false;
    private CurrencyType currency = CurrencyType.VAULT;
    private double price = 0.0;
    private int days = 30;
    private String buyPermission = ""; // 空字串 = 所有玩家都能買

    public static ShopConfig fromConfig(ConfigurationSection section) {
        ShopConfig config = new ShopConfig();
        if (section == null) return config;
        config.enable       = section.getBoolean("enable", false);
        config.currency     = CurrencyType.fromString(section.getString("currency", "vault"));
        config.price        = section.getDouble("price", 0.0);
        config.days         = section.getInt("days", 30);
        config.buyPermission = section.getString("buy-permission", "");
        return config;
    }

    public boolean isEnable()            { return enable; }
    public CurrencyType getCurrency()    { return currency; }
    public double getPrice()             { return price; }
    public int getPriceAsInt()           { return (int) price; } // PlayerPoints 用 int
    public int getDays()                 { return days; }
    public String getBuyPermission()     { return buyPermission; }
}
