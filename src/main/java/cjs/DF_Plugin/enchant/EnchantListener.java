package cjs.DF_Plugin.enchant;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class EnchantListener implements Listener {

    private final DF_Main plugin;
    private final EnchantManager enchantManager;
    public static final String GUI_TITLE = "§5마법 부여";
    public static final int ITEM_SLOT = 4;

    public EnchantListener(DF_Main plugin) {
        this.plugin = plugin;
        this.enchantManager = plugin.getEnchantManager();
    }

    @EventHandler
    public void onEnchantTableInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENCHANTING_TABLE) {
            return;
        }
        event.setCancelled(true);
        openEnchantGUI(event.getPlayer());
    }

    private void openEnchantGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, GUI_TITLE);

        // 배경 아이템 설정
        ItemStack filler = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < gui.getSize(); i++) {
            if (i != ITEM_SLOT) {
                gui.setItem(i, filler);
            }
        }

        // 중앙 슬롯 플레이스홀더
        gui.setItem(ITEM_SLOT, createPlaceholder());

        player.openInventory(gui);
    }

    private ItemStack createPlaceholder() {
        ItemStack placeholder = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = placeholder.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d마법 부여");
            meta.setLore(Arrays.asList("§7이곳에 아이템을 올려두고", "§7좌클릭하여 마법을 부여하세요."));
            placeholder.setItemMeta(meta);
        }
        return placeholder;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        Player player = (Player) event.getWhoClicked();
        Inventory topInventory = event.getView().getTopInventory();
        Inventory clickedInventory = event.getClickedInventory();

        if (clickedInventory == null) {
            return;
        }

        // --- GUI (Top Inventory) 상호작용 ---
        if (clickedInventory.equals(topInventory)) {
            event.setCancelled(true);

            if (event.getSlot() == ITEM_SLOT) {
                ItemStack cursorItem = event.getCursor();
                ItemStack itemInSlot = topInventory.getItem(ITEM_SLOT);

                // 커서의 아이템을 슬롯에 놓기
                if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                    if (itemInSlot == null || itemInSlot.getType() == Material.AIR || itemInSlot.getType() == Material.ENCHANTED_BOOK) {
                        topInventory.setItem(ITEM_SLOT, cursorItem.clone());
                        player.setItemOnCursor(null);
                    }
                }
                // 슬롯에 있는 아이템과 상호작용
                else if (itemInSlot != null && itemInSlot.getType() != Material.ENCHANTED_BOOK) {
                    if (event.isLeftClick()) { // 좌클릭: 인챈트 시도
                        enchantManager.attemptEnchant(player, itemInSlot);
                    } else if (event.isRightClick()) { // 우클릭: 아이템 빼기
                        player.getInventory().addItem(itemInSlot);
                        topInventory.setItem(ITEM_SLOT, createPlaceholder());
                    }
                }
            }
        }
        // --- 플레이어 인벤토리 (Bottom Inventory) 상호작용 ---
        else if (clickedInventory.equals(event.getView().getBottomInventory())) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            // GUI 슬롯이 비어있으면 아이템 이동
            ItemStack itemInGuiSlot = topInventory.getItem(ITEM_SLOT);
            if (itemInGuiSlot == null || itemInGuiSlot.getType() == Material.AIR || itemInGuiSlot.getType() == Material.ENCHANTED_BOOK) {
                event.setCancelled(true);
                topInventory.setItem(ITEM_SLOT, clickedItem.clone());
                event.setCurrentItem(null);
                player.updateInventory();
            }
            // 슬롯이 차있으면, 이벤트 취소 없이 일반적인 인벤토리 상호작용 허용
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        ItemStack item = event.getInventory().getItem(ITEM_SLOT);
        if (item != null && item.getType() != Material.ENCHANTED_BOOK && item.getType() != Material.AIR) {
            ((Player) event.getPlayer()).getInventory().addItem(item);
        }
    }
}