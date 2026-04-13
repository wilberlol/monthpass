package dev.wilber.monthpass.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CardDefinition {

    private String id;
    private String displayName;
    private String permission;
    private List<String> onActivateCommands = new ArrayList<>();
    private List<String> onExpireCommands = new ArrayList<>();
    private List<String> dailyCommands = new ArrayList<>();
    private List<RewardItem> dailyItems = new ArrayList<>();
    private FlyConfig fly;
    private ExpiryWarningConfig expiryWarning;
    private ShopConfig shop;
    private boolean expBoostEnable = false;
    private double expBoostMultiplier = 1.0;

    public static CardDefinition fromConfig(String id, ConfigurationSection section) {
        CardDefinition card = new CardDefinition();
        card.id = id;
        card.displayName = section.getString("display-name", id);
        card.permission = section.getString("permission", "monthpass." + id);

        // on-activate
        ConfigurationSection activateSec = section.getConfigurationSection("on-activate");
        if (activateSec != null) {
            card.onActivateCommands = activateSec.getStringList("commands");
        }

        // on-expire
        ConfigurationSection expireSec = section.getConfigurationSection("on-expire");
        if (expireSec != null) {
            card.onExpireCommands = expireSec.getStringList("commands");
        }

        // daily-reward
        ConfigurationSection dailySec = section.getConfigurationSection("daily-reward");
        if (dailySec != null) {
            card.dailyCommands = dailySec.getStringList("commands");

            // 使用 getMapList 解析物品列表（YAML list of maps）
            List<Map<?, ?>> itemMaps = dailySec.getMapList("items");
            for (Map<?, ?> map : itemMaps) {
                card.dailyItems.add(RewardItem.fromMap(map));
            }
        }

        // fly
        card.fly = FlyConfig.fromConfig(section.getConfigurationSection("fly"));

        // expiry-warning
        card.expiryWarning = ExpiryWarningConfig.fromConfig(section.getConfigurationSection("expiry-warning"));

        // shop
        card.shop = ShopConfig.fromConfig(section.getConfigurationSection("shop"));

        // exp-boost
        ConfigurationSection expBoostSec = section.getConfigurationSection("exp-boost");
        if (expBoostSec != null) {
            card.expBoostEnable = expBoostSec.getBoolean("enable", false);
            card.expBoostMultiplier = expBoostSec.getDouble("multiplier", 1.0);
            if (card.expBoostMultiplier < 1.0) card.expBoostMultiplier = 1.0; // 最低 1.0 倍
        }

        return card;
    }

    /**
     * 計算所有 dailyItems 需要的 inventory slots
     */
    public int calculateRequiredSlots() {
        int slots = 0;
        for (RewardItem item : dailyItems) {
            Material mat = item.getMaterial() != null
                    ? Material.getMaterial(item.getMaterial().toUpperCase())
                    : null;
            int maxStack = mat != null ? mat.getMaxStackSize() : 64;
            if (maxStack <= 0) maxStack = 64;
            slots += (int) Math.ceil((double) item.getAmount() / maxStack);
        }
        return slots;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getPermission() { return permission; }
    public List<String> getOnActivateCommands() { return onActivateCommands; }
    public List<String> getOnExpireCommands() { return onExpireCommands; }
    public List<String> getDailyCommands() { return dailyCommands; }
    public List<RewardItem> getDailyItems() { return dailyItems; }
    public FlyConfig getFly() { return fly; }
    public ExpiryWarningConfig getExpiryWarning() { return expiryWarning; }
    public ShopConfig getShop() { return shop; }
    public boolean isExpBoostEnabled() { return expBoostEnable; }
    public double getExpBoostMultiplier() { return expBoostMultiplier; }
}
