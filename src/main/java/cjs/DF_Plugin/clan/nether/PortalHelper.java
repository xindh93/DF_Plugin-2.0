package cjs.DF_Plugin.clan.nether;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Orientable;

public class PortalHelper {

    /**
     * 지정된 위치 주변에서 안전한 포탈을 찾거나 생성합니다.
     * @param center 중심 위치
     * @param searchRadius 포탈을 검색할 반경
     * @return 안전한 텔레포트 위치
     */
    public static Location findOrCreateSafePortal(Location center, int searchRadius) {
        // 1. 주변에 기존 포탈이 있는지 검색
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (block.getType() == Material.NETHER_PORTAL) {
                        // 포탈 블록을 찾으면, 안전한 중앙 위치를 반환
                        return block.getLocation().add(0.5, 0, 0.5);
                    }
                }
            }
        }

        // 2. 기존 포탈이 없으면 안전한 장소를 찾아 새로 생성
        Location safeLocation = findSafePortalLocation(center);
        buildPortal(safeLocation);
        return safeLocation.clone().add(1.5, 0.5, 0.5); // 포탈 내부의 중앙
    }

    /**
     * 포탈을 생성할 안전한 평지를 찾습니다.
     * @param center 검색 시작 위치
     * @return 포탈을 생성할 안전한 위치 (프레임의 왼쪽 아래)
     */
    private static Location findSafePortalLocation(Location center) {
        World world = center.getWorld();
        int startX = center.getBlockX();
        int startZ = center.getBlockZ();

        for (int r = 0; r < 16; r++) { // 반경을 점차 늘려가며 탐색
            for (int i = -r; i <= r; i++) {
                for (int j = -r; j <= r; j++) {
                    if (Math.abs(i) != r && Math.abs(j) != r) continue; // 가장자리만 탐색

                    Location checkLoc = world.getHighestBlockAt(startX + i, startZ + j).getLocation();
                    if (isSafeForPortal(checkLoc)) {
                        return checkLoc.add(0, 1, 0); // 블록 위
                    }
                }
            }
        }
        // 안전한 위치를 못 찾으면 원래 위치 위에 생성
        return center.getWorld().getHighestBlockAt(center).getLocation().add(0, 1, 0);
    }

    /**
     * 해당 위치가 포탈을 짓기에 안전한지 확인합니다. (평평한지)
     */
    private static boolean isSafeForPortal(Location loc) {
        // 땅이 단단하고, 위로 5칸, 옆으로 4칸의 공간이 있는지 확인
        if (!loc.getBlock().getType().isSolid()) return false;
        for (int y = 1; y <= 5; y++) {
            for (int x = 0; x < 4; x++) {
                if (loc.clone().add(x, y, 0).getBlock().getType() != Material.AIR) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 지정된 위치에 Z축 방향으로 네더 포탈을 생성합니다.
     * @param loc 포탈 프레임의 남서쪽(x, z가 가장 작은) 아래 코너
     */
    private static void buildPortal(Location loc) {
        // 바닥 프레임
        loc.clone().add(1, -1, 0).getBlock().setType(Material.OBSIDIAN);
        loc.clone().add(2, -1, 0).getBlock().setType(Material.OBSIDIAN);
        // 왼쪽 기둥
        loc.clone().add(0, 0, 0).getBlock().setType(Material.OBSIDIAN);
        loc.clone().add(0, 1, 0).getBlock().setType(Material.OBSIDIAN);
        loc.clone().add(0, 2, 0).getBlock().setType(Material.OBSIDIAN);
        // 오른쪽 기둥
        loc.clone().add(3, 0, 0).getBlock().setType(Material.OBSIDIAN);
        loc.clone().add(3, 1, 0).getBlock().setType(Material.OBSIDIAN);
        loc.clone().add(3, 2, 0).getBlock().setType(Material.OBSIDIAN);
        // 상단 프레임
        loc.clone().add(1, 3, 0).getBlock().setType(Material.OBSIDIAN);
        loc.clone().add(2, 3, 0).getBlock().setType(Material.OBSIDIAN);

        // 포탈 블록 생성 (점화)
        for (int y = 0; y < 3; y++) {
            for (int x = 1; x < 3; x++) {
                Block portalBlock = loc.clone().add(x, y, 0).getBlock();
                portalBlock.setType(Material.NETHER_PORTAL);
                Orientable portalData = (Orientable) portalBlock.getBlockData();
                portalData.setAxis(org.bukkit.Axis.X); // Z축 방향 포탈이므로 X축으로 설정
                portalBlock.setBlockData(portalData);
            }
        }
    }
}