package cjs.DF_Plugin.player.stats;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;

public class StatsListener implements Listener {

    private final DF_Main plugin;

    public StatsListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 스탯 평가/보기 GUI에서는 아이템을 클릭할 수 없습니다.
        if (event.getView().getTitle().startsWith(PlayerEvalGuiManager.EVAL_GUI_TITLE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        plugin.getStatsManager().incrementDeaths(victim.getUniqueId());

        Player killer = victim.getKiller();
        if (killer != null) {
            plugin.getStatsManager().incrementKills(killer.getUniqueId());
            if (!plugin.getGameConfigManager().isKillLogEnabled()) {
                event.setDeathMessage(null);
            }
        }
    }
}