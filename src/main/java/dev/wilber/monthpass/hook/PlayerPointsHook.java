package dev.wilber.monthpass.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * PlayerPoints 反射橋接器。
 * 不依賴編譯時 PlayerPoints API，透過反射呼叫，相容所有版本。
 * 使用的方法：PlayerPoints#getAPI()、PlayerPointsAPI#look(UUID)、PlayerPointsAPI#take(UUID, int)
 */
public class PlayerPointsHook {

    private final Logger logger;
    private Object api;          // PlayerPointsAPI 實例
    private Method lookMethod;   // look(UUID) → int
    private Method takeMethod;   // take(UUID, int) → boolean

    public PlayerPointsHook(Logger logger) {
        this.logger = logger;
    }

    /**
     * 嘗試連接 PlayerPoints，回傳是否成功。
     */
    public boolean hook() {
        try {
            Plugin pp = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (pp == null) return false;

            // 取得 PlayerPoints#getAPI() 方法
            Method getAPI = pp.getClass().getMethod("getAPI");
            api = getAPI.invoke(pp);
            if (api == null) return false;

            Class<?> apiClass = api.getClass();
            lookMethod = apiClass.getMethod("look", UUID.class);
            takeMethod = apiClass.getMethod("take", UUID.class, int.class);

            return true;
        } catch (Exception e) {
            logger.warning("PlayerPoints 掛載失敗：" + e.getMessage());
            api = null;
            return false;
        }
    }

    /** PlayerPoints 是否可用 */
    public boolean isAvailable() {
        return api != null;
    }

    /**
     * 查詢玩家積分
     * @return 積分數量，失敗時返回 0
     */
    public int look(UUID uuid) {
        if (!isAvailable()) return 0;
        try {
            Object result = lookMethod.invoke(api, uuid);
            return result instanceof Number ? ((Number) result).intValue() : 0;
        } catch (Exception e) {
            logger.warning("PlayerPoints look() 呼叫失敗：" + e.getMessage());
            return 0;
        }
    }

    /**
     * 扣除玩家積分
     * @return 是否成功
     */
    public boolean take(UUID uuid, int amount) {
        if (!isAvailable()) return false;
        try {
            Object result = takeMethod.invoke(api, uuid, amount);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            logger.warning("PlayerPoints take() 呼叫失敗：" + e.getMessage());
            return false;
        }
    }
}
