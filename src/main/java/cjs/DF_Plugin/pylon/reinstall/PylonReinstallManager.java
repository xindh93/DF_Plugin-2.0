package cjs.DF_Plugin.pylon.reinstall;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import cjs.DF_Plugin.util.PluginUtils;

public class PylonReinstallManager {
    private final DF_Main plugin;
    private final Map<UUID, Long> reinstallCooldowns = new HashMap<>();
    private static final String PREFIX = PluginUtils.colorize("&b[파일런] &f");

    public PylonReinstallManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void startReinstallTimer(Player player) {
        long durationHours = plugin.getGameConfigManager().getConfig().getLong("pylon.retrieval.reinstall-duration-hours", 2);
        if (durationHours <= 0) {
            return;
        }
        long endTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(durationHours);
        reinstallCooldowns.put(player.getUniqueId(), endTime);
        player.sendMessage(PREFIX + "§c파일런을 회수하여 " + durationHours + "시간 동안 재설치할 수 없습니다.");
    }

    public void cancelReinstallTimer(Player player) {
        if (reinstallCooldowns.remove(player.getUniqueId()) != null) {
            player.sendMessage(PREFIX + "§a파일런 재설치 대기 시간이 해제되었습니다.");
        }
    }

    public long getRemainingCooldown(Player player) {
        if (!reinstallCooldowns.containsKey(player.getUniqueId())) {
            return 0;
        }
        long endTime = reinstallCooldowns.get(player.getUniqueId());
        long remaining = endTime - System.currentTimeMillis();
        if (remaining <= 0) {
            reinstallCooldowns.remove(player.getUniqueId());
            return 0;
        }
        return remaining;
    }
}