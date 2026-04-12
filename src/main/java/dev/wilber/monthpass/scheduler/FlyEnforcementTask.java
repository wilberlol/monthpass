package dev.wilber.monthpass.scheduler;

import dev.wilber.monthpass.manager.FlyManager;
import org.bukkit.scheduler.BukkitRunnable;

public class FlyEnforcementTask extends BukkitRunnable {

    private final FlyManager flyManager;

    public FlyEnforcementTask(FlyManager flyManager) {
        this.flyManager = flyManager;
    }

    @Override
    public void run() {
        flyManager.enforceAll();
    }
}
