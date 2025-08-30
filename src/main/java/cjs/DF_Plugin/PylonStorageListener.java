// src/main/java/cjs/DF_Plugin/PylonStorageListener.java
package cjs.DF_Plugin;

import org.bukkit.inventory.InventoryView;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;

public final class PylonStorageListener implements Listener {
    // 창고 제목: "<가문이름(색)>§f의 파일런 창고" 형태
    private static final String STORAGE_SUFFIX_STRIPPED = "의 파일런 창고";
    private static final int SYSTEM_SLOT = 53; // 마지막 칸(0-indexed)

    private static boolean isPylonStorage(InventoryView view) {
        if (view == null) return false;
        String title = view.getTitle();
        if (title == null) return false;
        String stripped = ChatColor.stripColor(title);
        return stripped != null && stripped.endsWith(STORAGE_SUFFIX_STRIPPED);
    }

    /** 53번 슬롯 보호 + 균열 나침반 지급 */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isPylonStorage(event.getView())) return;

        // 탑 인벤토리(창고)에서만 시스템 슬롯 제어
        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory())) {

            // 숫자키/더블클릭 등 모든 경로에서 원천 차단
            if (event.getRawSlot() == SYSTEM_SLOT) {
                event.setCancelled(true);

                // 균열 나침반이면 복사본 지급
                ItemStack sysItem = event.getCurrentItem();
                if (sysItem != null && sysItem.getType() == Material.COMPASS) {
                    ItemMeta meta = sysItem.getItemMeta();
                    if (meta != null && "§d균열의 나침반".equals(meta.getDisplayName())) {
                        giveCopy(player, sysItem);
                        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1.0f, 1.2f);
                        player.sendMessage(PluginUtils.colorize("&d[나침반] &f균열 위치를 가리키는 나침반을 받았습니다."));
                    }
                }
                return;
            }

            // 시스템 슬롯 외에도, 창고 → 플레이어로의 SHIFT-클릭 이동은 허용
            // 다만 플레이어가 아이템을 들고 시스템 슬롯에 내려놓는 행위는 위에서 rawSlot 체크로 이미 차단됨.
        }

        // 플레이어 인벤토리에서 SHIFT-클릭으로 시스템 슬롯으로 밀어넣는 것도 rawSlot 보호로 자연 차단됨.
    }

    /** 드래그로 53번 슬롯 덮는 것도 차단 */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isPylonStorage(event.getView())) return;
        // 드래그가 탑 인벤토리의 SYSTEM_SLOT을 포함하면 취소
        for (int raw : event.getRawSlots()) {
            if (raw == SYSTEM_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void giveCopy(Player player, ItemStack template) {
        ItemStack copy = template.clone();
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(copy);
        if (!leftover.isEmpty()) {
            // 인벤 꽉 찼으면 바닥 드롭
            player.getWorld().dropItemNaturally(player.getLocation(), copy);
        }
    }
}
