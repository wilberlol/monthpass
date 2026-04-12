package dev.wilber.monthpass.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class FlyConfig {

    private boolean enable = false;
    private List<String> worlds = new ArrayList<>();

    public static FlyConfig fromConfig(ConfigurationSection section) {
        FlyConfig config = new FlyConfig();
        if (section == null) return config;
        config.enable = section.getBoolean("enable", false);
        config.worlds = section.getStringList("worlds");
        return config;
    }

    public boolean isEnable() {
        return enable;
    }

    public List<String> getWorlds() {
        return worlds;
    }
}
