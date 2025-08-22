package cjs.DF_Plugin.events.rift;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class RiftManager {

    private final DF_Main plugin;
    private boolean isEventActive = false;
    private Location altarLocation;
    private final Map<Location, BlockData> originalBlocks = new HashMap<>();
    private BossBar altarStateBossBar;
    private BukkitTask altarStateUpdateTask;
    private double breakingProgress = 1.0;

    // 이벤트 상태 저장을 위한 키
    private static final String CONFIG_PATH_ROOT = "events.rift.";
    private static final String CONFIG_PATH_ACTIVE = CONFIG_PATH_ROOT + "active";
    private static final String CONFIG_PATH_LOCATION = CONFIG_PATH_ROOT + "location";
    private static final String CONFIG_PATH_START_TIME = CONFIG_PATH_ROOT + "start-time";

    public RiftManager(DF_Main plugin) {
        this.plugin = plugin;
        loadState();
    }

    private void loadState() {
        FileConfiguration eventConfig = plugin.getEventDataManager().getConfig();
        this.isEventActive = eventConfig.getBoolean(CONFIG_PATH_ACTIVE, false);

        if (isEventActive) {
            plugin.getLogger().warning("[차원의 균열] 이전 이벤트가 활성 상태로 남아있습니다. 이벤트를 재개합니다...");
            String locString = eventConfig.getString(CONFIG_PATH_LOCATION);
            if (locString == null) {
                plugin.getLogger().severe("[차원의 균열] 활성 이벤트 위치 데이터가 손상되었습니다. 이벤트를 강제 종료합니다.");
                cleanupAltar();
                return;
            }
            this.altarLocation = cjs.DF_Plugin.util.PluginUtils.deserializeLocation(locString);
            long startTime = eventConfig.getLong(CONFIG_PATH_START_TIME);

            // Resume the event
            activateAltar(startTime);

        } else if (eventConfig.contains(CONFIG_PATH_ROOT + "altar-blocks")) {
            // If event is not marked as active, but the data file exists, it's a leftover. Clean it up.
            plugin.getLogger().warning("[차원의 균열] 이전 이벤트에서 남은 제단 데이터를 발견했습니다. 정리를 시작합니다...");
            cleanupAltarFromConfig();
        }
    }

    public void triggerEvent() {
        if (isEventActive) {
            return;
        }

        World world = Bukkit.getWorlds().get(0);
        if (world.getEnvironment() != World.Environment.NORMAL) {
            plugin.getLogger().warning("[차원의 균열] 차원의 균열 이벤트는 오버월드에서만 발생할 수 있습니다.");
            return;
        }

        Location groundLocation = findOrPrepareLocation(world);
        if (groundLocation == null) {
            plugin.getLogger().severe("[차원의 균열] 제단 생성 위치를 준비하는 데 실패했습니다. 이벤트가 취소됩니다.");
            return;
        }

        this.isEventActive = true;
        this.altarLocation = groundLocation.clone().add(0, 3, 0); // 알 위치를 기준으로 저장

        // 1단계: 알을 제외한 제단만 먼저 생성합니다.
        spawnAltar(groundLocation, false);

        // 이벤트 상태를 event_data.yml에 저장
        FileConfiguration eventConfig = plugin.getEventDataManager().getConfig();
        eventConfig.set(CONFIG_PATH_ACTIVE, true);
        eventConfig.set(CONFIG_PATH_LOCATION, cjs.DF_Plugin.util.PluginUtils.serializeLocation(this.altarLocation));
        long startTime = System.currentTimeMillis();
        eventConfig.set(CONFIG_PATH_START_TIME, startTime);
        plugin.getEventDataManager().saveConfig();

        saveAltarData(); // 제단 블록 구조 저장

        Bukkit.broadcastMessage("§d[차원의 균열] §f차원의 어딘가가 불안정합니다.");
        Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f));
        plugin.getLogger().info("[차원의 균열] 제단 생성 성공. 위치: " + groundLocation.getBlockX() + ", " + groundLocation.getBlockY() + ", " + groundLocation.getBlockZ());

        // 제단 활성화 및 보스바 시작
        activateAltar(startTime);
    }

    private Location findOrPrepareLocation(World world) {
        int maxAttempts = 25;
        Location candidateLocation = null;

        for (int i = 0; i < maxAttempts; i++) {
            candidateLocation = getRandomSafeLocation(world);
            Location highestBlockLoc = world.getHighestBlockAt(candidateLocation).getLocation();
            Block groundBlock = highestBlockLoc.getBlock();

            // Check if the ground is suitable
            if (!groundBlock.isLiquid() && groundBlock.getType() != Material.AIR && !groundBlock.getType().toString().contains("LEAVES")) {
                plugin.getLogger().info("[차원의 균열] " + (i + 1) + "번 시도 후 적절한 제단 위치를 찾았습니다: " + highestBlockLoc);
                return highestBlockLoc;
            }
        }

        // If loop finishes, no "perfect" spot was found. Prepare the last candidate location.
        plugin.getLogger().warning("[차원의 균열] " + maxAttempts + "번 시도 후에도 적절한 제단 위치를 찾지 못했습니다. 마지막 후보 위치에 플랫폼을 생성합니다.");
        Location groundLocation = world.getHighestBlockAt(candidateLocation).getLocation();

        // Create a 5x5 platform
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block platformBlock = groundLocation.clone().add(dx, 0, dz).getBlock();
                originalBlocks.put(platformBlock.getLocation(), platformBlock.getBlockData());
                platformBlock.setType(Material.COBBLESTONE);
            }
        }
        plugin.getLogger().info("[차원의 균열] 플랫폼 생성 완료: " + groundLocation);
        return groundLocation;
    }

    private void spawnAltar(Location groundLocation, boolean withEgg) {
        // 제단 구조의 기준점 설정
        // altarLocation은 항상 드래곤 알의 위치를 가리킵니다.
        Location eggLocation = this.altarLocation;
        Location beaconLocation = eggLocation.clone().subtract(0, 2, 0); // 신호기는 알보다 2블록 아래
        int worldMaxHeight = eggLocation.getWorld().getMaxHeight();

        // --- 1. 제단이 지어질 모든 위치의 '원래' 블록 데이터를 미리 저장 ---
        // 네더라이트 층 (알 위치 -3)
        saveOriginalBlock(eggLocation.clone().subtract(0, 3, 0), 1, 0, 1);
        // 신호기 층 (알 위치 -2)
        saveOriginalBlock(eggLocation.clone().subtract(0, 2, 0), 1, 0, 1);
        // 계단 층 (알 위치 -1)
        saveOriginalBlock(eggLocation.clone().subtract(0, 1, 0), 1, 0, 1);
        if (withEgg) {
            // 알 위치
            saveOriginalBlock(eggLocation, 0, 0, 0);
            // 알 위쪽 공기층
            for (int y = eggLocation.getBlockY() + 1; y < worldMaxHeight; y++) {
                Location locAbove = new Location(eggLocation.getWorld(), eggLocation.getX(), y, eggLocation.getZ());
                originalBlocks.put(locAbove.getBlock().getLocation(), locAbove.getBlock().getBlockData());
            }
        }

        // --- 2. 실제 제단 건설 ---
        // 1. 네더라이트 3x3 플랫폼 (알 위치 -3)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                beaconLocation.clone().add(x, -1, z).getBlock().setType(Material.NETHERITE_BLOCK);
            }
        }

        // 2. 신호기 (알 위치 -2 중앙)
        beaconLocation.getBlock().setType(Material.BEACON);

        // 3. 보라색 유리 (알 위치 -2 주변부)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                beaconLocation.clone().add(x, 0, z).getBlock().setType(Material.PURPLE_STAINED_GLASS);
            }
        }

        // 4. 알 바로 아래 보라색 유리 (알 위치 -1 중앙)
        eggLocation.clone().add(0, -1, 0).getBlock().setType(Material.PURPLE_STAINED_GLASS);

        // 5. 이끼 낀 조약돌 계단 (알 위치 -1 주변부)
        // 모서리
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                Block stairBlock = eggLocation.clone().add(x, -1, z).getBlock();
                stairBlock.setType(Material.MOSSY_COBBLESTONE_STAIRS);
                Stairs stairData = (Stairs) stairBlock.getBlockData();
                stairData.setFacing(z == 1 ? BlockFace.NORTH : BlockFace.SOUTH);
                stairBlock.setBlockData(stairData);
            }
        }
        // 십자 방향
        setStair(eggLocation.clone().add(0,-1,0), 0, 1, BlockFace.NORTH);
        setStair(eggLocation.clone().add(0,-1,0), 0, -1, BlockFace.SOUTH);
        setStair(eggLocation.clone().add(0,-1,0), 1, 0, BlockFace.WEST);
        setStair(eggLocation.clone().add(0,-1,0), -1, 0, BlockFace.EAST);

        if (withEgg) {
            // 6. 드래곤 알 설치
            eggLocation.getBlock().setType(Material.DRAGON_EGG);

            // 7. 알 위쪽을 공기로 변경
            for (int y = eggLocation.getBlockY() + 1; y < worldMaxHeight; y++) {
                Location locAbove = new Location(eggLocation.getWorld(), eggLocation.getX(), y, eggLocation.getZ());
                locAbove.getBlock().setType(Material.AIR, false);
            }
        }
    }

    private void setStair(Location center, int dX, int dZ, BlockFace facing) {
        Block stairBlock = center.clone().add(dX, 0, dZ).getBlock();
        stairBlock.setType(Material.MOSSY_COBBLESTONE_STAIRS);
        Stairs stairData = (Stairs) stairBlock.getBlockData();
        stairData.setFacing(facing);
        stairBlock.setBlockData(stairData);
    }

    private void activateAltar(long startTime) {
        long eventDurationMillis = TimeUnit.HOURS.toMillis(plugin.getGameConfigManager().getConfig().getLong("events.rift.spawn-delay-hours", 1));

        altarStateBossBar = Bukkit.createBossBar("§d차원의 균열이 점점 커지고 있습니다...", BarColor.PURPLE, BarStyle.SOLID);
        altarStateBossBar.setProgress(1.0);
        altarStateBossBar.setVisible(true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            altarStateBossBar.addPlayer(p);
        }

        altarStateUpdateTask = new BukkitRunnable() {
            private boolean eggSpawned = altarLocation != null && altarLocation.getBlock().getType() == Material.DRAGON_EGG;

            @Override
            public void run() {
                if (!isEventActive) {
                    this.cancel();
                    return;
                }

                if (!eggSpawned) {
                    // --- PHASE ONE: Countdown to egg spawn ---
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    double progress = 1.0 - ((double) elapsedTime / eventDurationMillis);

                    if (progress <= 0) {
                        // Time to spawn the egg
                        eggSpawned = true;
                        altarLocation.getBlock().setType(Material.DRAGON_EGG);
                        altarStateBossBar.setTitle("§c이계의 알이 도착했습니다");
                        altarStateBossBar.setColor(BarColor.RED);
                        Bukkit.broadcastMessage("§d[차원의 균열] §f균열에서 강력한 기운이 감지됩니다!");
                        Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f));
                    } else {
                        // Update countdown progress
                        altarStateBossBar.setProgress(progress);
                    }
                    return; // Don't run dismantling logic yet
                }

                // --- PHASE TWO: Egg dismantling ---
                altarStateBossBar.setTitle("§c이계의 알이 도착했습니다"); // 보스바 텍스트 유지
                altarStateBossBar.setProgress(breakingProgress); // 이제 보스바는 해체 진행도를 표시

                // If the egg is somehow destroyed by other means (e.g., worldedit), end the event.
                if (altarLocation.getBlock().getType() != Material.DRAGON_EGG) {
                    Bukkit.broadcastMessage("§d[차원의 균열] §c알이 불안정하여 소멸했습니다!");
                    cleanupAltar();
                    this.cancel();
                    return;
                }
                
                Player breakingPlayer = null;
                int altarX = altarLocation.getBlockX();
                int altarZ = altarLocation.getBlockZ();
                int stairLevelY = altarLocation.getBlockY() - 1; // 계단 층의 Y 좌표

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                        Location playerStandingOnLoc = p.getLocation().getBlock().getRelative(BlockFace.DOWN).getLocation();
                        int standingX = playerStandingOnLoc.getBlockX();
                        int standingY = playerStandingOnLoc.getBlockY();
                        int standingZ = playerStandingOnLoc.getBlockZ();

                        // 3x3 해체 구역에 있는지 확인
                        boolean isInArea = Math.abs(standingX - altarX) <= 1 && Math.abs(standingZ - altarZ) <= 1;

                        if (isInArea && standingY == stairLevelY) {
                            breakingPlayer = p;
                            break;
                        }
                    }
                }

                if (breakingPlayer != null) {
                    // 해체 진행
                    long breakSeconds = plugin.getGameConfigManager().getConfig().getLong("events.rift.break-duration-seconds", 8);
                    double breakRatePerTick = 1.0 / (breakSeconds * 20.0);
                    breakingProgress = Math.max(0, breakingProgress - breakRatePerTick);

                    if (breakingProgress <= 0) {
                        handleEggBreak(breakingPlayer);
                        this.cancel();
                    }
                } else {
                    // 해체 중단, 체력 회복
                    if (breakingProgress < 1.0) {
                        double regenRatePerTick = 1.0 / (2 * 20.0); // 2초에 걸쳐 회복
                        breakingProgress = Math.min(1.0, breakingProgress + regenRatePerTick);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void handleEggBreak(Player player) {
        if (!isEventActive) return;

        isEventActive = false;
        cleanupBossBar();

        Bukkit.broadcastMessage("§d[차원의 균열] §f차원의 균열이 닫힙니다.");
        Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.6f));

        // 신호기만 파괴
        altarLocation.clone().subtract(0, 2, 0).getBlock().setType(Material.AIR);
        // 알도 제거
        altarLocation.getBlock().setType(Material.AIR);

        // 보상 드랍
        generateRewards().forEach(item ->
                altarLocation.getWorld().dropItemNaturally(altarLocation, item)
        );

        // 제단 정리 예약
        long cleanupDelayMinutes = plugin.getGameConfigManager().getConfig().getLong("events.rift.cleanup-delay-minutes", 1);
        long cleanupDelayTicks = cleanupDelayMinutes * 20L * 60L;
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupAltar();
            }
        }.runTaskLater(plugin, cleanupDelayTicks);
    }

    private void cleanupAltar() {
        cleanupBossBar();
        isEventActive = false;

        // 메모리에 있는 원본 블록 정보로 복구
        if (!originalBlocks.isEmpty()) {
            originalBlocks.forEach((loc, data) -> loc.getBlock().setBlockData(data, false));
            originalBlocks.clear();
        }

        // 이벤트 상태를 event_data.yml에서 제거
        FileConfiguration eventConfig = plugin.getEventDataManager().getConfig();
        eventConfig.set(CONFIG_PATH_ACTIVE, false);
        eventConfig.set(CONFIG_PATH_LOCATION, null);
        eventConfig.set(CONFIG_PATH_START_TIME, null);
        // 제단 블록 데이터도 함께 제거합니다.
        eventConfig.set(CONFIG_PATH_ROOT + "altar-blocks", null);
        plugin.getEventDataManager().saveConfig();

        altarLocation = null;
        plugin.getRiftScheduler().startNewCooldown(); // 이벤트가 완전히 정리된 후 쿨다운 시작
    }

    private void cleanupBossBar() {
        if (altarStateUpdateTask != null) {
            altarStateUpdateTask.cancel();
            altarStateUpdateTask = null;
        }
        if (altarStateBossBar != null) {
            altarStateBossBar.removeAll();
            altarStateBossBar.setVisible(false);
            altarStateBossBar = null;
        }
    }

    public void showBarToPlayer(Player player) {
        if (isEventActive && altarStateBossBar != null) {
            altarStateBossBar.addPlayer(player);
        }
    }

    private List<ItemStack> generateRewards() {
        List<ItemStack> possibleRewards = new ArrayList<>();
        UpgradeManager upgradeManager = plugin.getUpgradeManager();

        // 갑옷
        possibleRewards.add(upgradeManager.setItemLevel(new ItemStack(Material.DIAMOND_HELMET), 10, Enchantment.PROTECTION, 4));
        possibleRewards.add(upgradeManager.setItemLevel(new ItemStack(Material.DIAMOND_CHESTPLATE), 10, Enchantment.PROTECTION, 4));
        possibleRewards.add(upgradeManager.setItemLevel(new ItemStack(Material.DIAMOND_LEGGINGS), 10, Enchantment.PROTECTION, 4));
        possibleRewards.add(upgradeManager.setItemLevel(new ItemStack(Material.DIAMOND_BOOTS), 10, Enchantment.PROTECTION, 4));
        // 무기
        possibleRewards.add(upgradeManager.setItemLevel(new ItemStack(Material.DIAMOND_SWORD), 10, Enchantment.SHARPNESS, 5));
        possibleRewards.add(upgradeManager.setItemLevel(new ItemStack(Material.TRIDENT), 10, Enchantment.RIPTIDE, 3));
        possibleRewards.add(upgradeManager.setItemLevel(new ItemStack(Material.BOW), 10, Enchantment.POWER, 5));
        possibleRewards.add(upgradeManager.setItemLevel(new ItemStack(Material.CROSSBOW), 10, Enchantment.QUICK_CHARGE, 3));

        if (possibleRewards.isEmpty()) {
            return Collections.emptyList();
        }

        // 1개의 아이템을 무작위로 선택하여 반환
        Random random = new Random();
        ItemStack chosenReward = possibleRewards.get(random.nextInt(possibleRewards.size()));
        return Collections.singletonList(chosenReward);
    }

    public boolean isEventActive() {
        return isEventActive;
    }

    public boolean isAltarBlock(Location loc) {
        if (!isEventActive) return false;
        return originalBlocks.containsKey(loc.getBlock().getLocation());
    }

    public boolean isProtectedZone(Location loc) {
        if (!isEventActive || altarLocation == null) {
            return false;
        }
        int altarX = altarLocation.getBlockX();
        int altarZ = altarLocation.getBlockZ();
        int altarY = altarLocation.getBlockY(); // Egg Y

        return loc.getBlockX() >= altarX - 1 && loc.getBlockX() <= altarX + 1 &&
                loc.getBlockZ() >= altarZ - 1 && loc.getBlockZ() <= altarZ + 1 &&
                loc.getBlockY() >= altarY; // 제단 알 높이 이상 보호
    }

    public Location getAltarLocation() {
        return altarLocation;
    }

    private void saveOriginalBlock(Location center, int radiusX, int radiusY, int radiusZ) {
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int y = -radiusY; y <= radiusY; y++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    Location loc = center.clone().add(x, y, z);
                    originalBlocks.put(loc, loc.getBlock().getBlockData());
                }
            }
        }
    }

    private void saveAltarData() {
        if (originalBlocks.isEmpty() || altarLocation == null) return;

        FileConfiguration eventConfig = plugin.getEventDataManager().getConfig();
        String altarBlocksPath = CONFIG_PATH_ROOT + "altar-blocks";

        // 기존 데이터를 모두 지웁니다.
        eventConfig.set(altarBlocksPath, null);

        eventConfig.set(altarBlocksPath + ".world", altarLocation.getWorld().getName());
        for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
            Location loc = entry.getKey();
            // 키를 "x,y,z" 형태로 저장
            String key = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
            eventConfig.set(altarBlocksPath + ".blocks." + key, entry.getValue().getAsString());
        }
        // 저장은 triggerEvent에서 다른 상태와 함께 일괄적으로 처리됩니다.
    }

    private void cleanupAltarFromConfig() {
        FileConfiguration eventConfig = plugin.getEventDataManager().getConfig();
        String altarBlocksPath = CONFIG_PATH_ROOT + "altar-blocks";
        if (!eventConfig.contains(altarBlocksPath)) return;

        ConfigurationSection section = eventConfig.getConfigurationSection(altarBlocksPath + ".blocks");
        String worldName = eventConfig.getString(altarBlocksPath + ".world");

        if (section == null || worldName == null) {
            plugin.getLogger().severe("[차원의 균열] Altar data in event_data.yml is corrupted. Removing it.");
            eventConfig.set(altarBlocksPath, null);
            plugin.getEventDataManager().saveConfig();
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("[차원의 균열] Cannot clean up altar: World '" + worldName + "' not found or not loaded!");
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                String[] parts = key.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                Location loc = new Location(world, x, y, z);
                BlockData data = Bukkit.createBlockData(section.getString(key));
                loc.getBlock().setBlockData(data, false);
            } catch (Exception e) {
                plugin.getLogger().severe("[차원의 균열] Error parsing block data for cleanup: " + key);
            }
        }

        eventConfig.set(altarBlocksPath, null);
        plugin.getEventDataManager().saveConfig();
        plugin.getLogger().info("[차원의 균열] Leftover altar cleanup complete.");
    }

    private Location getRandomSafeLocation(World world) {
        double borderSize = plugin.getGameConfigManager().getConfig().getDouble("world.border.overworld-size", 20000.0);
        double radius = (borderSize / 2.0) * 0.9;
        Random random = new Random();

        double angle = random.nextDouble() * 2 * Math.PI;
        double r = radius * Math.sqrt(random.nextDouble());
        int x = (int) (r * Math.cos(angle));
        int z = (int) (r * Math.sin(angle));

        return new Location(world, x + 0.5, 0, z + 0.5); // Y는 getHighestBlockAt으로 찾을 것이므로 0으로 둠
    }
}