package cjs.DF_Plugin.player;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.ClanManager;
import cjs.DF_Plugin.player.stats.StatsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final DF_Main plugin;
    private final ClanManager clanManager;

    public PlayerJoinListener(DF_Main plugin) {
        this.plugin = plugin;
        this.clanManager = plugin.getClanManager();
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ClanManager clanManager = plugin.getClanManager();
        StatsManager statsManager = plugin.getStatsManager();

        // A small delay might be needed for other plugins to load player data,
        // but Bukkit's event priority usually handles this.
        clanManager.getPlayerTagManager().updatePlayerTag(player);
        if (clanManager != null) {
            clanManager.getPlayerTagManager().updatePlayerTag(player);
        }

        // 플레이어의 스탯이 등록되어 있는지 확인하고, 없으면 기본값으로 자동 등록합니다.
        if (statsManager != null && !statsManager.hasStats(player.getUniqueId())) {
            statsManager.getPlayerStats(player.getUniqueId()); // 캐시에 기본 스탯 생성
            statsManager.savePlayerStats(player.getUniqueId()); // 파일에 저장
            player.sendMessage("§a스탯이 기본값으로 자동 등록되었습니다.");
        }

        if (plugin.getSupplyDropManager() != null) {
            plugin.getSupplyDropManager().showBarToPlayer(player);
        }
    }
}