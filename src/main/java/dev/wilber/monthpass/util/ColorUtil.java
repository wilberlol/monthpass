package dev.wilber.monthpass.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ColorUtil {

    private ColorUtil() {}

    /**
     * 將 & 顏色代碼轉換為 § 顏色代碼
     */
    public static String color(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }

    /**
     * 將含 & 顏色代碼的字串轉換為 Adventure Component
     */
    public static Component toComponent(String text) {
        if (text == null) return Component.empty();
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    /**
     * 替換字串中的 {key} 佔位符
     * keyValues 格式：[key1, val1, key2, val2, ...]
     */
    public static String format(String template, String... keyValues) {
        if (template == null) return "";
        if (keyValues == null || keyValues.length == 0) return template;
        String result = template;
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String key = keyValues[i];
            String val = keyValues[i + 1];
            if (key != null && val != null) {
                result = result.replace("{" + key + "}", val);
            }
        }
        return result;
    }

    /**
     * 去除所有顏色代碼（§ 和 & 格式）
     */
    public static String stripColor(String text) {
        if (text == null) return "";
        return text.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }
}
