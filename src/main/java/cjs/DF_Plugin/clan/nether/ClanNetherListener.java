package cjs.DF_Plugin.clan.nether;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.clan.ClanManager;
import cjs.DF_Plugin.pylon.beaconinteraction.PylonAreaManager;
import cjs.DF_Plugin.settings.GameConfigManager;
import cjs.DF_Plugin.world.WorldManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class ClanNetherListener implements Listener {

    private final DF_Main plugin;
    private final GameConfigManager configManager;
    private final ClanManager clanManager;
    private final WorldManager worldManager;
    private final PylonAreaManager pylonAreaManager;

    public ClanNetherListener(DF_Main plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getGameConfigManager();
        this.clanManager = plugin.getClanManager();
        this.worldManager = plugin.getWorldManager();
        this.pylonAreaManager = plugin.getPylonManager().getAreaManager();
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        // 이 리스너는 네더 포탈로 인한 이동만 처리합니다.
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }

        if (!configManager.getConfig().getBoolean("pylon.features.clan-nether", true)) {
            return;
        }

        Player player = event.getPlayer();
        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());

        if (clan == null) {
            player.sendMessage("§c가문에 소속되어 있어야 지옥에 입장할 수 있습니다.");
            event.setCancelled(true);
            return;
        }

        World fromWorld = event.getFrom().getWorld();
        if (fromWorld.getEnvironment() == World.Environment.NORMAL) { // 오버월드 -> 지옥
            if (!pylonAreaManager.isLocationInClanPylonArea(clan, event.getFrom())) {
                player.sendMessage("§c파일런 범위 내에 있는 지옥문만 사용할 수 있습니다.");
                event.setCancelled(true);
                return;
            }

            World clanNether = worldManager.getOrCreateClanNether(clan);
            // 올바른 목적지 Location 객체 생성
            Location from = event.getFrom();
            Location destination = new Location(clanNether, from.getX() / 8.0, from.getY(), from.getZ() / 8.0, from.getYaw(), from.getPitch());
            event.setTo(destination); // 서버가 포탈을 찾거나 생성하도록 목적지 설정

        } else if (fromWorld.getName().equals(worldManager.getClanNetherWorldName(clan))) { // 지옥 -> 오버월드
            event.setCancelled(true); // 기본 포탈 이동 취소
            clan.getMainPylonLocationObject().ifPresentOrElse(mainPylonLocation -> {
                // 주 파일런 위치로 목적지 설정. 서버가 주변에서 포탈을 찾거나 생성합니다.
                Location safePortalLocation = PortalHelper.findOrCreateSafePortal(mainPylonLocation, 16);
                player.teleport(safePortalLocation);
            }, () -> {
                player.sendMessage("§c가문의 파일런 코어 위치를 찾을 수 없어 오버월드로 귀환할 수 없습니다.");
            });
        }
    }
}