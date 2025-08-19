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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

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
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() == InventoryType.PLAYER) {
            return;
        }

        // 파일런 코어(주 파일런)만 보관함에 넣을 수 없도록 제한합니다.
        ItemStack cursorItem = event.getCursor();
        ItemStack currentItem = event.getCurrentItem();

        if (PylonItemFactory.isMainCore(cursorItem) || PylonItemFactory.isMainCore(currentItem)) {
            event.getWhoClicked().sendMessage(PREFIX + "§c파일런 코어는 보관함에 넣을 수 없습니다.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onGiftBoxOpen(InventoryOpenEvent event) {
        if (!event.getView().getTitle().startsWith("§d[선물상자]")) return;

        Player player = (Player) event.getPlayer();
        ClanManager clanManager = DF_Main.getInstance().getClanManager();
        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());

        if (clan != null && event.getView().getTitle().equals("§d[선물상자] §5" + clan.getName())) {
            UUID viewerId = clanManager.getGiftBoxViewer(clan);
            // 다른 사람이 이미 보고 있다면
            if (viewerId != null && !viewerId.equals(player.getUniqueId())) {
                player.sendMessage("§c다른 가문원이 이미 선물상자를 보고 있습니다.");
                event.setCancelled(true);
            } else {
                // 아무도 안보고 있으면 내가 시청자로 등록
                clanManager.setGiftBoxViewer(clan, player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onGiftBoxClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().startsWith("§d[선물상자]")) return;

        Player player = (Player) event.getPlayer();
        ClanManager clanManager = DF_Main.getInstance().getClanManager();
        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());

        if (clan != null && event.getView().getTitle().equals("§d[선물상자] §5" + clan.getName())) {
            // 인벤토리를 닫는 플레이어가 현재 시청자일 경우에만 시청자 목록에서 제거
            if (player.getUniqueId().equals(clanManager.getGiftBoxViewer(clan))) {
                clanManager.setGiftBoxViewer(clan, null);
            }
        }
    }
}