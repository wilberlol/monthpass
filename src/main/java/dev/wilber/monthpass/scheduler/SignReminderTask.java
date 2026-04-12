package dev.wilber.monthpass.scheduler;

import dev.wilber.monthpass.MonthPass;
import dev.wilber.monthpass.config.ConfigManager;
import dev.wilber.monthpass.database.PlayerCardData;
import dev.wilber.monthpass.manager.CardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.ZoneId;
import java.util.List;

public class SignReminderTask extends BukkitRunnable {

    private static final String CLICKABLE_TAG = "[點此簽到]";

    private final MonthPass plugin;
    private final CardManager cardManager;
    private final ConfigManager configManager;

    public SignReminderTask(MonthPass plugin, CardManager cardManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.cardManager = cardManager;
        this.configManager = configManager;
    }

    @Override
    public void run() {
        if (!configManager.isSignReminderEnabled()) return;

        ZoneId tz = configManager.getTimezone();

        for (Player player : Bukkit.getOnlinePlayers()) {
            List<PlayerCardData> unclaimed = cardManager.getUnclaimedCards(player.getUniqueId(), tz);
            if (!unclaimed.isEmpty()) {
                sendClickableReminder(player);
            }
        }
    }

    private void sendClickableReminder(Player player) {
        String rawMessage = configManager.getSignReminderMessage();

        // 找出 [點此簽到] 標記的位置
        int tagIndex = rawMessage.indexOf(CLICKABLE_TAG);

        Component message;
        if (tagIndex >= 0) {
            // 分割成前半段和後半段
            String beforeTag = rawMessage.substring(0, tagIndex);
            String afterTag = rawMessage.substring(tagIndex + CLICKABLE_TAG.length());

            // 前半段（普通文字，含顏色代碼）
            Component before = beforeTag.isEmpty()
                    ? Component.empty()
                    : LegacyComponentSerializer.legacyAmpersand().deserialize(beforeTag);

            // 可點擊的 [點此簽到] 按鈕
            Component clickable = Component.text(CLICKABLE_TAG)
                    .color(NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.runCommand("/monthpass sign"))
                    .hoverEvent(HoverEvent.showText(Component.text("點擊領取月卡每日獎勵")));

            // 後半段
            Component after = afterTag.isEmpty()
                    ? Component.empty()
                    : LegacyComponentSerializer.legacyAmpersand().deserialize(afterTag);

            message = before.append(clickable).append(after);
        } else {
            // 沒有找到標記，整段作為文字，末尾加上可點擊按鈕
            Component text = LegacyComponentSerializer.legacyAmpersand().deserialize(rawMessage);
            Component clickable = Component.text(CLICKABLE_TAG)
                    .color(NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.runCommand("/monthpass sign"))
                    .hoverEvent(HoverEvent.showText(Component.text("點擊領取月卡每日獎勵")));
            message = text.append(clickable);
        }

        player.sendMessage(message);
    }
}
