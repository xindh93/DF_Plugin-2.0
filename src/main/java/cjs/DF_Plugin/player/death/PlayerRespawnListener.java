package cjs.DF_Plugin.player.death;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.events.game.GameStartManager;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Random;

public class PlayerRespawnListener implements Listener {

    private final DF_Main plugin;
    private final GameStartManager gameStartManager;

    public PlayerRespawnListener(DF_Main plugin) {
        this.plugin = plugin;
        this.gameStartManager = plugin.getGameStartManager();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());

        if (clan == null) {
            return; // 가문이 없으면 기본 부활 로직 따름
        }

        if (gameStartManager.isGameStarted()) {
            // 게임 중일 때
            if (clan.getPylonLocations().isEmpty()) {
                // 파일런이 없으면 임시 부활 지점으로
                Location tempRespawn = clan.getStartLocation();
                if (tempRespawn != null) {
                    event.setRespawnLocation(tempRespawn);
                    player.sendMessage("§e[부활] §e설치된 파일런이 없어 임시 지점에서 부활합니다.");
                }
            } else {
                // 파일런이 있으면 파일런에서 부활
                setRespawnAtPylon(event, clan);
            }
        } else if (!clan.getPylonLocations().isEmpty()) {
            // 게임 중이 아닐 때 파일런이 있으면 파일런에서 부활
            setRespawnAtPylon(event, clan);
        }
    }

    private void setRespawnAtPylon(PlayerRespawnEvent event, Clan clan) {
        String locString = clan.getPylonLocations().keySet().iterator().next();
        Location pylonLoc = PluginUtils.deserializeLocation(locString);
        if (pylonLoc != null && pylonLoc.getWorld() != null) {
            // 파일런 주변의 안전한 리스폰 위치를 찾습니다.
            Location safeRespawnLoc = findSafeRespawnLocation(pylonLoc);
            event.setRespawnLocation(safeRespawnLoc);
        }
    }

    /**
     * 파일런 주변에서 플레이어가 안전하게 스폰될 수 있는 위치를 찾습니다.
     * 지하, 액체, 배리어 블록을 피해 지상의 안전한 공간을 찾습니다.
     * @param pylonCenter 파일런(신호기)의 중앙 위치
     * @return 안전한 리스폰 위치
     */
    private Location findSafeRespawnLocation(Location pylonCenter) {
        int radius = plugin.getGameConfigManager().getConfig().getInt("pylon.area-effects.radius", 30);
        Random random = new Random();
        World world = pylonCenter.getWorld();

        // 안전한 위치를 찾기 위해 일정 횟수 시도합니다.
        for (int i = 0; i < 100; i++) { // 100번의 무작위 시도
            int xOffset = random.nextInt(radius * 2 + 1) - radius;
            int zOffset = random.nextInt(radius * 2 + 1) - radius;

            // 원형 범위 체크
            if (xOffset * xOffset + zOffset * zOffset > radius * radius) {
                continue;
            }

            int checkX = pylonCenter.getBlockX() + xOffset;
            int checkZ = pylonCenter.getBlockZ() + zOffset;

            // 해당 X, Z 좌표의 가장 높은 블록을 찾습니다 (지하 제외).
            Block groundBlock = world.getHighestBlockAt(checkX, checkZ);

            // 안전성 검사
            // 1. 바닥이 액체, 배리어, 또는 통과 불가능한 블록이 아니어야 합니다.
            if (groundBlock.isLiquid() || groundBlock.getType() == Material.BARRIER || !groundBlock.getType().isSolid()) {
                continue;
            }

            // 2. 스폰될 위치(발, 머리)에 충분한 공간이 있어야 합니다.
            Block feetBlock = groundBlock.getRelative(BlockFace.UP);
            Block headBlock = feetBlock.getRelative(BlockFace.UP);

            if (feetBlock.isPassable() && headBlock.isPassable()) {
                // 안전한 위치를 찾았으므로, 블록 중앙으로 위치를 조정하여 반환합니다.
                Location spawnLocation = feetBlock.getLocation().add(0.5, 0, 0.5);
                // 플레이어의 시야 방향을 파일런 중심으로 설정
                Vector direction = pylonCenter.toVector().subtract(spawnLocation.toVector()).normalize();
                spawnLocation.setDirection(direction);
                return spawnLocation;
            }
        }

        // 무작위 탐색 실패 시, 기존의 4방향 탐색 로직을 최후의 수단으로 사용합니다.
        Vector[] fallbackOffsets = {
                new Vector(2, 0, 0), new Vector(-2, 0, 0),
                new Vector(0, 0, 2), new Vector(0, 0, -2)
        };

        for (Vector offset : fallbackOffsets) {
            Location checkLoc = pylonCenter.clone().add(offset);
            Block groundBlock = world.getHighestBlockAt(checkLoc);

            if (groundBlock.getType().isSolid() && !groundBlock.isLiquid() && groundBlock.getType() != Material.BARRIER) {
                Block feetBlock = groundBlock.getRelative(BlockFace.UP);
                Block headBlock = feetBlock.getRelative(BlockFace.UP);
                if (feetBlock.isPassable() && headBlock.isPassable()) {
                    Location spawnLocation = feetBlock.getLocation().add(0.5, 0, 0.5);
                    Vector direction = pylonCenter.toVector().subtract(spawnLocation.toVector()).normalize();
                    spawnLocation.setDirection(direction);
                    return spawnLocation;
                }
            }
        }

        // 최후의 최후의 수단: 파일런 바로 위 (기존 문제의 원인이지만, 다른 방법이 없을 때 사용)
        return pylonCenter.getWorld().getHighestBlockAt(pylonCenter).getLocation().add(0.5, 1, 0.5);
    }
}