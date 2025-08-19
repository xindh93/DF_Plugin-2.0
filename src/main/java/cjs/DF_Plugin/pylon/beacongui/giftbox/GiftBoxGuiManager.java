package cjs.DF_Plugin.pylon.beacongui.giftbox;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;

public class GiftBoxGuiManager implements Listener {
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

        Inventory giftBoxInventory = plugin.getClanManager().getGiftBoxInventory(clan);

        // 이 인벤토리를 현재 보고 있는 다른 플레이어가 있는지 확인합니다.
        // Inventory#getViewers()는 이 인벤토리를 보고 있는 모든 플레이어의 목록을 실시간으로 반환합니다.
        for (HumanEntity viewer : giftBoxInventory.getViewers()) {
            // 자기 자신은 제외하고 다른 플레이어가 있는지 확인합니다.
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(PREFIX + "§c다른 가문원이 이미 선물상자를 사용하고 있습니다.");
                return; // 다른 플레이어가 사용 중이므로 GUI를 열지 않고 종료합니다.
            }
        }

        // 아무도 사용하고 있지 않으므로, 플레이어에게 선물상자를 엽니다.
        player.openInventory(giftBoxInventory);
    }
}