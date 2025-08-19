package cjs.DF_Plugin.pylon.beaconinteraction;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.clan.ClanManager;
import cjs.DF_Plugin.pylon.item.PylonItemFactory;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

public class PylonItemListener implements Listener {

    private static final String PREFIX = PluginUtils.colorize("&b[파일런] &f");

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        // 레시피나 결과물이 없으면 무시
        if (event.getRecipe() == null || event.getInventory().getResult() == null) {
            return;
        }

        // 제작 결과가 신호기(BEACON)인 경우
        if (event.getInventory().getResult().getType() == Material.BEACON) {
            // 결과물을 '주 파일런 코어'로 변경
            event.getInventory().setResult(PylonItemFactory.createMainCore());
        }
    }

    @EventHandler
    public void onDropPylon(PlayerDropItemEvent event) {
        // 이제 파일런 코어(주 파일런)만 버릴 수 없도록 제한합니다.
        if (PylonItemFactory.isMainCore(event.getItemDrop().getItemStack())) {
            Player player = event.getPlayer();
            player.sendMessage(PREFIX + "§c파일런 코어는 버릴 수 없습니다.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onStorePylon(InventoryClickEvent event) {
        // We are only interested in inventories that are pylon storages.
        // The title is dynamic, so we check the end part.
        if (!event.getView().getTitle().endsWith(" §r§f파일런 창고")) {
            return;
        }

        // Determine the item being moved into the storage.
        ItemStack movedItem = null;
        if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // This is a shift-click. We only care if the item is moving from player inv to storage.
            if (event.getClickedInventory() != null && event.getClickedInventory().getType() == InventoryType.PLAYER) {
                movedItem = event.getCurrentItem();
            }
        } else {
            // This is a standard click/place action. We only care if the click is inside the storage.
            if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                movedItem = event.getCursor();
            }
        }

        // Check if the item is a main pylon core.
        if (PylonItemFactory.isMainCore(movedItem)) {
            event.getWhoClicked().sendMessage(PREFIX + "§c파일런 코어는 파일런 창고에 보관할 수 없습니다.");
            event.setCancelled(true);
        }
    }
}