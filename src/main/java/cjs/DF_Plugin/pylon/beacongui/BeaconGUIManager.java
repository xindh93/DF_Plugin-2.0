package cjs.DF_Plugin.pylon.beacongui;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.pylon.beacongui.giftbox.GiftBoxGuiManager;
import cjs.DF_Plugin.pylon.beacongui.shop.PylonShopManager;
import cjs.DF_Plugin.settings.GameConfigManager;
import cjs.DF_Plugin.util.PluginUtils;
import cjs.DF_Plugin.pylon.beacongui.recruit.RecruitGuiManager;
import cjs.DF_Plugin.pylon.beacongui.resurrect.ResurrectGuiManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class BeaconGUIManager {

    private final DF_Main plugin;
    private final RecruitGuiManager recruitGuiManager;
    private final ResurrectGuiManager resurrectGuiManager;
    private final GiftBoxGuiManager giftBoxGuiManager;
    private final PylonShopManager shopManager;
    public static final String MAIN_GUI_TITLE = "§b[파일런 메뉴]";
    public static final NamespacedKey GUI_BUTTON_KEY = new NamespacedKey(DF_Main.getInstance(), "df_gui_button");

    public BeaconGUIManager(DF_Main plugin) {
        this.plugin = plugin;
        this.recruitGuiManager = new RecruitGuiManager(plugin);
        this.resurrectGuiManager = new ResurrectGuiManager(plugin);
        this.giftBoxGuiManager = new GiftBoxGuiManager(plugin);
        this.shopManager = new PylonShopManager(plugin);
    }

    /**
     * 파일런 메인 메뉴 GUI를 엽니다.
     */
    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, MAIN_GUI_TITLE);

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§c가문 정보를 찾을 수 없습니다.");
            return;
        }

        // 아이템 생성 및 배치
        gui.setItem(0, createGuiItem(Material.ENDER_CHEST, "§e파일런 창고", "pylon_storage", "§7가문 공유 창고를 엽니다."));
        gui.setItem(2, createGuiItem(Material.BEACON, "§6파일런 상점", "shop", "§7다양한 아이템을 구매합니다."));
        gui.setItem(4, createGuiItem(Material.PLAYER_HEAD, "§a팀원 뽑기", "recruit", "§7새로운 팀원을 영입합니다."));
        gui.setItem(6, createGuiItem(Material.TOTEM_OF_UNDYING, "§d팀원 부활", "resurrect", "§7사망한 팀원을 부활시킵니다."));

        // 선물상자 아이템 (동적 Lore)
        ItemStack giftBox = new ItemStack(Material.CHEST);
        ItemMeta giftMeta = giftBox.getItemMeta();
        giftMeta.setDisplayName("§6선물상자");
        long cooldownMillis = TimeUnit.HOURS.toMillis(plugin.getGameConfigManager().getConfig().getInt("pylon.giftbox.cooldown-hours", 4));        long timePassed = System.currentTimeMillis() - clan.getLastGiftBoxTime();
        if (timePassed >= cooldownMillis) {
            giftMeta.setLore(Arrays.asList("§a선물이 도착했습니다!", "§7리더만 열 수 있습니다."));
        } else {
            long remainingMillis = cooldownMillis - timePassed;
            String timeString = PluginUtils.formatTime(remainingMillis);
            giftMeta.setLore(Arrays.asList("§7다음 선물이 도착하기까지:", "§e" + timeString, "§7리더만 열 수 있습니다."));
        }
        giftMeta.getPersistentDataContainer().set(GUI_BUTTON_KEY, PersistentDataType.STRING, "giftbox");        giftBox.setItemMeta(giftMeta);
        gui.setItem(8, giftBox);

        player.openInventory(gui);
    }

    /**
     * GUI에 사용될 아이템을 생성하는 헬퍼 메소드
     */
    private ItemStack createGuiItem(Material material, String name, String action, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            // 아이템에 어떤 버튼인지 식별자를 저장
            meta.getPersistentDataContainer().set(GUI_BUTTON_KEY, PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void handleMenuClick(Player player, String action) {
        GameConfigManager configManager = plugin.getGameConfigManager();

        switch (action) {
            case "pylon_storage":
                if (!configManager.getConfig().getBoolean("pylon.features.storage", true)) {
                    player.sendMessage("§c파일런 창고 기능이 비활성화되어 있습니다.");
                    player.closeInventory();
                    return;
                }
                plugin.getClanManager().openPylonStorage(player);
                break;
            case "clan_nether":
                if (!configManager.getConfig().getBoolean("pylon.features.clan-nether", true)) {
                    player.sendMessage("§c클랜 지옥 기능이 비활성화되어 있습니다.");
                    player.closeInventory();
                    return;
                }
                player.sendMessage("§a[클랜 지옥 안내] §7파일런 범위 내에 지옥문을 건설하고 이용하면 가문 전용 지옥으로 입장합니다.");
                player.closeInventory();
                break;
            case "resurrect":
                resurrectGuiManager.openResurrectionGui(player);
                break;
            case "recruit":
                recruitGuiManager.startRecruitmentProcess(player);
                break;
            case "giftbox":
                giftBoxGuiManager.openGiftBox(player);
                break;
            case "recon_firework":
                plugin.getPylonManager().getReconManager().activateRecon(player);
                break;
            case "shop":
                shopManager.openShopGui(player);
                break;
        }
    }

    public RecruitGuiManager getRecruitGuiManager() { return recruitGuiManager; }
    public ResurrectGuiManager getResurrectGuiManager() { return resurrectGuiManager; }
    public GiftBoxGuiManager getGiftBoxGuiManager() { return giftBoxGuiManager; }
    public PylonShopManager getShopManager() {
        return shopManager;
    }
}