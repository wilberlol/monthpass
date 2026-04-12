package dev.wilber.monthpass.util;

import dev.wilber.monthpass.config.RewardItem;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;

public final class ItemBuilder {

    private ItemBuilder() {}

    /**
     * 從 RewardItem 建立 ItemStack
     */
    public static ItemStack build(RewardItem rewardItem) {
        return rewardItem.toItemStack();
    }

    /**
     * 從 RewardItem 建立 ItemStack，並替換玩家名稱和日期佔位符
     */
    public static ItemStack build(RewardItem rewardItem, String playerName, LocalDate date) {
        String dateStr = date != null ? date.toString() : "";
        return rewardItem.toItemStack(playerName, dateStr);
    }
}
