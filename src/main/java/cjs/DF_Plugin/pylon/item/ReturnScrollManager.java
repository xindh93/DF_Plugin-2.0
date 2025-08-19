package cjs.DF_Plugin.pylon.item;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ReturnScrollManager {

    private final DF_Main plugin;
    private final Map<UUID, BukkitTask> castingPlayers = new HashMap<>();

    public ReturnScrollManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void startCasting(Player player) {
        if (castingPlayers.containsKey(player.getUniqueId())) {
            player.sendMessage("§c이미 귀환을 시도하고 있습니다.");
            return;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null || clan.getPylonLocations().isEmpty()) {
            player.sendMessage("§c귀환할 파일런이 없습니다.");
            return;
        }

        World.Environment environment = player.getWorld().getEnvironment();
        if (environment == World.Environment.NETHER && !plugin.getGameConfigManager().isReturnScrollAllowedInNether()) {
            player.sendMessage("§c이곳에서는 귀환 주문서를 사용할 수 없습니다.");
            return;
        }
        if (environment == World.Environment.THE_END && !plugin.getGameConfigManager().isReturnScrollAllowedInEnd()) {
            player.sendMessage("§c이곳에서는 귀환 주문서를 사용할 수 없습니다.");
            return;
        }

        int castTime = plugin.getGameConfigManager().getReturnScrollCastTime();
        player.sendMessage("§b" + castTime + "초 후 파일런으로 귀환합니다... (피격 시 취소)");
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);

        final Location startLocation = player.getLocation();

        // 시전 완료 시 실행될 로직
        Runnable onComplete = () -> {
            if (castingPlayers.containsKey(player.getUniqueId())) { // 시전이 취소되지 않았는지 확인
                castingPlayers.remove(player.getUniqueId());
                if (player.isOnline()) {
                    teleportToPylonArea(player, clan);
                    consumeReturnScroll(player);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                    player.sendMessage("§b파일런 영역으로 귀환했습니다.");
                }
            }
        };

        // 새로운 파티클 효과와 함께 시전 시작
        BukkitTask task = playCastEffect(player, castTime, startLocation, onComplete);

        castingPlayers.put(player.getUniqueId(), task);
    }

    public void cancelCasting(Player player, boolean showMessage) {
        if (castingPlayers.containsKey(player.getUniqueId())) {
            castingPlayers.get(player.getUniqueId()).cancel();
            castingPlayers.remove(player.getUniqueId());
            if (showMessage) {
                player.sendMessage("§c귀환이 취소되었습니다.");
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.0f);
            }
        }
    }

    public boolean isCasting(Player player) {
        return castingPlayers.containsKey(player.getUniqueId());
    }

    private void teleportToPylonArea(Player player, Clan clan) {
        List<String> pylonLocations = new ArrayList<>(clan.getPylonLocations().keySet());
        Collections.shuffle(pylonLocations);
        Location pylonCenter = PluginUtils.deserializeLocation(pylonLocations.get(0));

        if (pylonCenter == null) {
            player.sendMessage("§c귀환 위치를 찾는데 실패했습니다. 스폰 지역으로 이동합니다.");
            player.teleport(player.getWorld().getSpawnLocation());
            return;
        }

        // 리스폰 리스너와 동일한 로직을 사용하여 안전한 귀환 위치를 찾습니다.
        Location safeLoc = findSafeTeleportLocation(pylonCenter);
        player.teleport(safeLoc);
    }

    /**
     * 파일런 주변에서 플레이어가 안전하게 텔레포트될 수 있는 위치를 찾습니다.
     * 지하, 액체, 배리어 블록을 피해 지상의 안전한 공간을 찾습니다.
     * @param pylonCenter 파일런(신호기)의 중앙 위치
     * @return 안전한 텔레포트 위치
     */
    private Location findSafeTeleportLocation(Location pylonCenter) {
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
            if (groundBlock.isLiquid() || groundBlock.getType() == Material.BARRIER || !groundBlock.getType().isSolid()) {
                continue;
            }

            Block feetBlock = groundBlock.getRelative(BlockFace.UP);
            Block headBlock = feetBlock.getRelative(BlockFace.UP);

            if (feetBlock.isPassable() && headBlock.isPassable()) {
                Location spawnLocation = feetBlock.getLocation().add(0.5, 0, 0.5);
                // 플레이어의 시야 방향을 파일런 중심으로 설정
                org.bukkit.util.Vector direction = pylonCenter.toVector().subtract(spawnLocation.toVector()).normalize();
                spawnLocation.setDirection(direction);
                return spawnLocation;
            }
        }

        // 최후의 수단: 파일런 바로 위 (리스폰과 동일한 문제 발생 가능성 있음)
        return pylonCenter.getWorld().getHighestBlockAt(pylonCenter).getLocation().add(0.5, 1, 0.5);
    }

    private BukkitTask playCastEffect(Player player, int castTime, Location startLocation, Runnable onComplete) {
        return new BukkitRunnable() {
            int ticks = 0;
            final double radius = 1.2;
            double yOffset = 0.2;

            @Override
            public void run() {
                // 플레이어가 오프라인이거나, 움직였거나, 아이템을 바꿨으면 시전 취소
                if (!player.isOnline() ||
                        startLocation.getBlockX() != player.getLocation().getBlockX() ||
                        startLocation.getBlockY() != player.getLocation().getBlockY() ||
                        startLocation.getBlockZ() != player.getLocation().getBlockZ() ||
                        !PylonItemFactory.isReturnScroll(player.getInventory().getItemInMainHand())) {

                    cancelCasting(player, true); // 메시지와 함께 취소
                    return;
                }

                if (ticks >= castTime * 20) {
                    onComplete.run();
                    this.cancel();
                    return;
                }

                // 나선형 파티클 효과
                Location playerLoc = player.getLocation();
                for (int i = 0; i < 3; i++) { // 3개의 나선
                    double angle = (ticks * 10 + (i * 120)) * (Math.PI / 180);
                    double x = radius * Math.cos(angle);
                    double z = radius * Math.sin(angle);
                    player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, playerLoc.clone().add(x, yOffset, z), 1, 0, 0, 0, 0);
                }
                yOffset += 0.08; // 파티클이 위로 올라가는 속도

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 플레이어의 인벤토리에서 귀환 주문서를 찾아 1개 소모합니다.
     * @param player 대상 플레이어
     */
    private void consumeReturnScroll(Player player) {
        // 시전이 완료되었으므로, 주 손에 있는 아이템을 소모합니다.
        // 시전 중 계속 손에 들고 있었음이 보장됩니다.
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (PylonItemFactory.isReturnScroll(mainHand)) {
            mainHand.setAmount(mainHand.getAmount() - 1);
        }
    }
}