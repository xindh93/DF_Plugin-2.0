package cjs.DF_Plugin.pylon.beacongui.resurrect;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.util.item.ItemBuilder;
import cjs.DF_Plugin.util.item.ItemFactory;
import cjs.DF_Plugin.pylon.beacongui.BeaconGUIManager;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ResurrectGuiManager {
    private final DF_Main plugin;
    public static final String RESURRECT_GUI_TITLE = "§b[팀원 부활]";
    private static final String PREFIX = PluginUtils.colorize("&d[부활] &f");

    public ResurrectGuiManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void openResurrectionGui(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;

        List<UUID> deadClanMembers = plugin.getPlayerDeathManager().getDeadPlayers().keySet().stream()
                .filter(clan.getMembers()::contains)
                .collect(Collectors.toList());

        // TODO: Add pagination for more than 54 members
        Inventory gui = Bukkit.createInventory(null, 54, RESURRECT_GUI_TITLE);

        if (deadClanMembers.isEmpty()) {
            ItemStack noOneToResurrect = new ItemBuilder(Material.BARRIER)
                    .withName("§c부활시킬 팀원이 없습니다.")
                    .addLoreLine("§7사망한 가문원이 없습니다.")
                    .build();
            gui.setItem(22, noOneToResurrect); // GUI 중앙에 메시지 표시
        } else {
            for (UUID deadMemberUUID : deadClanMembers) {
                OfflinePlayer deadPlayer = Bukkit.getOfflinePlayer(deadMemberUUID);
                gui.addItem(createResurrectionHead(deadPlayer));
            }
        }

        player.openInventory(gui);
    }

    private ItemStack createResurrectionHead(OfflinePlayer deadPlayer) {
        long deathTime = plugin.getPlayerDeathManager().getDeadPlayers().get(deadPlayer.getUniqueId());
        int banDurationMinutes = plugin.getGameConfigManager().getConfig().getInt("pylon.death-ban.duration-minutes", 60);
        long banEndTime = deathTime + TimeUnit.MINUTES.toMillis(banDurationMinutes);
        long remainingMillis = Math.max(0, banEndTime - System.currentTimeMillis());

        long remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis);
        int costPerMinute = plugin.getGameConfigManager().getConfig().getInt("pylon.death-ban.resurrection-cost-per-minute", 1);
        long totalCost = remainingMinutes * costPerMinute;
        String remainingTime = PluginUtils.formatTime(remainingMillis);

        // 데이터 파일에 저장된 머리를 먼저 로드하고, 없으면 새로 생성합니다.
        ItemStack playerHead = plugin.getPlayerDataManager().getPlayerHead(deadPlayer.getUniqueId());
        if (playerHead == null || playerHead.getType() == Material.AIR) {
            playerHead = ItemFactory.createPlayerHead(deadPlayer.getUniqueId());
        }

        return new ItemBuilder(playerHead)
                .withName("§c" + deadPlayer.getName())
                .withLore(
                        "§7클릭하여 이 팀원을 부활시킵니다.",
                        "",
                        "§f남은 시간: §e" + remainingTime,
                        "§f부활 비용: §a에메랄드 " + totalCost + "개"
                )
                .withPDCString(BeaconGUIManager.GUI_BUTTON_KEY, "resurrect_player:" + deadPlayer.getUniqueId())
                .build();
    }

    public void handleGuiClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String actionData = meta.getPersistentDataContainer().get(BeaconGUIManager.GUI_BUTTON_KEY, PersistentDataType.STRING);

        if (actionData == null || !actionData.startsWith("resurrect_player:")) return;

        UUID targetUUID = UUID.fromString(actionData.split(":")[1]);
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUUID);

        // Calculate cost again to be safe
        long deathTime = plugin.getPlayerDeathManager().getDeadPlayers().get(targetUUID);
        int banDurationMinutes = plugin.getGameConfigManager().getConfig().getInt("pylon.death-ban.duration-minutes", 60);
        long banEndTime = deathTime + TimeUnit.MINUTES.toMillis(banDurationMinutes);
        long remainingMillis = Math.max(0, banEndTime - System.currentTimeMillis());
        long remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis);
        int costPerMinute = plugin.getGameConfigManager().getConfig().getInt("pylon.death-ban.resurrection-cost-per-minute", 1);
        long totalCost = remainingMinutes * costPerMinute;

        if (!player.getInventory().contains(Material.EMERALD, (int) totalCost)) {
            player.sendMessage(PREFIX + "§c부활에 필요한 §a에메랄드§c가 부족합니다. (필요: " + totalCost + "개)");
            player.closeInventory();
            return;
        }

        // Process resurrection
        player.getInventory().removeItem(new ItemStack(Material.EMERALD, (int) totalCost));
        plugin.getPlayerDeathManager().resurrectPlayer(targetUUID);

        player.sendMessage(PREFIX + "§a" + targetPlayer.getName() + "님을 성공적으로 부활시켰습니다!");

        // Refresh or close GUI
        player.closeInventory(); // 부활 후 GUI를 닫습니다.
    }
}