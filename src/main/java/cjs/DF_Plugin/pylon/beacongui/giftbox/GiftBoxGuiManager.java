package cjs.DF_Plugin.pylon.beacongui.giftbox;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.util.UUID;

public class GiftBoxGuiManager implements Listener {
    private final DF_Main plugin;
    private static final String PREFIX = PluginUtils.colorize("&e[선물상자] &f");

    public GiftBoxGuiManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void openGiftBox(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            // 버튼이 표시되지 않아야 하므로, 만약을 대비해 조용히 실패 처리
            return;
        }

        // 이 선물상자를 다른 가문원이 이미 사용하고 있는지 확인합니다. (더 안정적인 방식으로 변경)
        if (plugin.getClanManager().isGiftBoxInUse(clan)) {
            UUID viewerId = plugin.getClanManager().getGiftBoxViewer(clan);
            // 자기 자신이 이미 열고 있는 경우는 거의 없지만, 만약을 위해 체크합니다.
            if (viewerId != null && !viewerId.equals(player.getUniqueId())) {
                player.sendMessage(PREFIX + "§c다른 가문원이 이미 선물상자를 사용하고 있습니다.");
                return;
            }
        }

        Inventory giftBoxInventory = plugin.getClanManager().getGiftBoxInventory(clan);
        // 아무도 사용하고 있지 않으므로, 플레이어에게 선물상자를 엽니다.
        player.openInventory(giftBoxInventory);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        InventoryView view = event.getView();
        // 인벤토리 제목이 "§f의 선물상자"로 끝나는지 확인하여 선물상자 GUI를 식별합니다.
        if (!view.getTitle().endsWith("§f의 선물상자")) {
            return;
        }

        Player player = (Player) event.getPlayer();
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan != null) {
            // 선물상자를 연 플레이어를 기록합니다.
            plugin.getClanManager().setGiftBoxViewer(clan, player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryView view = event.getView();
        if (!view.getTitle().endsWith("§f의 선물상자")) {
            return;
        }

        Player player = (Player) event.getPlayer();
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan != null) {
            // 선물상자를 닫은 플레이어 기록을 제거합니다.
            plugin.getClanManager().setGiftBoxViewer(clan, null);

            // [개선] 플레이어가 아이템을 가져간 후 데이터가 유실되는 것을 방지하기 위해,
            // 인벤토리를 닫을 때 즉시 파일에 저장합니다.
            plugin.getInventoryDataManager().saveInventory(event.getInventory(), "gift_box", clan.getName());
            plugin.getInventoryDataManager().saveConfig();
        }
    }
}