package dev.wilber.monthpass.manager;

import dev.wilber.monthpass.MonthPass;
import dev.wilber.monthpass.util.ColorUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 連續簽到：資料存在玩家 PDC（簽到必在線，不需動 DB）。
 * 漏簽時自動消耗補簽卡（每漏一天一張，最多 max-makeup-gap 天）接上連簽，補簽不重發每日獎勵。
 */
public class StreakManager {

    private final MonthPass plugin;
    private final CardManager cards;
    private final NamespacedKey kStreak, kLast, kMakeup;

    public StreakManager(MonthPass plugin, CardManager cards) {
        this.plugin = plugin;
        this.cards = cards;
        kStreak = new NamespacedKey(plugin, "streak");
        kLast = new NamespacedKey(plugin, "streak_last");
        kMakeup = new NamespacedKey(plugin, "makeup");
    }

    public int streak(Player p) {
        return p.getPersistentDataContainer().getOrDefault(kStreak, PersistentDataType.INTEGER, 0);
    }

    public int makeup(Player p) {
        return p.getPersistentDataContainer().getOrDefault(kMakeup, PersistentDataType.INTEGER, 0);
    }

    /** 下一個里程碑天數；沒有則回傳 0 */
    public int nextMilestone(int streak) {
        ConfigurationSection ms = plugin.getConfig().getConfigurationSection("streak.milestones");
        int best = 0;
        if (ms != null) {
            for (String k : ms.getKeys(false)) {
                try {
                    int d = Integer.parseInt(k);
                    if (d > streak && (best == 0 || d < best)) best = d;
                } catch (NumberFormatException ignored) { }
            }
        }
        return best;
    }

    /** 例：再簽 3 天可領 7 天獎勵 */
    public String nextInfo(Player p) {
        int s = streak(p), n = nextMilestone(s);
        return n == 0 ? "已達最高里程碑" : "再簽 " + (n - s) + " 天可領 " + n + " 天獎勵";
    }

    public void addMakeup(Player p, int n) {
        p.getPersistentDataContainer().set(kMakeup, PersistentDataType.INTEGER, Math.max(0, makeup(p) + n));
    }

    /** 簽到成功後呼叫；同一天重複呼叫不會重複計算 */
    public void onSign(Player p, LocalDate today) {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("streak");
        if (cfg == null || !cfg.getBoolean("enable", false)) return;

        PersistentDataContainer pdc = p.getPersistentDataContainer();
        String lastStr = pdc.get(kLast, PersistentDataType.STRING);
        LocalDate last = lastStr == null ? null : LocalDate.parse(lastStr);
        if (today.equals(last)) return;

        int streak = streak(p);
        if (last == null) {
            streak = 1;
        } else {
            long gap = ChronoUnit.DAYS.between(last, today) - 1;
            int tokens = makeup(p);
            if (gap <= 0) {
                streak++;
            } else if (gap <= cfg.getInt("max-makeup-gap", 3) && tokens >= gap) {
                addMakeup(p, (int) -gap);
                streak += gap + 1;
                p.sendMessage(ColorUtil.color("&8[&6月卡&8] &e漏簽 " + gap + " 天，已自動使用 " + gap + " 張補簽卡保住連簽！"));
            } else {
                if (streak > 1) p.sendMessage(ColorUtil.color("&8[&6月卡&8] &c連續簽到中斷（原 " + streak + " 天），重新累計"));
                streak = 1;
            }
        }
        pdc.set(kLast, PersistentDataType.STRING, today.toString());

        p.sendMessage(ColorUtil.color("&8[&6月卡&8] &a連續簽到 &e" + streak + " &a天"));
        ConfigurationSection ms = cfg.getConfigurationSection("milestones");
        ConfigurationSection m = ms == null ? null : ms.getConfigurationSection(String.valueOf(streak));
        if (m != null) {
            String msg = m.getString("message");
            if (msg != null) p.sendMessage(ColorUtil.color(msg.replace("{days}", String.valueOf(streak))));
            for (String c : m.getStringList("commands")) cards.executeCommand(c, p);
        }
        int cycle = cfg.getInt("cycle", 0);
        if (cycle > 0 && streak >= cycle) {
            streak = 0; // 下一天 gap=0 → 回到 1，里程碑可重複領
            p.sendMessage(ColorUtil.color("&8[&6月卡&8] &d已完成 " + cycle + " 天連簽！新一輪開始"));
        }
        pdc.set(kStreak, PersistentDataType.INTEGER, streak);
        if (streak > 0) p.sendMessage(ColorUtil.color("&8[&6月卡&8] &7" + nextInfo(p)));
    }
}
