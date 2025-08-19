package cjs.DF_Plugin.player.stats;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.items.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class StatsEditor {

    public static final String GUI_TITLE_PREFIX = "§c[스탯 편집] §f";
    public static final NamespacedKey STATS_ACTION_KEY = new NamespacedKey(DF_Main.getInstance(), "stats_action");
    public static final NamespacedKey STATS_TYPE_KEY = new NamespacedKey(DF_Main.getInstance(), "stats_type");

    public static Inventory create(Player target, PlayerStats stats) {
        Inventory gui = Bukkit.createInventory(null, 45, GUI_TITLE_PREFIX + target.getName());

        // 스탯별 아이템 및 버튼 배치
        setupStatSection(gui, StatType.ATTACK, stats.getStat(StatType.ATTACK), 1, 10, 19);
        setupStatSection(gui, StatType.INTELLIGENCE, stats.getStat(StatType.INTELLIGENCE), 3, 12, 21);
        setupStatSection(gui, StatType.STAMINA, stats.getStat(StatType.STAMINA), 5, 14, 23);
        setupStatSection(gui, StatType.ENTERTAINMENT, stats.getStat(StatType.ENTERTAINMENT), 7, 16, 25);

        // 저장 버튼
        gui.setItem(44, new ItemBuilder(Material.WRITABLE_BOOK).withName("§a저장").withPDCString(STATS_ACTION_KEY, "SAVE").build());

        return gui;
    }

    private static void setupStatSection(Inventory gui, StatType type, int value, int plusSlot, int displaySlot, int minusSlot) {
        gui.setItem(displaySlot, createStatDisplay(type, value));
        gui.setItem(plusSlot, createButton(type, true));
        gui.setItem(minusSlot, createButton(type, false));
    }

    private static ItemStack createStatDisplay(StatType type, int value) {
        // StatType에 getMaterial()이 정의되어 있다고 가정합니다.
        return new ItemBuilder(type.getMaterial())
                .withName(type.getDisplayName())
                .addLoreLine("§7현재: " + getStars(value))
                .build();
    }

    private static ItemStack createButton(StatType type, boolean increment) {
        Material mat = increment ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        String name = increment ? "§a+1" : "§c-1";
        String action = increment ? "INCREMENT" : "DECREMENT";
        return new ItemBuilder(mat)
                .withName(name)
                .withPDCString(STATS_ACTION_KEY, action)
                .withPDCString(STATS_TYPE_KEY, type.name())
                .build();
    }

    public static String getStars(int value) {
        value = Math.max(0, Math.min(10, value)); // 0~10 범위 보장
        return "§e" + "★".repeat(value) + "§7" + "☆".repeat(10 - value);
    }
}