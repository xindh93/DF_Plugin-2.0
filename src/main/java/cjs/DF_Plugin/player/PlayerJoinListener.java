package cjs.DF_Plugin.player;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.clan.ClanManager;
import cjs.DF_Plugin.data.PlayerDataManager;
import cjs.DF_Plugin.player.stats.StatsManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {
    private final DF_Main plugin;

    public PlayerJoinListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 서버 입장 메시지를 제거합니다.
        event.setJoinMessage(null);

        Player player = event.getPlayer();
        ClanManager clanManager = plugin.getClanManager();
        StatsManager statsManager = plugin.getStatsManager();
        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();

        // 게임이 시작된 경우, 접속한 플레이어의 상태를 처리합니다.
        if (plugin.getGameStartManager().isGameStarted()) {
            Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());

            if (playerClan == null) {
                // 가문이 없는 플레이어는 관전 모드로 전환
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("§e게임이 진행 중이며 소속된 가문이 없어 관전 모드로 접속합니다.");
            } else {
                player.setGameMode(GameMode.SURVIVAL);

                // 가문 가입 후 첫 접속(신규 영입)인지 확인합니다.
                if (playerClan.isPendingFirstSpawn(player.getUniqueId())) {
                    // 1. 신규 영입된 멤버는 파일런 근처로 소환합니다.
                    plugin.getPylonManager().getAreaManager().findSafeSpawnInPylonArea(playerClan).ifPresentOrElse(
                            safeLoc -> {
                                player.teleport(safeLoc);
                                player.sendMessage("§a가문에 새로 합류하여 파일런 근처로 소환되었습니다.");
                                // 첫 스폰 처리가 끝났으므로 플래그를 제거하고 저장합니다.
                                playerClan.removePendingFirstSpawn(player.getUniqueId());
                                clanManager.saveClanData(playerClan);
                            },
                            () -> {
                                // 소환에 실패했으므로, 플래그를 제거하지 않고 다음 접속 시 다시 시도하도록 합니다.
                                player.sendMessage("§c소속 가문의 파일런을 찾을 수 없어 다음 접속 시 다시 시도합니다.");
                            }
                    );
                } else if (!playerDataManager.hasInitialTeleportDone(player.getUniqueId())) { // 게임 시작 또는 가문 생성 후 첫 접속
                    // 2. 게임 시작 후 첫 접속이거나, 가문 생성 후 첫 접속인 경우 시작 지점으로 이동합니다.
                    Location startLoc = playerClan.getStartLocation();
                    if (startLoc != null) {
                        player.teleport(startLoc);
                        player.sendMessage("§a게임이 시작되어 시작 지점으로 이동합니다.");
                    } else {
                        player.sendMessage("§c가문의 시작 지점을 찾을 수 없습니다. 관리자에게 문의하세요.");
                    }
                    playerDataManager.setInitialTeleportDone(player.getUniqueId(), true);
                } else {
                    // 3. 이후 재접속 시, 가문의 파일런이 없으면(회수된 경우 등) 시작 지점으로 이동합니다.
                    boolean hasPylon = playerClan.getMainPylonLocationObject().isPresent();
                    if (!hasPylon) {
                        Location startLoc = playerClan.getStartLocation();
                        if (startLoc != null) {
                            player.teleport(startLoc);
                            player.sendMessage("§e소속 가문의 파일런이 없어 임시 시작 지점으로 이동되었습니다.");
                        } else {
                            player.sendMessage("§c소속 가문의 임시 시작 지점을 찾을 수 없습니다. 관리자에게 문의하세요.");
                        }
                    }
                    // 파일런이 있다면, 아무 행동도 하지 않고 마지막 위치에서 접속합니다.
                }
            }
        }

        if (clanManager != null) {
            clanManager.getPlayerTagManager().updatePlayerTag(player);
        }

        // 플레이어의 스탯이 등록되어 있는지 확인하고, 없으면 기본값으로 자동 등록합니다.
        if (statsManager != null && !statsManager.hasStats(player.getUniqueId())) {
            statsManager.getPlayerStats(player.getUniqueId()); // 캐시에 기본 스탯 생성
            statsManager.savePlayerStats(player.getUniqueId()); // 파일에 저장
            player.sendMessage("§a기본 스탯으로 등록되었습니다.");
        }

        if (plugin.getRiftManager() != null) {
            plugin.getRiftManager().showBarToPlayer(player);
        }
    }
}