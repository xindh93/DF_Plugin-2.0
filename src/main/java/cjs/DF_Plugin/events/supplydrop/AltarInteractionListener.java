package cjs.DF_Plugin.events.supplydrop;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class AltarInteractionListener implements Listener {

    private final DF_Main plugin;

    public AltarInteractionListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // SupplyDropManager에서 제단 주변인지 확인하여 블록 설치를 막습니다.
        if (plugin.getSupplyDropManager().isProtectedZone(event.getBlockPlaced().getLocation())) {
            event.getPlayer().sendMessage("§c[보급] §f제단 위에는 블록을 설치할 수 없습니다.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // SupplyDropManager에 저장된 제단 블록 정보와 대조하여 파괴를 막습니다.
        if (plugin.getSupplyDropManager().isAltarBlock(event.getBlock().getLocation())) {
            event.getPlayer().sendMessage("§c[보급] §f이 제단은 파괴할 수 없습니다.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 알 우클릭(텔레포트) 및 좌클릭 상호작용을 막습니다.
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.DRAGON_EGG) return;

        Location eggLoc = plugin.getSupplyDropManager().getAltarLocation();
        if (eggLoc != null && eggLoc.equals(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
}