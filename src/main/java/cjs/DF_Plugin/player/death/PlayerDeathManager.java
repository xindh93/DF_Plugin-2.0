package cjs.DF_Plugin.player.death;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * 플레이어의 사망 및 밴 상태를 관리하는 클래스
 */
public class PlayerDeathManager implements Listener {
    private final DF_Main plugin;
    private final File deathsFile;
    private final FileConfiguration deathsConfig;
    private final Map<UUID, Long> deadPlayers = new ConcurrentHashMap<>(); // Player UUID -> Death Timestamp

    public PlayerDeathManager(DF_Main plugin) {
        this.plugin = plugin;
        File playersFolder = new File(plugin.getDataFolder(), "players");
        if (!playersFolder.exists()) {
            playersFolder.mkdirs();
        }
        this.deathsFile = new File(playersFolder, "deaths.yml");
        if (!deathsFile.exists()) {
            try {
                deathsFile.createNewFile();
                plugin.getLogger().info("Created a new deaths.yml file.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create deaths.yml!", e);
            }
        }
        this.deathsConfig = YamlConfiguration.loadConfiguration(deathsFile);
        loadDeaths();
    }

    private void loadDeaths() {
        if (deathsConfig.isConfigurationSection("deaths")) {
            deathsConfig.getConfigurationSection("deaths").getKeys(false).forEach(uuidString -> {
                try {
                    deadPlayers.put(UUID.fromString(uuidString), deathsConfig.getLong("deaths." + uuidString));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID found in deaths.yml: " + uuidString);
                }
            });
            plugin.getLogger().info("Loaded " + deadPlayers.size() + " death ban entries.");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        final boolean deathBanEnabled = plugin.getGameConfigManager().isDeathTimerEnabled();
        if (!deathBanEnabled) return;

        Player player = event.getEntity();
        // config.yml의 death-timer.time은 분 단위로 저장됩니다.
        final int banDurationMinutes = plugin.getGameConfigManager().getDeathTimerDurationMinutes();
        deadPlayers.put(player.getUniqueId(), System.currentTimeMillis());

        // formatDuration은 분 단위를 받습니다.
        String durationString = formatDuration(banDurationMinutes);
        player.kickPlayer("§c사망하여 " + durationString + " 동안 서버에 접속할 수 없습니다.");
    }

    @EventHandler
    public void onPlayerLogin(AsyncPlayerPreLoginEvent event) {
        final UUID playerUUID = event.getUniqueId();
        if (!deadPlayers.containsKey(playerUUID)) return;
        
        long remainingMillis = getRemainingBanMillis(playerUUID);
        if (remainingMillis > 0) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, getKickMessage(remainingMillis));
        } else {
            // Ban time is over, allow login and remove from list
            resurrectPlayer(playerUUID);
        }
    }

    public void resurrectPlayer(UUID playerUUID) {
        deadPlayers.remove(playerUUID);
        // 변경 사항은 메모리에서만 관리하고, 파일 저장은 서버 종료 시 한 번에 처리합니다.
    }

    /**
     * 플레이어가 현재 사망 밴 상태인지 확인합니다.
     * @param playerUUID 확인할 플레이어의 UUID
     * @return 밴 상태이면 true, 아니면 false
     */
    public boolean isDeathBanned(UUID playerUUID) {
        return deadPlayers.containsKey(playerUUID);
    }

    public Map<UUID, Long> getDeadPlayers() {
        return deadPlayers;
    }

    /**
     * 서버 종료 시 호출되어 모든 사망자 데이터를 파일에 저장합니다.
     */
    public void saveOnDisable() {
        plugin.getLogger().info("Saving " + deadPlayers.size() + " death ban entries...");
        // 먼저 기존 데이터를 모두 지웁니다.
        deathsConfig.set("deaths", null);
        // 현재 메모리에 있는 데이터로 덮어씁니다.
        deadPlayers.forEach((uuid, timestamp) -> deathsConfig.set("deaths." + uuid.toString(), timestamp));

        try {
            deathsConfig.save(deathsFile);
            plugin.getLogger().info("Death ban entries saved successfully.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save deaths.yml!", e);
        }
    }

    private long getRemainingBanMillis(UUID playerUUID) {
        // config.yml의 death-timer.time은 분 단위로 저장됩니다.
        final long deathTime = deadPlayers.get(playerUUID);
        final int banDurationMinutes = plugin.getGameConfigManager().getDeathTimerDurationMinutes();
        final long banEndTime = deathTime + TimeUnit.MINUTES.toMillis(banDurationMinutes);
        return banEndTime - System.currentTimeMillis();
    }

    private String getKickMessage(long remainingMillis) {
        String remainingTime = String.format("%02d시간 %02d분 %02d초",
                TimeUnit.MILLISECONDS.toHours(remainingMillis),
                TimeUnit.MILLISECONDS.toMinutes(remainingMillis) % 60,
                TimeUnit.MILLISECONDS.toSeconds(remainingMillis) % 60
        );
        return "§c사망으로 인해 추방되었습니다.\n§e남은 시간: " + remainingTime;
    }

    private String formatDuration(int totalMinutes) {
        if (totalMinutes <= 0) {
            return "잠시";
        }
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("시간 ");
        }
        if (minutes > 0 || hours == 0) {
            sb.append(minutes).append("분");
        }
        return sb.toString().trim();
    }
}