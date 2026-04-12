package dev.wilber.monthpass.config;

import dev.wilber.monthpass.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RewardItem {

    private String material;
    private int amount = 1;
    private String name;
    private List<String> lore = new ArrayList<>();
    private int customModelData = 0;
    private Map<String, Integer> enchantments = new HashMap<>();
    private List<String> itemFlags = new ArrayList<>();

    public RewardItem() {}

    /**
     * 從 ConfigurationSection 建立 RewardItem
     */
    public static RewardItem fromConfig(ConfigurationSection section) {
        RewardItem item = new RewardItem();
        if (section == null) return item;
        item.material = section.getString("material", "STONE").toUpperCase();
        item.amount = section.getInt("amount", 1);
        item.name = section.getString("name", null);
        item.lore = section.getStringList("lore");
        item.customModelData = section.getInt("custom-model-data", 0);

        // 讀取附魔
        ConfigurationSection enchSec = section.getConfigurationSection("enchantments");
        if (enchSec != null) {
            for (String key : enchSec.getKeys(false)) {
                item.enchantments.put(key.toUpperCase(), enchSec.getInt(key));
            }
        }

        item.itemFlags = section.getStringList("item-flags");
        return item;
    }

    /**
     * 從 Map（getMapList 解析結果）建立 RewardItem
     */
    @SuppressWarnings("unchecked")
    public static RewardItem fromMap(Map<?, ?> map) {
        RewardItem item = new RewardItem();
        if (map == null) return item;

        if (map.containsKey("material")) {
            item.material = String.valueOf(map.get("material")).toUpperCase();
        } else {
            item.material = "STONE";
        }

        if (map.containsKey("amount")) {
            try { item.amount = Integer.parseInt(String.valueOf(map.get("amount"))); }
            catch (NumberFormatException ignored) {}
        }

        if (map.containsKey("name")) {
            Object nameObj = map.get("name");
            item.name = nameObj != null ? String.valueOf(nameObj) : null;
        }

        if (map.containsKey("lore")) {
            Object loreObj = map.get("lore");
            if (loreObj instanceof List) {
                for (Object l : (List<?>) loreObj) {
                    item.lore.add(String.valueOf(l));
                }
            }
        }

        if (map.containsKey("custom-model-data")) {
            try { item.customModelData = Integer.parseInt(String.valueOf(map.get("custom-model-data"))); }
            catch (NumberFormatException ignored) {}
        }

        if (map.containsKey("enchantments")) {
            Object enchObj = map.get("enchantments");
            if (enchObj instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) enchObj).entrySet()) {
                    try {
                        item.enchantments.put(String.valueOf(entry.getKey()).toUpperCase(),
                                Integer.parseInt(String.valueOf(entry.getValue())));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        if (map.containsKey("item-flags")) {
            Object flagsObj = map.get("item-flags");
            if (flagsObj instanceof List) {
                for (Object f : (List<?>) flagsObj) {
                    item.itemFlags.add(String.valueOf(f));
                }
            }
        }

        return item;
    }

    public ItemStack toItemStack() {
        return toItemStack(null, null);
    }

    public ItemStack toItemStack(String playerName, String date) {
        Material mat = material != null ? Material.getMaterial(material) : null;
        if (mat == null) {
            mat = Material.STONE;
        }
        ItemStack stack = new ItemStack(mat, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                String displayName = name;
                if (playerName != null) displayName = displayName.replace("{player}", playerName);
                if (date != null) displayName = displayName.replace("{date}", date);
                meta.setDisplayName(ColorUtil.color(displayName));
            }
            if (!lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    String l = line;
                    if (playerName != null) l = l.replace("{player}", playerName);
                    if (date != null) l = l.replace("{date}", date);
                    coloredLore.add(ColorUtil.color(l));
                }
                meta.setLore(coloredLore);
            }
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                Enchantment ench = Enchantment.getByName(entry.getKey());
                if (ench != null) {
                    meta.addEnchant(ench, entry.getValue(), true);
                }
            }
            for (String flag : itemFlags) {
                try {
                    ItemFlag itemFlag = ItemFlag.valueOf(flag.toUpperCase());
                    meta.addItemFlags(itemFlag);
                } catch (IllegalArgumentException ignored) {
                    // 忽略無效的 ItemFlag
                }
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getLore() { return lore; }
    public void setLore(List<String> lore) { this.lore = lore; }

    public int getCustomModelData() { return customModelData; }
    public void setCustomModelData(int customModelData) { this.customModelData = customModelData; }

    public Map<String, Integer> getEnchantments() { return enchantments; }
    public void setEnchantments(Map<String, Integer> enchantments) { this.enchantments = enchantments; }

    public List<String> getItemFlags() { return itemFlags; }
    public void setItemFlags(List<String> itemFlags) { this.itemFlags = itemFlags; }
}
