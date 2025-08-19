package cjs.DF_Plugin.pylon.beacongui.giftbox;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class GiftBoxGuiManager {
    private final DF_Main plugin;
    public static final String GIFT_GUI_TITLE = "§d[선물상자]";
    private static final String PREFIX = PluginUtils.colorize("&e[선물상자] &f");

    public GiftBoxGuiManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void openGiftBox(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            return;
        }

        // 가문 대표만 열 수 있다는 조건은 유지합니다.
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(PREFIX + "§c가문 대표만 선물상자를 열 수 있습니다.");
            player.closeInventory();
            return;
        }

        // ClanManager로부터 해당 가문의 선물상자 인벤토리를 가져와서 엽니다.
        // 이 인벤토리는 백그라운드 작업에 의해 주기적으로 리필됩니다.
        Inventory giftBoxInventory = plugin.getClanManager().getGiftBoxInventory(clan);
        player.openInventory(giftBoxInventory);
    }
}