package cjs.DF_Plugin.events.supplydrop;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class SupplyDropScheduler implements Listener {

    private final DF_Main plugin;
    private final File eventDataFile;
    private final FileConfiguration eventDataConfig;

    private enum State { COOLDOWN, EVENT_ACTIVE }
    private State currentState;
    private long cooldownStartTime;

    public SupplyDropScheduler(DF_Main plugin) {
        this.plugin = plugin;
        this.eventDataFile = new File(plugin.getDataFolder(), "event_data.yml");
        this.eventDataConfig = YamlConfiguration.loadConfiguration(eventDataFile);
        loadState();
        startDayCheckTask();
    }

    private void loadState() {
        if (!eventDataFile.exists()) {
            plugin.getLogger().info("[SupplyDrop] Event data file not found. Creating a new one.");
            startNewCooldown(); // 새로운 쿨다운 시작 및 파일 생성
            return;
        }

        this.currentState = State.valueOf(eventDataConfig.getString("supply-drop.state", "COOLDOWN"));
        this.cooldownStartTime = eventDataConfig.getLong("supply-drop.cooldown-start-time", 0);

        if (this.cooldownStartTime == 0) {
            this.cooldownStartTime = System.currentTimeMillis();
            plugin.getLogger().info("[SupplyDrop] Cooldown start time not found. Resetting to now.");
        }

        // 서버가 이벤트 도중에 종료된 경우를 처리
        if (this.currentState == State.EVENT_ACTIVE) {
            long eventStartTime = this.cooldownStartTime;
            long eventDuration = getEventDurationMillis();
            if (System.currentTimeMillis() > eventStartTime + eventDuration) {
                plugin.getLogger().warning("[SupplyDrop] Server was likely stopped during an event. The event is now considered finished.");
                startNewCooldown();
            } else {
                plugin.getLogger().info("[SupplyDrop] An event is currently active. Resuming...");
                long remainingDuration = (eventStartTime + eventDuration) - System.currentTimeMillis();
                scheduleCooldownReset(remainingDuration / 50); // 남은 시간만큼 쿨다운 재설정 예약
            }
        } else {
            plugin.getLogger().info("[SupplyDrop] Loaded event state: " + currentState);
        }
    }

    private void saveState() {
        eventDataConfig.set("supply-drop.state", currentState.name());
        eventDataConfig.set("supply-drop.cooldown-start-time", cooldownStartTime);
        try {
            eventDataConfig.save(eventDataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save event_data.yml!", e);
        }
    }

    private void startDayCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getGameConfigManager().getConfig().getBoolean("events.supply-drop.enabled", true)) return;
                if (!plugin.getGameStartManager().isGameStarted()) return;
                if (currentState == State.EVENT_ACTIVE) return;

                long cooldownHours = plugin.getGameConfigManager().getConfig().getLong("events.supply-drop.cooldown-hours", 8);
                long cooldownMillis = TimeUnit.HOURS.toMillis(cooldownHours);
                long timeSinceCooldownStart = System.currentTimeMillis() - cooldownStartTime;

                if (timeSinceCooldownStart >= cooldownMillis) {
                    startEventNow();
                }
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60); // 1분마다 확인
    }

    public void startEventNow() {
        if (!plugin.getGameConfigManager().getConfig().getBoolean("events.supply-drop.enabled", true)) {
            plugin.getLogger().info("[SupplyDrop] 이벤트가 config.yml에서 비활성화되어 있어 시작할 수 없습니다.");
            return;
        }
        if (currentState == State.EVENT_ACTIVE) {
            plugin.getLogger().warning("[SupplyDrop] An event is already active. Cannot start a new one.");
            return;
        }

        plugin.getLogger().info("[SupplyDrop] Triggering supply drop event.");
        plugin.getSupplyDropManager().triggerEvent();

        this.currentState = State.EVENT_ACTIVE;
        this.cooldownStartTime = System.currentTimeMillis(); // 이벤트 시작 시간 기록
        saveState();

        long eventDurationTicks = getEventDurationMillis() / 50;
        scheduleCooldownReset(eventDurationTicks);
    }

    private void scheduleCooldownReset(long delayTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentState == State.EVENT_ACTIVE) {
                    plugin.getLogger().info("[SupplyDrop] Event has finished.");
                    startNewCooldown();
                }
            }
        }.runTaskLater(plugin, delayTicks);
    }

    private void startNewCooldown() {
        this.currentState = State.COOLDOWN;
        this.cooldownStartTime = System.currentTimeMillis();
        saveState();
        long cooldownHours = plugin.getGameConfigManager().getConfig().getLong("events.supply-drop.cooldown-hours", 8);
        plugin.getLogger().info("[SupplyDrop] 새로운 " + cooldownHours + "시간 쿨다운이 시작되었습니다.");
    }

    private long getEventDurationMillis() {
        FileConfiguration config = plugin.getGameConfigManager().getConfig();
        long spawnDelay = TimeUnit.HOURS.toMillis(config.getLong("events.supply-drop.spawn-delay-hours", 0));
        long breakDuration = TimeUnit.SECONDS.toMillis(config.getLong("events.supply-drop.break-duration-seconds", 8));
        long cleanupDelay = TimeUnit.MINUTES.toMillis(config.getLong("events.supply-drop.cleanup-delay-minutes", 1));
        return spawnDelay + breakDuration + cleanupDelay;
    }
}