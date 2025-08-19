package cjs.DF_Plugin.pylon.beaconinteraction.registration;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.pylon.PylonType;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class BeaconRegistrationManager {
    private final DF_Main plugin;
    private static final String PREFIX = PluginUtils.colorize("&b[파일런] &f");

    public BeaconRegistrationManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void registerPylon(Player player, Block beacon, Clan clan) {
        // 1. 구조물 설치
        plugin.getPylonManager().getStructureManager().placeBaseAndBarrier(beacon.getLocation(), PylonType.MAIN_CORE);

        // 2. 파일런 정보 등록
        String locationString = PluginUtils.serializeLocation(beacon.getLocation());
        clan.addPylonLocation(locationString, PylonType.MAIN_CORE);
        plugin.getClanManager().saveClanData(clan);

        // 재설치 타이머가 있다면 취소
        plugin.getPylonManager().getReinstallManager().cancelReinstallTimer(player);

        // 3. 영역 보호 활성화
        plugin.getPylonManager().getAreaManager().addProtectedPylon(beacon.getLocation(), clan);

        player.sendMessage(PREFIX + "파일런 코어가 성공적으로 설치 및 활성화되었습니다!");
    }
}