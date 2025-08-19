package cjs.DF_Plugin.events.game;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameStartManager {

    private final DF_Main plugin;
    private boolean isGameStarted = false;
    private final Map<UUID, Location> temporaryRespawnPoints = new ConcurrentHashMap<>(); // Clan Leader UUID -> Location
    private final Random random = new Random();

    public GameStartManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void startGame() {
        if (isGameStarted) {
            Bukkit.broadcastMessage("§c이미 게임이 시작되었습니다.");
            return;
        }
        isGameStarted = true;
        temporaryRespawnPoints.clear();

        // 모든 가문 대표를 랜덤 위치로 텔레포트하고 임시 부활 지점 설정
        for (Clan clan : plugin.getClanManager().getAllClans()) {
            Player leader = Bukkit.getPlayer(clan.getLeader());
            if (leader != null && leader.isOnline()) {
                Location randomLoc = getRandomSafeLocation(leader.getWorld());
                leader.teleport(randomLoc);
                temporaryRespawnPoints.put(clan.getLeader(), randomLoc);
                leader.sendMessage("§a게임이 시작되었습니다! 이곳이 임시 부활 지점입니다.");
            }
        }

        // 대표가 아닌 모든 플레이어를 관전 모드로 전환
        for (Player player : Bukkit.getOnlinePlayers()) {
            Clan playerClan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
            if (playerClan == null || !playerClan.getLeader().equals(player.getUniqueId())) {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("§e게임이 시작되어 관전 모드로 전환됩니다.");
            }
        }
        Bukkit.broadcastMessage("§6[알림] §e게임이 시작되었습니다!");
    }

    public void stopGame() {
        if (!isGameStarted) {
            Bukkit.broadcastMessage("§c시작된 게임이 없습니다.");
            return;
        }
        isGameStarted = false;
        temporaryRespawnPoints.clear();

        // 모든 플레이어를 서바이벌 모드로 되돌리고 스폰으로 이동
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
                player.teleport(player.getWorld().getSpawnLocation());
            }
        }
        Bukkit.broadcastMessage("§6[알림] §e게임이 종료되었습니다!");
    }

    public boolean isGameStarted() {
        return isGameStarted;
    }

    public Location getTemporaryRespawnPoint(UUID leaderUuid) {
        return temporaryRespawnPoints.get(leaderUuid);
    }

    private Location getRandomSafeLocation(World world) {
        double borderSize = plugin.getGameConfigManager().getConfig().getDouble("world.border.overworld-size", 20000);
        double radius = (borderSize / 2.0) * 0.9; // 경계선 바로 근처는 피하도록 90% 반경으로 설정

        double angle = random.nextDouble() * 2 * Math.PI;
        double r = radius * Math.sqrt(random.nextDouble());
        int x = (int) (r * Math.cos(angle));
        int z = (int) (r * Math.sin(angle));
        int y = world.getHighestBlockYAt(x, z) + 1;

        return new Location(world, x + 0.5, y, z + 0.5);
    }
}