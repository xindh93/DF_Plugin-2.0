package cjs.DF_Plugin.pylon.beaconinteraction;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.pylon.item.PylonItemFactory;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public class BeaconInteractionListener implements Listener {

    private final DF_Main plugin;
    private static final String PREFIX = PluginUtils.colorize("&b[파일런] &f");

    public BeaconInteractionListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPylonPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        ItemStack itemInHand = event.getItemInHand();

        if (block.getType() != Material.BEACON) {
            return;
        }

        boolean isMainCore = PylonItemFactory.isMainCore(itemInHand);
        boolean isAuxiliaryCore = PylonItemFactory.isAuxiliaryCore(itemInHand);

        if (!isMainCore && !isAuxiliaryCore) {
            return;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(PREFIX + "§c가문에 소속되어 있어야 파일런을 설치할 수 있습니다.");
            event.setCancelled(true);
            return;
        }

        int maxPylons = plugin.getGameConfigManager().getConfig().getInt("pylon.max-pylons-per-clan", 1);
        if (clan.getPylonLocations().size() >= maxPylons) {
            player.sendMessage(PREFIX + "§c가문이 가질 수 있는 최대 파일런 개수(" + maxPylons + "개)에 도달했습니다.");
            event.setCancelled(true);
            return;
        }

        if (isMainCore) {
            // 해수면 아래 설치 강제 확인
            boolean requireBelowSeaLevel = plugin.getGameConfigManager().getConfig().getBoolean("pylon.installation.require-below-sea-level", true);
            if (requireBelowSeaLevel && block.getLocation().getBlockY() >= block.getWorld().getSeaLevel()) {
                player.sendMessage(PREFIX + "§c주 파일런 코어는 해수면 아래에만 설치할 수 있습니다.");
                event.setCancelled(true);
                return;
            }
            plugin.getPylonManager().getRegistrationManager().registerPylon(player, block, clan);

        } else if (isAuxiliaryCore) {
            // 보조 파일런은 주 파일런이 있어야만 설치 가능
            if (!clan.hasMainPylon()) {
                player.sendMessage(PREFIX + "§c보조 파일런을 설치하려면 먼저 주 파일런 코어를 설치해야 합니다.");
                event.setCancelled(true);
                return;
            }

            // 멀티 코어 기능 활성화 여부 확인
            boolean multiCoreEnabled = plugin.getGameConfigManager().getConfig().getBoolean("pylon.features.multi-core", false);
            if (!multiCoreEnabled) {
                player.sendMessage(PREFIX + "§c서버에서 멀티 코어 기능이 비활성화되어 있어 보조 파일런을 설치할 수 없습니다.");
                event.setCancelled(true);
                return;
            }

            // 해수면 아래 설치 강제 확인
            boolean requireBelowSeaLevel = plugin.getGameConfigManager().getConfig().getBoolean("pylon.installation.require-below-sea-level", true);
            if (requireBelowSeaLevel && block.getLocation().getBlockY() >= block.getWorld().getSeaLevel()) {
                player.sendMessage(PREFIX + "§c보조 파일런은 해수면 아래에만 설치할 수 있습니다.");
                event.setCancelled(true);
                return;
            }

            // 다른 파일런 기반과 겹치는지 확인
            Location beaconLoc = block.getLocation();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (plugin.getPylonManager().getAreaManager().isPylonStructureBlock(beaconLoc.clone().add(x, -1, z))) {
                        player.sendMessage(PREFIX + "§c다른 파일런의 기반과 겹치는 위치에는 설치할 수 없습니다.");
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            // 보조 파일런은 주 파일런의 영역 내에만 설치 가능
            if (!plugin.getPylonManager().getAreaManager().isLocationInClanMainPylonArea(clan, block.getLocation())) {
                player.sendMessage(PREFIX + "§c보조 파일런은 주 파일런의 영역 내에만 설치할 수 있습니다.");
                event.setCancelled(true);
                return;
            }
            plugin.getPylonManager().getAuxiliaryRegistrationManager().registerAuxiliaryPylon(player, block, clan);
        }
    }
}