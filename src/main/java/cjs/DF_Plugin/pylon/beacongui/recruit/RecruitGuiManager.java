package cjs.DF_Plugin.pylon.beacongui.recruit;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.items.ItemBuilder;
import cjs.DF_Plugin.player.stats.PlayerStats;
import cjs.DF_Plugin.player.stats.StatType;
import cjs.DF_Plugin.player.stats.StatsEditor;
import cjs.DF_Plugin.player.stats.StatsManager;
import cjs.DF_Plugin.pylon.beacongui.BeaconGUIManager;
import cjs.DF_Plugin.settings.GameConfigManager;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class RecruitGuiManager {
    private final DF_Main plugin;
    public static final String RECRUIT_GUI_TITLE_SELECT = "§b[팀원 목록]";
    public static final String RECRUIT_GUI_TITLE_ROULETTE = "§b[팀원 뽑기]";
    private static final String PREFIX = PluginUtils.colorize("&a[팀원 모집] &f");
    private final Set<UUID> playersInRecruitment = new HashSet<>(); // 룰렛 중복 실행 방지

    public RecruitGuiManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void startRecruitmentProcess(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;

        org.bukkit.configuration.file.FileConfiguration config = plugin.getGameConfigManager().getConfig();

        if (clan.getMembers().size() >= config.getInt("pylon.recruitment.max-members", 4)) {
            player.sendMessage(PREFIX + "§c가문 인원이 최대치에 도달하여 더 이상 팀원을 뽑을 수 없습니다.");
            player.closeInventory();
            return;
        }
        String mode = config.getString("pylon.recruitment.mode", "roulette");

        if ("roulette".equalsIgnoreCase(mode)) {
            openRouletteStartGui(player, clan);
        } else {
            // 기본값은 선택 모드
            openPlayerListGui(player, clan);
        }
    }

    private void openPlayerListGui(Player player, Clan clan) {
        List<UUID> recruitable = plugin.getPlayerRegistryManager().getRecruitablePlayerUUIDs();
        // 자기 자신과 현재 가문원은 목록에서 제외
        recruitable.remove(player.getUniqueId());
        recruitable.removeAll(clan.getMembers());

        Inventory gui = Bukkit.createInventory(null, 27, RECRUIT_GUI_TITLE_SELECT);

        if (recruitable.isEmpty()) {
            ItemStack noPlayers = new ItemBuilder(Material.BARRIER)
                    .withName("§c모집 가능한 플레이어 없음")
                    .addLoreLine("§7서버에 접속한 기록이 있는 플레이어가 부족합니다.")
                    .build();
            gui.setItem(13, noPlayers);
        } else {
            int costPerMember = plugin.getGameConfigManager().getConfig().getInt("pylon.recruitment.cost-per-member", 64);
            int totalCost = clan.getMembers().size() * costPerMember;

            for (UUID playerUUID : recruitable) {
                if (gui.firstEmpty() == -1) break; // GUI가 가득 차면 중단
                OfflinePlayer target = Bukkit.getOfflinePlayer(playerUUID);
                gui.addItem(createRecruitablePlayerHead(target, totalCost));
            }
        }

        player.openInventory(gui);
    }

    private void openRouletteStartGui(Player player, Clan clan) {
        Inventory gui = Bukkit.createInventory(null, 27, RECRUIT_GUI_TITLE_ROULETTE);
        int costPerMember = plugin.getGameConfigManager().getConfig().getInt("pylon.recruitment.cost-per-member", 64);
        int totalCost = clan.getMembers().size() * costPerMember;

        ItemStack startButton = new ItemBuilder(Material.DIAMOND)
                .withName("§a팀원 무작위 뽑기")
                .addLoreLine("§7클릭하여 무작위로 팀원을 뽑습니다.")
                .addLoreLine("§f비용: §b다이아몬드 " + totalCost + "개")
                .withPDCString(BeaconGUIManager.GUI_BUTTON_KEY, "start_random_draw")                .build();

        gui.setItem(13, startButton);
        player.openInventory(gui);
    }

    public void handleGuiClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        String viewTitle = event.getView().getTitle();

        if (clickedItem == null || !clickedItem.hasItemMeta()) return;
        ItemMeta meta = clickedItem.getItemMeta();

        // 룰렛 시작 버튼 클릭 처리
        if (meta.getPersistentDataContainer().has(BeaconGUIManager.GUI_BUTTON_KEY, PersistentDataType.STRING)) {
            String action = meta.getPersistentDataContainer().get(BeaconGUIManager.GUI_BUTTON_KEY, PersistentDataType.STRING);
            if ("start_random_draw".equals(action)) {
                event.setCancelled(true); // 버튼 클릭 이벤트는 항상 취소
                handleRouletteStartClick(player, event.getInventory());
                return;
            }
        }

        // 플레이어 머리 클릭 처리
        if (clickedItem.getType() == Material.PLAYER_HEAD && meta instanceof SkullMeta) {
            // 선택 모드 GUI에서만 영입이 가능하도록 제목을 확인
            if (RECRUIT_GUI_TITLE_SELECT.equals(viewTitle)) {
                OfflinePlayer target = ((SkullMeta) meta).getOwningPlayer();
                if (target != null) {
                    event.setCancelled(true);
                    handlePlayerSelectionClick(player, target);
                }
            } else if (RECRUIT_GUI_TITLE_ROULETTE.equals(viewTitle)) {
                // 룰렛 GUI에서는 머리를 클릭해도 아무 일도 일어나지 않도록 이벤트를 취소.
                event.setCancelled(true);
            }
        }
    }

    private void handlePlayerSelectionClick(Player player, OfflinePlayer targetPlayer) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;

        UUID targetUUID = targetPlayer.getUniqueId();

        // 대상이 이미 다른 가문에 속해 있는지 다시 확인
        if (plugin.getClanManager().getClanByPlayer(targetUUID) != null) {
            player.sendMessage(PREFIX + "§c" + targetPlayer.getName() + "님은 이미 다른 가문에 소속되어 있습니다.");
            player.closeInventory();
            return;
        }

        // 비용 확인
        int costPerMember = plugin.getGameConfigManager().getConfig().getInt("pylon.recruitment.cost-per-member", 64);
        int totalCost = clan.getMembers().size() * costPerMember;

        if (!player.getInventory().contains(Material.DIAMOND, totalCost)) {
            player.sendMessage(PREFIX + "§c팀원 모집에 필요한 다이아몬드가 부족합니다. (필요: " + totalCost + "개)");
            player.closeInventory();
            return;
        }

        // 모든 조건 통과, 영입 진행
        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, totalCost));
        plugin.getClanManager().addMemberToClan(clan, targetUUID);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        player.sendMessage(PREFIX + "§a" + targetPlayer.getName() + "님을 새로운 가문원으로 영입했습니다!");
        if (targetPlayer.isOnline()) {
            targetPlayer.getPlayer().sendMessage(PREFIX + "§a" + clan.getColor() + clan.getName() + "§a 가문에 영입되었습니다!");
        }
        player.closeInventory();
    }

    private void handleRouletteStartClick(Player player, Inventory gui) {
        if (playersInRecruitment.contains(player.getUniqueId())) {
            return; // 이미 룰렛 진행 중
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) return;

        int costPerMember = plugin.getGameConfigManager().getConfig().getInt("pylon.recruitment.cost-per-member", 64);
        int totalCost = clan.getMembers().size() * costPerMember;

        if (!player.getInventory().contains(Material.DIAMOND, totalCost)) {
            player.sendMessage(PREFIX + "§c팀원 뽑기에 필요한 다이아몬드가 부족합니다. (필요: " + totalCost + "개)");
            player.closeInventory();
            return;
        }

        List<UUID> recruitable = plugin.getPlayerRegistryManager().getRecruitablePlayerUUIDs();
        recruitable.remove(player.getUniqueId());
        recruitable.removeAll(clan.getMembers());

        if (recruitable.isEmpty()) {
            player.sendMessage(PREFIX + "§c모집할 수 있는 플레이어가 없습니다.");
            player.closeInventory();
            return;
        }

        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, totalCost));
        startSlotMachineAnimation(player, gui, recruitable);
    }

    private void startSlotMachineAnimation(Player player, Inventory gui, List<UUID> recruitable) {
        if (playersInRecruitment.contains(player.getUniqueId())) return;
        playersInRecruitment.add(player.getUniqueId());
        gui.clear(); // "시작" 버튼 제거

        // --- Animation Parameters ---
        final int ROULETTE_START_SLOT = 9;
        final int ROULETTE_SLOTS = 9;
        final int CENTER_SLOT = 13;

        // --- Animation Timing ---
        final int TOTAL_DURATION_TICKS = 100; // 총 5초
        final int FINAL_SLOWDOWN_TICKS = 40; // 마지막 2초는 최종 감속 및 결과 표시

        // --- Pre-determine Winner ---
        final UUID finalRecruitUUID = selectPlayerByWeightedRoulette(recruitable);
        if (finalRecruitUUID == null) {
            player.sendMessage(PREFIX + "§c모집할 플레이어를 선택하는 중 오류가 발생했습니다.");
            playersInRecruitment.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }
        final OfflinePlayer finalRecruit = Bukkit.getOfflinePlayer(finalRecruitUUID);

        // --- Start Animation ---
        new BukkitRunnable() {
            private int ticks = 0;
            private int interval = 2; // 초기 빠른 속도

            @Override
            public void run() {
                // --- Stop Condition ---
                if (ticks >= TOTAL_DURATION_TICKS) {
                    this.cancel();
                    finalizeRecruitment(player, gui, finalRecruit);
                    return;
                }

                // --- Adjust Speed (Multi-stage) ---
                if (ticks > TOTAL_DURATION_TICKS - (FINAL_SLOWDOWN_TICKS / 2)) { // 마지막 1초
                    interval = 8;
                } else if (ticks > TOTAL_DURATION_TICKS - FINAL_SLOWDOWN_TICKS) { // 마지막 2초
                    interval = 5;
                } else if (ticks > 40) { // 2초 후
                    interval = 3;
                }

                // --- Animate ---
                if (ticks % interval == 0) {
                    // Shift items from right to left
                    for (int i = ROULETTE_START_SLOT; i < ROULETTE_START_SLOT + ROULETTE_SLOTS - 1; i++) {
                        gui.setItem(i, gui.getItem(i + 1));
                    }

                    // Add a new random head on the right
                    UUID randomUUID = recruitable.get(new Random().nextInt(recruitable.size()));
                    gui.setItem(ROULETTE_START_SLOT + ROULETTE_SLOTS - 1, createPlayerHead(Bukkit.getOfflinePlayer(randomUUID), "§b???", "§7..."));

                    // Play sound
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finalizeRecruitment(Player player, Inventory gui, OfflinePlayer finalRecruit) {
        // 최종 결과를 중앙에 표시
        gui.setItem(13, createPlayerHead(finalRecruit, "§a§l" + finalRecruit.getName() + "!", "§e팀원으로 영입되었습니다!"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

        // 가문원 추가 로직
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan != null) {
            plugin.getClanManager().addMemberToClan(clan, finalRecruit.getUniqueId());
            player.sendMessage(PREFIX + "§a" + finalRecruit.getName() + "님을 새로운 가문원으로 영입했습니다!");
            if (finalRecruit.isOnline()) {
                finalRecruit.getPlayer().sendMessage(PREFIX + "§a" + clan.getColor() + clan.getName() + "§a 가문에 영입되었습니다!");
            }
        }

        playersInRecruitment.remove(player.getUniqueId());

        // 2초 후 GUI 닫기
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.getOpenInventory().getTitle().equals(RECRUIT_GUI_TITLE_ROULETTE)) {
                    player.closeInventory();
                }
            }
        }.runTaskLater(plugin, 40L);
    }

    private ItemStack createRecruitablePlayerHead(OfflinePlayer player, int cost) {
        StatsManager statsManager = plugin.getStatsManager();
        PlayerStats stats = statsManager.getPlayerStats(player.getUniqueId());

        ItemBuilder builder = new ItemBuilder(Material.PLAYER_HEAD)
                .withSkullOwner(player)
                .withName("§a" + player.getName())
                .addLoreLine(" ")
                .addLoreLine("§7" + StatType.ATTACK.getDisplayName() + ": " + StatsEditor.getStars(stats.getStat(StatType.ATTACK)))
                .addLoreLine("§7" + StatType.INTELLIGENCE.getDisplayName() + ": " + StatsEditor.getStars(stats.getStat(StatType.INTELLIGENCE)))
                .addLoreLine("§7" + StatType.STAMINA.getDisplayName() + ": " + StatsEditor.getStars(stats.getStat(StatType.STAMINA)))
                .addLoreLine("§7" + StatType.ENTERTAINMENT.getDisplayName() + ": " + StatsEditor.getStars(stats.getStat(StatType.ENTERTAINMENT)))
                .addLoreLine(" ")
                .addLoreLine("§e전투력: " + String.format("%.2f", stats.getCombatPower()));
        builder.addLoreLine(" ");
        builder.addLoreLine("§e좌클릭하여 팀원으로 영입");
        builder.addLoreLine("§f비용: §b다이아몬드 " + cost + "개");
        return builder.build();
    }

    private ItemStack createPlayerHead(OfflinePlayer player, String name, String... lore) {
        return new ItemBuilder(Material.PLAYER_HEAD)
                .withSkullOwner(player)
                .withName(name)
                .withLore(lore)
                .build();
    }

    /**
     * 전투력에 기반한 가중치 랜덤으로 플레이어를 선택합니다.
     * 전투력이 낮을수록 선택될 확률이 높아집니다.
     * @param recruitable 모집 가능한 플레이어 목록
     * @return 선택된 플레이어의 UUID
     */
    private UUID selectPlayerByWeightedRoulette(List<UUID> recruitable) {
        if (recruitable == null || recruitable.isEmpty()) {
            return null;
        }
        if (recruitable.size() == 1) {
            return recruitable.get(0);
        }

        StatsManager statsManager = plugin.getStatsManager();
        Map<UUID, Double> weights = new HashMap<>();
        double totalWeight = 0.0;

        for (UUID uuid : recruitable) {
            PlayerStats stats = statsManager.getPlayerStats(uuid);
            // 전투력이 낮을수록 높은 가중치를 부여 (밸런스 목적)
            double weight = 1.0 / (stats.getCombatPower() + 1.0); // 0으로 나누는 것을 방지
            weights.put(uuid, weight);
            totalWeight += weight;
        }

        double randomValue = new Random().nextDouble() * totalWeight;

        for (Map.Entry<UUID, Double> entry : weights.entrySet()) {
            randomValue -= entry.getValue();
            if (randomValue <= 0) {
                return entry.getKey();
            }
        }
        return recruitable.get(0); // Fallback
    }
}