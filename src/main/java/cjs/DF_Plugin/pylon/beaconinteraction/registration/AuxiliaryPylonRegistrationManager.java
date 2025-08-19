package cjs.DF_Plugin.pylon.beaconinteraction.registration;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.pylon.PylonType;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class AuxiliaryPylonRegistrationManager {
    private final DF_Main plugin;
    private static final String PREFIX = PluginUtils.colorize("&d[보조 파일런] &f");

    public AuxiliaryPylonRegistrationManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void registerAuxiliaryPylon(Player player, Block beacon, Clan clan) {
        // 1. 구조물 설치
        plugin.getPylonManager().getStructureManager().placeBaseAndBarrier(beacon.getLocation(), PylonType.AUXILIARY);
        // 2. 파일런 정보 등록
        String locationString = PluginUtils.serializeLocation(beacon.getLocation());
        clan.addPylonLocation(locationString, PylonType.AUXILIARY);
        plugin.getClanManager().getStorageManager().saveClan(clan);

        // 3. 영역 보호 활성화
        plugin.getPylonManager().getAreaManager().addProtectedPylon(beacon.getLocation(), clan);

        player.sendMessage(PREFIX + "보조 파일런이 성공적으로 설치 및 활성화되었습니다!");
    }
}