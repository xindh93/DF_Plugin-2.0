package cjs.DF_Plugin.pylon.beaconinteraction;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.clan.ClanManager;
import cjs.DF_Plugin.pylon.PylonType;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class PylonProtectionListener implements Listener {

    private final DF_Main plugin;
    private final ClanManager clanManager;
    private final PylonAreaManager areaManager;

    public PylonProtectionListener(DF_Main plugin) {
        this.plugin = plugin;
        this.clanManager = plugin.getClanManager();
        this.areaManager = plugin.getPylonManager().getAreaManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPylonBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // 1. 비콘(파일런 코어) 자체에 대한 처리
        if (block.getType() == Material.BEACON) {
            Clan victimClan = areaManager.getClanAt(block.getLocation());
            if (victimClan != null) {
                handleBeaconBreak(event, block, victimClan);
            }
            return; // 비콘 관련 처리는 여기서 종료
        }

        // 2. 비콘이 아닌 다른 구조물(기반, 배리어)에 대한 처리
        if (areaManager.isPylonStructureBlock(block.getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c이 블록은 파일런의 일부이므로 파괴할 수 없습니다.");
        }
    }

    private void handleBeaconBreak(BlockBreakEvent event, Block block, Clan victimClan) {
        Player player = event.getPlayer();
        Clan attackerClan = clanManager.getClanByPlayer(player.getUniqueId());
        String pylonLocStr = PluginUtils.serializeLocation(block.getLocation());
        PylonType pylonType = victimClan.getPylonType(pylonLocStr);

        // Case 1: Player is breaking their own clan's pylon (retrieval)
        if (victimClan.equals(attackerClan)) {
            // 주 파일런 코어는 가문 대표만 회수할 수 있도록 권한을 확인합니다.
            if (pylonType == PylonType.MAIN_CORE && !victimClan.getLeader().equals(player.getUniqueId())) {
                player.sendMessage("§c주 파일런 코어는 가문 대표만 회수할 수 있습니다.");
                event.setCancelled(true);
                return;
            }

            // 회수 로직을 호출하고, 그 결과에 따라 파괴 이벤트를 처리합니다.
            boolean success = plugin.getPylonManager().getRetrievalManager().handlePylonRetrieval(player, block, victimClan);
            if (!success) {
                // 회수 실패(쿨타임, 권한 등) 시 파괴를 취소합니다.
                event.setCancelled(true);
            }
            return;
        }
        // Case 2: Attacker is clanless
        if (attackerClan == null) {
            player.sendMessage("§c가문 소속원만 다른 가문의 파일런을 파괴할 수 있습니다.");
            event.setCancelled(true);
            return;
        }

        // Case 3: Attacking another clan's pylon
        if (pylonType == PylonType.MAIN_CORE) {
            if (victimClan.hasAuxiliaryPylons()) {
                player.sendMessage("§c이 가문의 모든 보조 파일런을 먼저 파괴해야 합니다.");
                event.setCancelled(true);
                return;
            }
            // This is the last pylon, clan will be absorbed.
            event.setDropItems(false);
            clanManager.absorbClan(attackerClan, victimClan);
        } else if (pylonType == PylonType.AUXILIARY) {
            // Auxiliary pylon is destroyed.
            event.setDropItems(false);
            plugin.getPylonManager().getStructureManager().removeBaseAndBarrier(block.getLocation());
            areaManager.removeProtectedPylon(block.getLocation());
            victimClan.removePylonLocation(pylonLocStr);
            clanManager.getStorageManager().saveClan(victimClan);
            attackerClan.broadcastMessage("§a" + victimClan.getFormattedName() + "§a 가문의 보조 파일런을 파괴했습니다!");
            victimClan.broadcastMessage("§c" + attackerClan.getFormattedName() + "§c 가문에 의해 보조 파일런이 파괴되었습니다!");

            // 잠시 후, 남은 파일런들의 기반을 재설치하여 안정화시킵니다.
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    Clan currentVictimClan = clanManager.getClanByName(victimClan.getName());
                    if (currentVictimClan != null) {
                        plugin.getPylonManager().reinitializeAllBases(currentVictimClan);
                    }
                }
            }.runTaskLater(plugin, 100L); // 5초 지연
        } else {
            // Should not happen if data is consistent
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplodeByBlock(BlockExplodeEvent event) {
        event.blockList().removeIf(block ->
                areaManager.getClanAt(block.getLocation()) != null || areaManager.isPylonStructureBlock(block.getLocation())
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplodeByEntity(EntityExplodeEvent event) {
        event.blockList().removeIf(block ->
                areaManager.getClanAt(block.getLocation()) != null || areaManager.isPylonStructureBlock(block.getLocation())
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (areaManager.getClanAt(block.getLocation()) != null || areaManager.isPylonStructureBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (areaManager.getClanAt(block.getLocation()) != null || areaManager.isPylonStructureBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }
}