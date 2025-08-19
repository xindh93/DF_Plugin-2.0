package cjs.DF_Plugin.events.end;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.enchant.MagicStone;
import cjs.DF_Plugin.items.UpgradeItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldCreator;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class EndEventManager {

    private final DF_Main plugin;
    private boolean isEndOpen;
    private long scheduledOpenTime = -1;
    private BukkitTask openTask;
    private BukkitTask collapseUpdateTask;
    private BossBar collapseBossBar;
    private long collapseEndTime = -1;

    private static final String CONFIG_PATH_IS_OPEN = "end-event.is-open";
    private static final String CONFIG_PATH_SCHEDULED_TIME = "end-event.scheduled-open-time";
    private static final String CONFIG_PATH_COLLAPSE_TIME = "end-event.collapse-delay-minutes";
    private static final String CONFIG_PATH_COLLAPSE_END_TIME = "end-event.collapse-end-time";

    public EndEventManager(DF_Main plugin) {
        this.plugin = plugin;
        loadState();
    }

    /**
     * 서버 시작 시 config.yml에서 엔드 이벤트의 상태(개방, 예약, 붕괴)를 불러옵니다.
     */
    public void loadState() {
        this.isEndOpen = plugin.getGameConfigManager().getConfig().getBoolean(CONFIG_PATH_IS_OPEN, false);
        this.scheduledOpenTime = plugin.getGameConfigManager().getConfig().getLong(CONFIG_PATH_SCHEDULED_TIME, -1);
        this.collapseEndTime = plugin.getGameConfigManager().getConfig().getLong(CONFIG_PATH_COLLAPSE_END_TIME, -1);

        if (collapseEndTime != -1) {
            long remainingMillis = collapseEndTime - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                // 서버가 꺼져있는 동안 붕괴 시간이 지났으므로, 즉시 닫습니다.
                closeAndResetEnd();
            } else {
                // 붕괴 카운트다운을 다시 시작합니다.
                long totalDurationMinutes = plugin.getGameConfigManager().getConfig().getLong(CONFIG_PATH_COLLAPSE_TIME, 10);
                Bukkit.broadcastMessage("§5[엔드 이벤트] §c엔드 월드 붕괴가 재개됩니다! 서둘러 탈출하세요!");
                startCollapseCountdown(totalDurationMinutes);
            }
        } else if (scheduledOpenTime != -1) {
            long delayMillis = scheduledOpenTime - System.currentTimeMillis();
            if (delayMillis <= 0) {
                // 서버가 꺼져있는 동안 열릴 시간이었으므로, 지금 바로 엽니다.
                openEnd(false);
            } else {
                // 개방 예약을 다시 설정합니다.
                scheduleOpen(TimeUnit.MILLISECONDS.toMinutes(delayMillis), false);
            }
        }
    }

    private void saveState() {
        plugin.getGameConfigManager().getConfig().set(CONFIG_PATH_IS_OPEN, isEndOpen);
        plugin.getGameConfigManager().getConfig().set(CONFIG_PATH_SCHEDULED_TIME, scheduledOpenTime);
        plugin.getGameConfigManager().getConfig().set(CONFIG_PATH_COLLAPSE_END_TIME, collapseEndTime);
        plugin.getGameConfigManager().save();
    }

    public void openEnd(boolean broadcast) {
        if (isEndOpen) return;

        // 엔드를 열기 전에 항상 월드를 초기화하여 새로운 경험을 제공합니다.
        plugin.getLogger().info("Resetting The End before opening...");
        plugin.getWorldManager().resetWorld("world_the_end");

        // 월드가 로드되었는지 확인하고, 그렇지 않으면 생성합니다.
        World endWorld = Bukkit.getWorld("world_the_end");
        if (endWorld == null) {
            plugin.getLogger().info("The End world is not loaded, creating it now...");
            endWorld = new WorldCreator("world_the_end")
                    .environment(World.Environment.THE_END)
                    .createWorld();
            if (endWorld == null) {
                plugin.getLogger().severe("엔드 월드를 생성하는 데 실패했습니다!");
                Bukkit.broadcastMessage("§c[오류] 엔드 월드를 생성하는 데 실패했습니다. 서버 관리자에게 문의하세요.");
                return;
            }
        }

        if (openTask != null) {
            openTask.cancel();
            openTask = null;
        }

        this.isEndOpen = true;
        this.scheduledOpenTime = -1;
        saveState();

        if (broadcast) {
            Bukkit.broadcastMessage("§5[엔드 이벤트] §d엔더 드래곤의 포효가 들려옵니다! 엔드 포탈이 활성화되었습니다!");
            // 모든 플레이어에게 엔드 포탈 활성화 소리를 재생합니다.
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.0f);
            }
        }
        plugin.getLogger().info("The End has been opened.");
    }

    public void scheduleOpen(long minutes, boolean broadcast) {
        if (minutes <= 0) {
            openEnd(true);
            return;
        }

        if (openTask != null) {
            openTask.cancel();
        }

        this.isEndOpen = false;
        this.scheduledOpenTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes);
        saveState();

        if (broadcast) {
            Bukkit.broadcastMessage("§5[엔드 이벤트] §d공허의 기운이 꿈틀거립니다... " + minutes + "분 뒤 엔드 포탈이 열립니다!");
        }

        openTask = Bukkit.getScheduler().runTaskLater(plugin, () -> openEnd(true), minutes * 60 * 20L);
        plugin.getLogger().info("The End is scheduled to open in " + minutes + " minutes.");
    }

    /**
     * 관리자 명령 등으로 엔드를 강제로 닫고 초기화합니다.
     */
    public void forceCloseEnd() {
        // 붕괴 중이 아니더라도 관련 상태를 모두 초기화하기 위해 closeAndResetEnd를 호출합니다.
        Bukkit.broadcastMessage("§5[엔드 이벤트] §4관리자에 의해 엔드 월드가 강제로 닫혔습니다.");
        closeAndResetEnd();
    }

    public boolean isEndOpen() {
        return isEndOpen;
    }

    /**
     * 엔더 드래곤 처치 시 보상 지급 및 월드 붕괴 절차를 시작합니다.
     */
    public void triggerDragonDefeatSequence() {
        if (!isEndOpen) return;

        // 드래곤이 죽는 즉시 엔드를 닫아 더 이상의 진입을 막습니다.
        // 붕괴 카운트다운은 계속 진행됩니다.
        this.isEndOpen = false;
        Bukkit.broadcastMessage("§5[엔드 이벤트] §c엔더 드래곤이 쓰러져 엔드 포탈이 닫혔습니다!");

        World endWorld = Bukkit.getWorld("world_the_end");
        if (endWorld != null) {
            scatterRewards(endWorld);
            Bukkit.broadcastMessage("§5[엔드 이벤트] §d엔더 드래곤이 쓰러졌습니다! 하늘에서 보상이 쏟아집니다!");
        } else {
            plugin.getLogger().log(Level.WARNING, "엔드 월드가 로드되지 않아 보상을 지급할 수 없습니다.");
        }

        long delayMinutes = plugin.getGameConfigManager().getConfig().getLong(CONFIG_PATH_COLLAPSE_TIME, 10);
        Bukkit.broadcastMessage("§5[엔드 이벤트] §c엔드 월드가 " + delayMinutes + "분 뒤 붕괴를 시작합니다! 서둘러 탈출하세요!");

        this.collapseEndTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes);
        saveState();
        startCollapseCountdown(delayMinutes);
    }

    /**
     * 붕괴 카운트다운과 보스바 업데이트를 시작합니다.
     * @param totalDurationMinutes 붕괴까지 총 소요 시간(분)
     */
    private void startCollapseCountdown(long totalDurationMinutes) {
        if (collapseUpdateTask != null) {
            collapseUpdateTask.cancel();
        }
        if (collapseBossBar != null) {
            collapseBossBar.removeAll();
        }

        collapseBossBar = Bukkit.createBossBar("§c엔드 월드 붕괴까지...", BarColor.RED, BarStyle.SOLID);
        collapseBossBar.setVisible(true);

        World endWorld = Bukkit.getWorld("world_the_end");
        if (endWorld != null) {
            endWorld.getPlayers().forEach(collapseBossBar::addPlayer);
        }

        final long totalDurationMillis = TimeUnit.MINUTES.toMillis(totalDurationMinutes);

        collapseUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                long remainingMillis = collapseEndTime - System.currentTimeMillis();

                if (remainingMillis <= 0) {
                    closeAndResetEnd();
                    this.cancel();
                    return;
                }

                double progress = (double) remainingMillis / totalDurationMillis;
                collapseBossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1초마다 업데이트
    }

    private void closeAndResetEnd() {
        if (collapseUpdateTask != null) {
            collapseUpdateTask.cancel();
            collapseUpdateTask = null;
        }
        if (collapseBossBar != null) {
            collapseBossBar.removeAll();
            collapseBossBar.setVisible(false);
            collapseBossBar = null;
        }
        // 예약된 개방 작업이 있다면 취소합니다.
        if (openTask != null) {
            openTask.cancel();
            openTask = null;
        }

        World endWorld = Bukkit.getWorld("world_the_end");
        if (endWorld != null) {
            for (Player player : endWorld.getPlayers()) {
                teleportPlayerToSafety(player);
            }
        }

        this.isEndOpen = false;
        this.scheduledOpenTime = -1;
        this.collapseEndTime = -1;
        saveState();

        Bukkit.broadcastMessage("§5[엔드 이벤트] §4엔드 월드가 붕괴하여 닫혔습니다.");
        plugin.getLogger().info("The End has been closed and is being reset.");

        plugin.getWorldManager().resetWorld("world_the_end");
    }

    public void teleportPlayerToSafety(Player player) {
        plugin.getWorldManager().teleportPlayerToSafety(player);
    }

    /**
     * 현재 엔드 월드가 붕괴 중인지 확인합니다.
     * @return 붕괴 중이면 true
     */
    public boolean isCollapsing() {
        return this.collapseEndTime != -1;
    }

    /**
     * 특정 플레이어를 붕괴 보스바에 추가합니다.
     * @param player 추가할 플레이어
     */
    public void addPlayerToCollapseBar(Player player) {
        if (collapseBossBar != null && isCollapsing()) {
            collapseBossBar.addPlayer(player);
        }
    }

    public void removePlayerFromCollapseBar(Player player) {
        if (collapseBossBar != null) {
            collapseBossBar.removePlayer(player);
        }
    }

    /**
     * 엔더 드래곤 처치 후 설정된 위치에 보상을 뿌립니다.
     * @param world 보상을 뿌릴 월드 (엔드 월드)
     */
    public void scatterRewards(World world) {
        FileConfiguration config = plugin.getGameConfigManager().getConfig();
        if (world.getEnvironment() != World.Environment.THE_END) {
            plugin.getLogger().warning("보상 뿌리기는 엔드 월드에서만 가능합니다.");
            return;
        }

        // 설정 값 읽기
        int areaSize = config.getInt("end-event.rewards.area-size", 100);
        int dropY = config.getInt("end-event.rewards.drop-y-level", 200);
        int minStack = config.getInt("end-event.rewards.min-stack-size", 2);
        int maxStack = config.getInt("end-event.rewards.max-stack-size", 4);
        int minTotal = config.getInt("end-event.rewards.min-total-quantity", 1000);
        int maxTotal = config.getInt("end-event.rewards.max-total-quantity", 2000);

        int totalItemsToDrop = ThreadLocalRandom.current().nextInt(minTotal, maxTotal + 1);
        int itemsDropped = 0;

        plugin.getLogger().info(String.format("엔더 드래곤 보상을 뿌립니다. 총 %d개", totalItemsToDrop));

        while (itemsDropped < totalItemsToDrop) {
            // 랜덤 위치 생성
            double x = ThreadLocalRandom.current().nextDouble(-areaSize / 2.0, areaSize / 2.0);
            double z = ThreadLocalRandom.current().nextDouble(-areaSize / 2.0, areaSize / 2.0);
            Location dropLocation = new Location(world, x, dropY, z);

            // 랜덤 스택 크기 결정
            int stackSize = ThreadLocalRandom.current().nextInt(minStack, maxStack + 1);
            if (itemsDropped + stackSize > totalItemsToDrop) {
                stackSize = totalItemsToDrop - itemsDropped;
            }

            // 50% 확률로 마석 또는 강화석 선택
            ItemStack rewardItem;
            if (ThreadLocalRandom.current().nextBoolean()) {
                rewardItem = MagicStone.createMagicStone(stackSize);
            } else {
                rewardItem = UpgradeItems.createUpgradeStone(stackSize);
            }

            // 아이템 드롭 및 발광 효과 적용
            Item droppedItemEntity = world.dropItemNaturally(dropLocation, rewardItem);
            droppedItemEntity.setGlowing(true);
            droppedItemEntity.setUnlimitedLifetime(true); // 아이템이 사라지지 않도록 설정

            itemsDropped += stackSize;
        }
    }
}