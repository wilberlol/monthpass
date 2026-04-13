package dev.wilber.monthpass;

import dev.wilber.monthpass.command.MonthPassCommand;
import dev.wilber.monthpass.config.ConfigManager;
import dev.wilber.monthpass.database.Database;
import dev.wilber.monthpass.database.SQLiteDatabase;
import dev.wilber.monthpass.listener.PlayerExpChangeListener;
import dev.wilber.monthpass.listener.PlayerJoinListener;
import dev.wilber.monthpass.listener.PlayerQuitListener;
import dev.wilber.monthpass.listener.PlayerWorldChangeListener;
import dev.wilber.monthpass.manager.CardManager;
import dev.wilber.monthpass.manager.FlyManager;
import dev.wilber.monthpass.placeholder.MonthPassExpansion;
import dev.wilber.monthpass.scheduler.ExpiryCheckTask;
import dev.wilber.monthpass.scheduler.FlyEnforcementTask;
import dev.wilber.monthpass.scheduler.SignReminderTask;
import dev.wilber.monthpass.hook.PlayerPointsHook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class MonthPass extends JavaPlugin {

    private static MonthPass instance;

    private ConfigManager configManager;
    private Database database;
    private CardManager cardManager;
    private FlyManager flyManager;
    private MonthPassExpansion papiExpansion;
    private ExpiryCheckTask expiryTask;
    private SignReminderTask reminderTask;
    private FlyEnforcementTask flyEnforcementTask;
    private Economy economy;                   // Vault Economy，可能為 null
    private PlayerPointsHook playerPointsHook; // PlayerPoints 反射橋接器

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // 初始化 ConfigManager
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        // 初始化資料庫
        try {
            database = createDatabase();
            database.init();
        } catch (Exception e) {
            getLogger().severe("資料庫初始化失敗：" + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化管理員
        cardManager = new CardManager(this, database, configManager);
        flyManager = new FlyManager(this, cardManager, configManager);
        cardManager.setFlyManager(flyManager);

        // 載入所有已在線玩家的月卡（熱加載情境）
        for (Player player : Bukkit.getOnlinePlayers()) {
            cardManager.loadPlayerCards(player);
        }

        // 註冊 Listeners
        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(cardManager, flyManager, configManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerWorldChangeListener(this, flyManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerQuitListener(cardManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerExpChangeListener(cardManager), this);

        // 註冊 Command
        MonthPassCommand cmd = new MonthPassCommand(this, cardManager, flyManager, configManager);
        PluginCommand pluginCommand = getCommand("monthpass");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(cmd);
            pluginCommand.setTabCompleter(cmd);
        }

        // 啟動定時任務
        startTasks();

        // 註冊 PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiExpansion = new MonthPassExpansion(this, cardManager, configManager);
            papiExpansion.register();
            getLogger().info("已與 PlaceholderAPI 整合。");
        }

        // 連接 Vault Economy
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp =
                    getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                getLogger().info("已與 Vault Economy 整合（" + economy.getName() + "）。");
            } else {
                getLogger().warning("找到 Vault 但未找到經濟插件，currency: vault 的月卡將無法購買。");
            }
        } else {
            getLogger().info("未找到 Vault，currency: vault 的月卡將無法購買。");
        }

        // 連接 PlayerPoints（反射方式，相容所有版本）
        playerPointsHook = new PlayerPointsHook(getLogger());
        if (playerPointsHook.hook()) {
            getLogger().info("已與 PlayerPoints 整合。");
        } else {
            getLogger().info("未找到 PlayerPoints，currency: points 的月卡將無法購買。");
        }

        getLogger().info("MonthPass 已啟動！");
    }

    @Override
    public void onDisable() {
        // 取消定時任務
        if (expiryTask != null) expiryTask.cancel();
        if (reminderTask != null) reminderTask.cancel();
        if (flyEnforcementTask != null) flyEnforcementTask.cancel();

        // 取消所有 Bukkit 任務
        getServer().getScheduler().cancelTasks(this);

        // 反註冊 PAPI
        if (papiExpansion != null) {
            papiExpansion.unregister();
            papiExpansion = null;
        }

        // 清理所有玩家 cache
        if (cardManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                cardManager.unloadPlayerCards(player.getUniqueId());
            }
        }

        // 關閉資料庫
        if (database != null) {
            database.close();
        }

        getLogger().info("MonthPass 已停止。");
    }

    /**
     * 重載插件設定（不需要重啟伺服器）
     */
    public void reload() {
        // 取消任務
        if (expiryTask != null) { expiryTask.cancel(); expiryTask = null; }
        if (reminderTask != null) { reminderTask.cancel(); reminderTask = null; }
        if (flyEnforcementTask != null) { flyEnforcementTask.cancel(); flyEnforcementTask = null; }

        // 取消 Bukkit 任務
        getServer().getScheduler().cancelTasks(this);

        // 清除所有玩家 cache 和 attachment
        for (Player player : Bukkit.getOnlinePlayers()) {
            cardManager.unloadPlayerCards(player.getUniqueId());
        }

        // 反註冊 PAPI
        if (papiExpansion != null) {
            papiExpansion.unregister();
            papiExpansion = null;
        }

        // 重載 config
        reloadConfig();
        configManager.loadConfig();

        // 重新載入所有玩家月卡
        for (Player player : Bukkit.getOnlinePlayers()) {
            cardManager.loadPlayerCards(player);
        }

        // 重啟任務
        startTasks();

        // 重新註冊 PAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiExpansion = new MonthPassExpansion(this, cardManager, configManager);
            papiExpansion.register();
        }
    }

    private void startTasks() {
        // 到期檢查任務（非同步，每 60 秒）
        expiryTask = new ExpiryCheckTask(this, cardManager);
        expiryTask.runTaskTimerAsynchronously(this, 0L, 20L * 60);

        // 簽到提醒任務（同步，依設定間隔）
        long reminderInterval = configManager.getSignReminderInterval();
        reminderTask = new SignReminderTask(this, cardManager, configManager);
        reminderTask.runTaskTimer(this, 0L, 20L * reminderInterval);

        // 飛行強制補正任務
        if (configManager.isFlyModuleEnabled()) {
            long enforceInterval = configManager.getFlyEnforceInterval();
            flyEnforcementTask = new FlyEnforcementTask(flyManager);
            flyEnforcementTask.runTaskTimer(this, 40L, enforceInterval);
            getLogger().info("飛行補正任務已啟動（每 " + enforceInterval + " ticks）。");
        }
    }

    private Database createDatabase() {
        return new SQLiteDatabase(this);
    }

    public static MonthPass getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Database getDatabase() {
        return database;
    }

    public CardManager getCardManager() {
        return cardManager;
    }

    public FlyManager getFlyManager() {
        return flyManager;
    }

    /** 返回 Vault Economy，若 Vault 未安裝或無經濟插件則返回 null */
    public Economy getEconomy() {
        return economy;
    }

    /** 返回 PlayerPoints 橋接器（永遠不為 null，但 isAvailable() 可能為 false） */
    public PlayerPointsHook getPlayerPointsHook() {
        return playerPointsHook;
    }
}
