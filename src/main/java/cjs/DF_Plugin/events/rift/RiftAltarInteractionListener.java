package cjs.DF_Plugin.events.rift;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class RiftAltarInteractionListener implements Listener {

    private final DF_Main plugin;

    public RiftAltarInteractionListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getRiftManager().isProtectedZone(event.getBlockPlaced().getLocation())) {
            event.getPlayer().sendMessage("§c[차원의 균열] §f제단 위에는 블록을 설치할 수 없습니다.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getRiftManager().isAltarBlock(event.getBlock().getLocation())) {
            event.getPlayer().sendMessage("§c[차원의 균열] §f이 제단은 파괴할 수 없습니다.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.DRAGON_EGG) return;

        Location eggLoc = plugin.getRiftManager().getAltarLocation();
        if (eggLoc != null && eggLoc.equals(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}