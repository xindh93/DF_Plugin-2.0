package cjs.DF_Plugin.player.stats;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Bukkit;

import static cjs.DF_Plugin.player.stats.StatsEditor.STATS_ACTION_KEY;
import static cjs.DF_Plugin.player.stats.StatsEditor.STATS_TYPE_KEY;

public class StatsListener implements Listener {

    private final DF_Main plugin;

    public StatsListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith(StatsEditor.GUI_TITLE_PREFIX)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player editor)) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        // 버튼이 아니면 무시
        if (!container.has(STATS_ACTION_KEY, PersistentDataType.STRING)) {
            return;
        }

        StatsManager statsManager = plugin.getStatsManager();
        String targetName = event.getView().getTitle().replace(StatsEditor.GUI_TITLE_PREFIX, "");
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            editor.sendMessage("§c평가 대상 플레이어를 찾을 수 없습니다.");
            editor.closeInventory();
            return;
        }

        String action = container.get(STATS_ACTION_KEY, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "SAVE":
                // 현재 GUI에서 보고 있는 플레이어의 스탯을 저장합니다.
                statsManager.savePlayerStats(target.getUniqueId());
                editor.sendMessage("§a" + target.getName() + "님의 스탯을 저장했습니다.");
                editor.closeInventory();
                break;

            case "INCREMENT":
            case "DECREMENT":
                if (!container.has(STATS_TYPE_KEY, PersistentDataType.STRING)) return;
                try {
                    String typeStr = container.get(STATS_TYPE_KEY, PersistentDataType.STRING);
                    StatType type = StatType.valueOf(typeStr);
                    boolean increment = action.equals("INCREMENT");

                    statsManager.updateStatFromGUI(target, type, increment);
                    // 변경사항을 반영하여 GUI를 새로고침합니다.
                    editor.openInventory(StatsEditor.create(target, statsManager.getPlayerStats(target.getUniqueId())));
                } catch (IllegalArgumentException e) {
                    editor.sendMessage("§c내부 오류가 발생했습니다. (Invalid StatType)");
                }
                break;
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