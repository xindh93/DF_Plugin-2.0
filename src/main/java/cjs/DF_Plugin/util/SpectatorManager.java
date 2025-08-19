package cjs.DF_Plugin.util;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;

public class SpectatorManager implements Listener {

    private final DF_Main plugin;
    private BukkitTask spectatorCheckTask;

    private static final String PLAYER_SELECTOR_ITEM_NAME = "§a플레이어 관전";
    private static final Material PLAYER_SELECTOR_ITEM_MATERIAL = Material.COMPASS;
    private static final String PLAYER_SELECTION_GUI_TITLE = "§l플레이어 선택";

    public SpectatorManager(DF_Main plugin) {
        this.plugin = plugin;
        startSpectatorCheckTask();
    }

    /**
     * 플레이어를 제한된 관전자 모드로 설정합니다.
     * - 게임 모드를 SPECTATOR로 변경합니다.
     * - 핫바에 플레이어 선택 아이템을 지급합니다.
     * - 다른 플레이어를 관전하도록 시도합니다.
     * @param player 관전자 모드로 설정할 플레이어
     */
    public void setRestrictedSpectator(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();
        player.getInventory().setItem(4, getPlayerSelectorItem()); // 핫바 중앙에 나침반 지급
        player.sendMessage("§7관전 모드에서는 핫바의 나침반으로 다른 플레이어를 관전할 수 있습니다.");

        // 즉시 다른 플레이어를 관전하도록 시도합니다.
        boolean hasTarget = trySetSpectatorTarget(player);

        if (!hasTarget) {
            // 관전할 대상이 없을 경우, 현재 위치에 고정시키기 위해
            // 플레이어를 자신의 위치로 다시 텔레포트하여 상태를 갱신합니다.
            // 이렇게 하면 onPlayerMove 이벤트가 즉시 올바르게 작동합니다.
            player.teleport(player.getLocation());
        }
    }

    /**
     * 관전자 모드에서 자유로운 이동을 제한합니다.
     * 다른 플레이어를 관전하고 있지 않을 경우 움직임을 취소합니다.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR && player.getSpectatorTarget() == null) {
            // 플레이어가 자유롭게 움직이는 관전자 모드일 경우 이동을 취소합니다.
            // 단, 블록 단위 이동이 아니면 취소하지 않아 부드러운 시점 전환을 방해하지 않습니다.
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                event.setCancelled(true);
                player.sendMessage("§c다른 플레이어를 관전 중이 아닐 때는 자유롭게 움직일 수 없습니다.");
            }
        }
    }

    /**
     * 핫바 아이템 클릭 시 플레이어 선택 GUI를 엽니다.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (player.getGameMode() != GameMode.SPECTATOR || item == null) {
            return;
        }

        // 관전자가 스폰알을 사용하지 못하도록 막습니다.
        if (item.getType().name().endsWith("_SPAWN_EGG")) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                player.sendMessage("§c관전자 모드에서는 몬스터를 스폰할 수 없습니다.");
                event.setCancelled(true);
            }
            return; // 스폰알 사용 시도는 여기서 종료
        }

        if (isPlayerSelectorItem(item)) {
            event.setCancelled(true); // 아이템 사용 이벤트 취소
            player.openInventory(createPlayerSelectionGUI());
        }
    }

    /**
     * 플레이어 선택 GUI에서 플레이어 클릭 시 해당 플레이어를 관전합니다.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle().equals(PLAYER_SELECTION_GUI_TITLE)) {
            event.setCancelled(true); // GUI 아이템 이동 방지

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() != Material.PLAYER_HEAD) return;

            SkullMeta meta = (SkullMeta) clickedItem.getItemMeta();
            if (meta == null || meta.getOwningPlayer() == null || meta.getOwningPlayer().getPlayer() == null) return;

            Player targetPlayer = meta.getOwningPlayer().getPlayer();
            if (targetPlayer.equals(player)) {
                player.sendMessage("§c자기 자신을 관전할 수는 없습니다.");
                return;
            }

            player.setSpectatorTarget(targetPlayer);
            player.sendMessage("§a" + targetPlayer.getName() + "님을 관전합니다.");
            player.closeInventory();
        }
    }

    /**
     * 관전 중이던 플레이어가 로그아웃할 경우, 다른 플레이어를 관전하도록 시도합니다.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player disconnectedPlayer = event.getPlayer();
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SPECTATOR && disconnectedPlayer.equals(p.getSpectatorTarget()))
                .forEach(spectator -> {
                    // 관전 대상이 나갔으므로, 다른 대상을 찾거나 위치를 고정합니다.
                    if (!trySetSpectatorTarget(spectator)) {
                        spectator.teleport(spectator.getLocation());
                    }
                });
    }

    /**
     * 게임 모드가 SPECTATOR로 변경될 때, 자동으로 관전 대상을 지정합니다.
     */
    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            // 다음 틱에 관전 대상을 설정하여, 게임 모드 변경이 완전히 적용된 후 실행되도록 합니다.
            new BukkitRunnable() {
                @Override
                public void run() {
                    setRestrictedSpectator(event.getPlayer());
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    private ItemStack getPlayerSelectorItem() {
        ItemStack item = new ItemStack(PLAYER_SELECTOR_ITEM_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(PLAYER_SELECTOR_ITEM_NAME);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isPlayerSelectorItem(ItemStack item) {
        return item.getType() == PLAYER_SELECTOR_ITEM_MATERIAL &&
               item.hasItemMeta() &&
               item.getItemMeta().hasDisplayName() &&
               item.getItemMeta().getDisplayName().equals(PLAYER_SELECTOR_ITEM_NAME);
    }

    private Inventory createPlayerSelectionGUI() {
        Inventory inv = Bukkit.createInventory(null, 27, PLAYER_SELECTION_GUI_TITLE);
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
            meta.setOwningPlayer(onlinePlayer);
            meta.setDisplayName("§b" + onlinePlayer.getName());
            playerHead.setItemMeta(meta);
            inv.addItem(playerHead);
        }
        return inv;
    }

    private boolean trySetSpectatorTarget(Player spectator) {
        Optional<? extends Player> targetPlayer = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(spectator)) // 자기 자신은 제외
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR) // 다른 관전자는 관전하지 않음
                .findAny(); // 아무 다른 플레이어
        targetPlayer.ifPresent(spectator::setSpectatorTarget);
        return targetPlayer.isPresent();
    }

    /**
     * 주기적으로 관전자 모드 플레이어가 다른 플레이어를 관전하고 있는지 확인하고,
     * 그렇지 않다면 관전 대상을 지정합니다.
     */
    private void startSpectatorCheckTask() {
        if (spectatorCheckTask != null) {
            spectatorCheckTask.cancel();
        }
        spectatorCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.SPECTATOR && player.getSpectatorTarget() == null) {
                        trySetSpectatorTarget(player); // 주기적으로 관전할 플레이어를 찾아봅니다.
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1초마다 실행
    }

    public void stopSpectatorCheckTask() {
        if (spectatorCheckTask != null) {
            spectatorCheckTask.cancel();
            spectatorCheckTask = null;
        }
    }
}