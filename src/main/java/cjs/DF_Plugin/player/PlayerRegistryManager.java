package cjs.DF_Plugin.player;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.data.PlayerDataManager;
import cjs.DF_Plugin.player.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 서버에 접속한 모든 플레이어를 기록하고 관리하는 클래스
 */
public class PlayerRegistryManager implements Listener {
    private final DF_Main plugin;
    private final Map<UUID, RegisteredPlayerData> allPlayers = new HashMap<>();

    public PlayerRegistryManager(DF_Main plugin) {
        this.plugin = plugin;
        loadPlayers();
    }

    private void loadPlayers() {
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        ConfigurationSection playersSection = pdm.getConfig().getConfigurationSection("players");
        if (playersSection != null) {
            for (String uuidString : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    String name = playersSection.getString(uuidString + ".name");
                    String clanName = playersSection.getString(uuidString + ".clan");
                    if (name != null) {
                        allPlayers.put(uuid, new RegisteredPlayerData(name, clanName));
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in playerdata.yml: " + uuidString);
                }
            }
        }

        // 서버에 접속한 적 있는 모든 플레이어를 확인하고, 등록되지 않은 경우 새로 등록합니다.
        boolean needsSave = false;
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            UUID uuid = offlinePlayer.getUniqueId();
            if (!allPlayers.containsKey(uuid)) {
                String name = offlinePlayer.getName();
                if (name != null) {
                    plugin.getLogger().info("기록에 없는 오프라인 플레이어를 발견하여 등록합니다: " + name);
                    allPlayers.put(uuid, new RegisteredPlayerData(name, null));
                    pdm.getPlayerSection(uuid).set("name", name);
                    pdm.getPlayerSection(uuid).set("clan", null);
                    needsSave = true;
                }
            }
        }

        if (needsSave) {
            pdm.saveConfig();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        StatsManager statsManager = plugin.getStatsManager();

        // 플레이어의 스탯 데이터가 없는 경우 (첫 접속) 기본 스탯을 설정합니다.
        if (!statsManager.hasStats(uuid)) {
            plugin.getLogger().info(player.getName() + "님이 처음 접속하여 기본 스탯을 설정합니다.");
            // getPlayerStats는 스탯이 없으면 기본값으로 생성하므로, 호출하는 것만으로도 캐시에 등록됩니다.
            statsManager.getPlayerStats(uuid);
        }

        updatePlayerClan(player.getUniqueId(), plugin.getClanManager().getClanByPlayer(player.getUniqueId()));
        plugin.getPlayerDataManager().setPlayerName(player); // 이름 변경 시 업데이트
    }

    /**
     * 플레이어의 클랜 정보를 players.yml에 업데이트합니다.
     * @param playerUUID 업데이트할 플레이어의 UUID
     * @param clan 소속된 클랜 (없으면 null)
     */
    public void updatePlayerClan(UUID playerUUID, Clan clan) {
        RegisteredPlayerData currentData = allPlayers.get(playerUUID);
        String playerName = Bukkit.getOfflinePlayer(playerUUID).getName();
        if (playerName == null && currentData != null) {
            playerName = currentData.getName();
        }
        if (playerName == null) return; // Cannot find player name.

        String clanName = (clan != null) ? clan.getName() : null;

        // 데이터가 변경되었을 경우에만 업데이트
        if (currentData == null || !currentData.getName().equals(playerName) || !Objects.equals(currentData.getClanName(), clanName)) {
            allPlayers.put(playerUUID, new RegisteredPlayerData(playerName, clanName));
            PlayerDataManager pdm = plugin.getPlayerDataManager();
            ConfigurationSection playerSection = pdm.getPlayerSection(playerUUID);
            playerSection.set("name", playerName);
            playerSection.set("clan", clanName);
            // No need to save immediately, will be saved on disable.
        }
    }

    /**
     * 모집 가능한 플레이어(클랜에 소속되지 않은) 목록을 가져옵니다.
     * @return 모집 가능한 플레이어 UUID 목록
     */
    public List<UUID> getRecruitablePlayerUUIDs() {
        return allPlayers.entrySet().stream()
                .filter(entry -> entry.getValue().getClanName() == null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<UUID> getAllPlayerUUIDs() {
        return new ArrayList<>(allPlayers.keySet());
    }

    /**
     * 플레이어 등록 정보를 담는 내부 클래스
     */
    private static class RegisteredPlayerData {
        private final String name;
        private final String clanName; // null if not in a clan

        public RegisteredPlayerData(String name, String clanName) {
            this.name = name;
            this.clanName = clanName;
        }

        public String getName() { return name; }

        public String getClanName() { return clanName; }
    }
}