package cjs.DF_Plugin.pylon.reinstall;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PylonReinstallManager {
    private final DF_Main plugin;
    private final Map<UUID, BukkitTask> reinstallTimers = new HashMap<>();

    public PylonReinstallManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void startReinstallTimer(Player player) {
        // TODO: 파일런 재설치 타이머 로직 구현
    }

    public void cancelReinstallTimer(Player player) {
        BukkitTask existingTask = reinstallTimers.remove(player.getUniqueId());
        if (existingTask != null) {
            existingTask.cancel();
        }
    }
}