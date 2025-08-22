package cjs.DF_Plugin.util.item;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class PylonItemFactory {

    public static final NamespacedKey AUXILIARY_CORE_KEY = new NamespacedKey(DF_Main.getInstance(), "auxiliary_pylon_core");
    public static final NamespacedKey MAIN_CORE_KEY = new NamespacedKey(DF_Main.getInstance(), "main_pylon_core");
    public static final NamespacedKey RETURN_SCROLL_KEY = new NamespacedKey(DF_Main.getInstance(), "return_scroll");

    /**
     * 보조 파일런 코어 아이템을 생성합니다.
     * @return 보조 파일런 코어 ItemStack
     */
    public static ItemStack createAuxiliaryCore() {
        ItemStack core = new ItemStack(Material.BEACON);
        ItemMeta meta = core.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d보조 파일런 코어");
            meta.setLore(Arrays.asList("§7주 파일런의 신호기 범위 내에 설치하여", "§7가문의 영역을 확장할 수 있습니다."));
            meta.addEnchant(Enchantment.LURE, 1, false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(AUXILIARY_CORE_KEY, PersistentDataType.BYTE, (byte) 1);
            core.setItemMeta(meta);
        }
        return core;
    }

    /**
     * 해당 아이템이 보조 파일런 코어인지 확인합니다.
     * @param item 확인할 ItemStack
     * @return 보조 파일런 코어가 맞으면 true
     */
    public static boolean isAuxiliaryCore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(AUXILIARY_CORE_KEY, PersistentDataType.BYTE);
    }

    /**
     * 주 파일런 코어 아이템을 생성합니다.
     * @return 주 파일런 코어 ItemStack
     */
    public static ItemStack createMainCore() {
        ItemStack core = new ItemStack(Material.BEACON);
        ItemMeta meta = core.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§l파일런 코어");
            meta.setLore(Arrays.asList(
                    "§7가문의 중심이 되는 강력한 코어입니다.",
                    "§7설치 시 가문의 영역이 생성됩니다."
            ));
            meta.addEnchant(Enchantment.LURE, 1, false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(MAIN_CORE_KEY, PersistentDataType.BYTE, (byte) 1);
            core.setItemMeta(meta);
        }
        return core;
    }

    /**
     * 해당 아이템이 주 파일런 코어인지 확인합니다.
     * @param item 확인할 ItemStack
     * @return 주 파일런 코어가 맞으면 true
     */
    public static boolean isMainCore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(MAIN_CORE_KEY, PersistentDataType.BYTE);
    }

    /**
     * 귀환 주문서를 생성합니다.
     * @return 귀환 주문서 ItemStack
     */
    public static ItemStack createReturnScroll() {
        ItemStack scroll = new ItemStack(Material.GLOBE_BANNER_PATTERN);
        ItemMeta meta = scroll.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b귀환 주문서");
            meta.setLore(Arrays.asList(
                    "§7가문 파일런 영역 내의 안전한 곳으로",
                    "§7자신을 소환합니다.",
                    "",
                    "§e[사용법] §f손에 들고 우클릭하여 사용합니다.",
                    "§c(시전 중 이동하거나 피격 시 취소)"
            ));
            meta.addEnchant(Enchantment.FORTUNE, 1, false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(RETURN_SCROLL_KEY, PersistentDataType.BYTE, (byte) 1);
            scroll.setItemMeta(meta);
        }
        return scroll;
    }

    /**
     * 해당 아이템이 귀환 주문서인지 확인합니다.
     * @param item 확인할 ItemStack
     * @return 귀환 주문서가 맞으면 true
     */
    public static boolean isReturnScroll(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(RETURN_SCROLL_KEY, PersistentDataType.BYTE);
    }

}