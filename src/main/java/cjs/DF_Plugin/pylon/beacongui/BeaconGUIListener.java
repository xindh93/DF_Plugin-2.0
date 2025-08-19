package cjs.DF_Plugin.pylon.beacongui;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.pylon.beacongui.giftbox.GiftBoxGuiManager;
import cjs.DF_Plugin.pylon.beacongui.recruit.RecruitGuiManager;
import cjs.DF_Plugin.pylon.beacongui.resurrect.ResurrectGuiManager;
import cjs.DF_Plugin.pylon.beacongui.shop.PylonShopManager;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class BeaconGUIListener implements Listener {

    private final DF_Main plugin;
    private final BeaconGUIManager guiManager;

    public BeaconGUIListener(DF_Main plugin, BeaconGUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onPylonInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.BEACON) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        Clan playerClan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (playerClan == null) return;

        String clickedLocationStr = PluginUtils.serializeLocation(clickedBlock.getLocation());
        // 자신의 가문 파일런을 클릭했을 때만 커스텀 GUI를 엽니다.
        if (playerClan.getPylonLocations().contains(clickedLocationStr)) {
            event.setCancelled(true);
            guiManager.openMainMenu(player);
        }
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        final String title = event.getView().getTitle();
        final boolean isMainGUI = title.equals(BeaconGUIManager.MAIN_GUI_TITLE);
        final boolean isRecruitGUI = title.equals(RecruitGuiManager.RECRUIT_GUI_TITLE_SELECT) || title.equals(RecruitGuiManager.RECRUIT_GUI_TITLE_ROULETTE);
        final boolean isResurrectGUI = title.equals(ResurrectGuiManager.RESURRECT_GUI_TITLE);
        final boolean isShopGUI = title.equals(PylonShopManager.SHOP_GUI_TITLE);
        final boolean isGiftBoxGUI = title.equals(GiftBoxGuiManager.GIFT_GUI_TITLE);

        // 플러그인의 GUI가 아니면 아무것도 하지 않음
        if (!isMainGUI && !isRecruitGUI && !isResurrectGUI && !isShopGUI && !isGiftBoxGUI) {
            return;
        }

        // 선물상자 GUI에서는 아이템을 가져갈 수 있으므로 이벤트를 취소하지 않습니다.
        if (isGiftBoxGUI) {
            return;
        }

        // 다른 모든 GUI에서는 상단 인벤토리 클릭 시 기본 동작(아이템 이동)을 막습니다.
        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            event.setCancelled(true);

            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || !clickedItem.hasItemMeta()) return;

            if (isMainGUI) {
                ItemMeta meta = clickedItem.getItemMeta();
                if (meta.getPersistentDataContainer().has(BeaconGUIManager.GUI_BUTTON_KEY, PersistentDataType.STRING)) {
                    String action = meta.getPersistentDataContainer().get(BeaconGUIManager.GUI_BUTTON_KEY, PersistentDataType.STRING);
                    if (action != null) guiManager.handleMenuClick(player, action);
                }
            } else if (isRecruitGUI) {
                guiManager.getRecruitGuiManager().handleGuiClick(event);
            } else if (isResurrectGUI) {
                guiManager.getResurrectGuiManager().handleGuiClick(event);
            } else if (isShopGUI) {
                guiManager.getShopManager().handleGuiClick(event);
            }
        }
    }
}