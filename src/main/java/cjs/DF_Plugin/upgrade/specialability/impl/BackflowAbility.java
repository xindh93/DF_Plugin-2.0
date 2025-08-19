package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.BubbleColumn;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.List;

public class BackflowAbility implements ISpecialAbility {

    @Override
    public String getInternalName() {
        return "backflow";
    }

    @Override
    public String getDisplayName() {
        return "§3역류";
    }

    @Override
    public String getDescription() {
        return "§7[좌클릭]으로 지정한 위치에 강력한 역류를 일으켜 적들을 띄우고 지속 피해를 입힙니다.";
    }

    @Override
    public double getCooldown() {
        return DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.cooldown", 90.0);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event, Player player, ItemStack item) {
        // 좌클릭으로만 발동되도록 수정합니다.
        if (!event.getAction().isLeftClick()) {
            return;
        }

        // 능력 발동 위치를 플레이어가 바라보는 30미터 내의 블록으로 설정합니다.
        RayTraceResult rayTraceResult = player.rayTraceBlocks(30);
        if (rayTraceResult == null || rayTraceResult.getHitBlock() == null) {
            // 사정거리 내에 대상 블록이 없으면 발동하지 않습니다.
            return;
        }
        Location center = rayTraceResult.getHitBlock().getLocation().add(0.5, 1.2, 0.5); // 블록 바로 위를 중심으로 설정

        event.setCancelled(true); // 기본 삼지창 사용 방지

        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
        if (manager.isOnCooldown(player, this)) { // 아이템과 무관하게 플레이어의 능력 쿨다운을 확인합니다.
            return;
        }

        // tryUseAbility는 1회성 액티브 스킬에 적합. 여기서는 쿨다운만 설정.
        manager.setCooldown(player, this, getCooldown()); // 아이템이 아닌 플레이어에게 직접 쿨다운을 설정합니다.

        // 능력 실행
        performBackflow(player, center);
    }

    private void performBackflow(Player player, Location center) {
        double castDuration = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.cast-duration-seconds", 1.5);
        double effectDuration = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.effect-duration-seconds", 4.0);
        double radius = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.radius", 16.0);
        double damagePerTick = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.damage-per-tick", 5.0);
        double pullStrength = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.pull-strength", 15.0);
        double initialDamage = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.backflow.details.initial-damage", 30.0);

        // --- Ambient Rain Sound ---
        final double totalSoundDuration = castDuration + effectDuration + 2.0; // 2 extra seconds for fade out
        new BukkitRunnable() {
            double elapsedTicks = 0;
            @Override
            public void run() {
                if (elapsedTicks >= totalSoundDuration * 20) {
                    this.cancel();
                    return;
                }

                for (Player p : center.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(center) < 100 * 100) {
                        p.playSound(p.getLocation(), Sound.WEATHER_RAIN, 3.0f, 1.0f);
                    }
                }
                elapsedTicks += 4; // Check every 4 ticks
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, 4L);

        // --- 이벤트 순서 재정의 ---

        // 1. 삼지창 드롭 (즉시)
        Location tridentSpawnLoc = center.clone().add(0, 30, 0);
        final Trident fallingTrident = (Trident) center.getWorld().spawnEntity(tridentSpawnLoc, EntityType.TRIDENT);
        fallingTrident.setGravity(true);
        fallingTrident.setInvulnerable(true);
        fallingTrident.setPickupStatus(Trident.PickupStatus.DISALLOWED);
        fallingTrident.setVelocity(new Vector(0, -2.5, 0));

        // 2. 번개 (0.5초 후)
        new BukkitRunnable() {
            @Override
            public void run() {
                Location strikeLoc = fallingTrident.isValid() ? fallingTrident.getLocation() : center;
                strikeLoc.getWorld().strikeLightningEffect(strikeLoc);
            }
        }.runTaskLater(DF_Main.getInstance(), 10L);

        // 3. 땅에서 물이 새어 나옴 (0.6초 ~ 1.5초)
        new BukkitRunnable() {
            private int ticksRun = 0;
            private final int DURATION_TICKS = (int) (castDuration * 20) - 12; // 30 - 12 = 18 ticks

            @Override
            public void run() {
                if (ticksRun >= DURATION_TICKS) {
                    this.cancel();
                    return;
                }
                for (int i = 0; i < (int) (radius * 15); i++) {
                    double r = Math.random() * radius;
                    double angle = Math.random() * 2 * Math.PI;
                    double x = center.getX() + r * Math.cos(angle);
                    double z = center.getZ() + r * Math.sin(angle);
                    center.getWorld().spawnParticle(Particle.DRIPPING_WATER, x, center.getY(), z, 1, 0, 0, 0, 0);
                }
                ticksRun++;
            }
        }.runTaskTimer(DF_Main.getInstance(), 12L, 1L);

        // 4 & 5. 소리 감소 및 물기둥 생성 (1.5초 후)
        new BukkitRunnable() {
            @Override
            public void run() {
                final int MUTE_DURATION_TICKS = 40; // 2초
                final double MUTE_RADIUS = 100.0;
                for (Player p : center.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(center) < MUTE_RADIUS * MUTE_RADIUS) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, MUTE_DURATION_TICKS, 0, false, false, false));
                    }
                }
                if (fallingTrident.isValid()) fallingTrident.remove();
                startMainEffect(player, center, effectDuration, radius, damagePerTick, pullStrength, initialDamage);
            }
        }.runTaskLater(DF_Main.getInstance(), (long) (castDuration * 20));
    }

    private void startMainEffect(Player player, Location center, double duration, double radius, double damage, double pullStrength, double initialDamage) {
        // 스피커가 터지는 듯한 강력한 시작음
        center.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.0f);
        center.getWorld().playSound(center, Sound.BLOCK_WATER_AMBIENT, 2.0f, 0.5f);

        final double radiusSquared = radius * radius;
        final List<Location> waterBlocks = new ArrayList<>();

        // --- Main Effect Task (Creation, Animation, Game Logic) ---
        new BukkitRunnable() {
            int tickCounter = 0;
            int yOffset = 0;
            final int upwardHeight = 200;
            final int downwardHeight = 50;
            final int maxHeight = Math.max(upwardHeight, downwardHeight);
            final int layersPerTick = 5; // 물기둥이 더 빠르게 올라가도록 수정

            // Animation parameters
            final double screwRadius = 9.0;
            final int totalAnglePoints = 72;
            final int emptyLanes = 8;
            final double anglePerPoint = (2.0 * Math.PI) / totalAnglePoints;
            final int holeWidthInPoints = 5; // The width of the empty part of the wave

            @Override
            public void run() {
                // --- Stop condition ---
                if (tickCounter >= duration * 20) {
                    this.cancel();
                    // --- Destruction Animation ---
                    startDestructionAnimation(waterBlocks, center, upwardHeight, downwardHeight, screwRadius);
                    return;
                }

                // --- Creation logic (runs until pillar is built) ---
                if (yOffset < maxHeight) {
                    for (int i = 0; i < layersPerTick; i++) {
                        if (yOffset >= maxHeight) break;

                        // Create water layer at yOffset
                        for (double xOffset = -radius; xOffset <= radius; xOffset++) {
                            for (double zOffset = -radius; zOffset <= radius; zOffset++) {
                                if (xOffset * xOffset + zOffset * zOffset <= radiusSquared) {
                                    // Upward
                                    if (yOffset < upwardHeight) {
                                        Location blockLoc = center.clone().add(xOffset, yOffset, zOffset);
                                        Block block = blockLoc.getBlock();
                                        if (block.isPassable()) {
                                            block.setType(Material.BUBBLE_COLUMN, false);
                                            ((BubbleColumn) block.getBlockData()).setDrag(false);
                                            waterBlocks.add(blockLoc.clone());
                                        }
                                    }
                                    // Downward
                                    if (yOffset > 0 && yOffset <= downwardHeight) {
                                        Location blockLoc = center.clone().add(xOffset, -yOffset, zOffset);
                                        Block block = blockLoc.getBlock();
                                        if (block.isPassable()) {
                                            block.setType(Material.BUBBLE_COLUMN, false);
                                            ((BubbleColumn) block.getBlockData()).setDrag(false);
                                            waterBlocks.add(blockLoc.clone());
                                        }
                                    }
                                }
                            }
                        }
                        yOffset++;
                    }
                }

                // --- Animation logic (runs every tick) ---
                // Animate all layers that have been created so far.
                for (int y = -downwardHeight; y < yOffset; y++) {
                    if (y >= upwardHeight) break;
                    if (y < 0 && yOffset < -y) continue;

                    redrawSnowLayer(y);
                }

                // --- Game Logic (every 4 ticks) ---
                if (tickCounter % 4 == 0) {
                    // 첫 틱에만 초기 폭발 데미지를 줍니다.
                    if (tickCounter == 0) {
                        for (Entity entity : center.getWorld().getNearbyEntities(center.clone().add(0, 100, 0), radius, 100, radius)) {
                            if (entity instanceof LivingEntity && !entity.getUniqueId().equals(player.getUniqueId())) {
                                ((LivingEntity) entity).damage(initialDamage, player);
                                entity.getWorld().spawnParticle(Particle.FLASH, entity.getLocation().add(0, 1, 0), 1);
                            }
                        }
                    }

                    // 반경 100블럭 내의 모든 플레이어에게 소리를 들려줍니다.
                    for (Player p : center.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(center) < 100 * 100) {
                            // 빗소리는 별도 타이머가 관리합니다.
                            // 스피커가 터지는 듯한 지속음을 추가합니다.
                            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_AMBIENT, 2.0f, 1.0f);
                        }
                    }

                    // 주변 엔티티에 효과 적용 (범위를 물기둥 높이에 맞춤)
                    for (Entity entity : center.getWorld().getNearbyEntities(center.clone().add(0, 100, 0), radius, 100, radius)) {
                        if (entity instanceof LivingEntity && !entity.getUniqueId().equals(player.getUniqueId())) {
                            final LivingEntity target = (LivingEntity) entity;

                            // 데미지 적용
                            target.damage(damage, player);

                            // 1틱 늦게 속도를 설정하여 버블 컬럼의 자체 상승 효과에 덮어씌워지는 것을 방지하고 우선순위를 확보합니다.
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (target.isValid() && !target.isDead()) {
                                        Vector velocity = target.getVelocity();
                                        velocity.setY(pullStrength);
                                        target.setVelocity(velocity);
                                    }
                                }
                            }.runTaskLater(DF_Main.getInstance(), 1L);
                        }
                    }
                }
                tickCounter++;
            }

            /**
             * Redraws a single horizontal layer of the snow screw based on its y-level and the current time.
             * @param y The y-offset from the center.
             */
            private void redrawSnowLayer(int y) {
                // The rotation for this layer depends on its layer group and the current time
                int layerGroup = y / layersPerTick;
                int rotationStep = layerGroup + tickCounter;

                for (int point = 0; point < totalAnglePoints; point++) {
                    boolean isEmptySpace = false;
                    for (int j = 0; j < emptyLanes; j++) {
                        int emptyLaneStartPoint = (j * (totalAnglePoints / emptyLanes) + rotationStep) % totalAnglePoints;
                        for (int k = 0; k < holeWidthInPoints; k++) {
                            if (point == (emptyLaneStartPoint + k) % totalAnglePoints) {
                                isEmptySpace = true;
                                break;
                            }
                        }
                        if (isEmptySpace) break;
                    }

                    double angle = point * anglePerPoint;
                    double x = center.getX() + screwRadius * Math.cos(angle);
                    double z = center.getZ() + screwRadius * Math.sin(angle);
                    Location loc = new Location(center.getWorld(), x, center.getY() + y, z);

                    loc.getBlock().setType(isEmptySpace ? Material.AIR : Material.POWDER_SNOW, false);
                }
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, 1L);
    }

    private void startDestructionAnimation(List<Location> waterBlocks, Location center, int upwardHeight, int downwardHeight, double screwRadius) {
        final List<Location> allBlocks = new ArrayList<>(waterBlocks);
        waterBlocks.clear();

        new BukkitRunnable() {
            int yOffset = 0;
            final int maxHeight = Math.max(upwardHeight, downwardHeight);
            final int layersPerTick = 5;

            @Override
            public void run() {
                for (int i = 0; i < layersPerTick; i++) {
                    if (yOffset >= maxHeight) {
                        // 남아있는 물 블록이 있다면 안전하게 모두 제거합니다.
                        for (Location loc : allBlocks) {
                            Material type = loc.getBlock().getType();
                            if (type == Material.BUBBLE_COLUMN) {
                                loc.getBlock().setType(Material.AIR, false);
                            }
                        }
                        allBlocks.clear();
                        this.cancel();
                        return;
                    }

                    // 가루눈 기둥을 아래에서부터 제거합니다.
                    final double innerRadiusSq = (screwRadius - 1.0) * (screwRadius - 1.0);
                    final double outerRadiusSq = screwRadius * screwRadius;
                    for (double xOffset = -screwRadius; xOffset <= screwRadius; xOffset++) {
                        for (double zOffset = -screwRadius; zOffset <= screwRadius; zOffset++) {
                            double distSq = xOffset * xOffset + zOffset * zOffset;
                            if (distSq > innerRadiusSq && distSq <= outerRadiusSq) {
                                Location upLoc = center.clone().add(xOffset, yOffset, zOffset);
                                if (upLoc.getBlock().getType() == Material.POWDER_SNOW) upLoc.getBlock().setType(Material.AIR, false);
                                Location downLoc = center.clone().add(xOffset, -yOffset, zOffset);
                                if (downLoc.getBlock().getType() == Material.POWDER_SNOW) downLoc.getBlock().setType(Material.AIR, false);
                            }
                        }
                    }

                    // 한 층의 물기둥을 위아래로 제거합니다.
                    double currentYUp = center.getY() + yOffset;
                    double currentYDown = center.getY() - yOffset;
                    allBlocks.removeIf(loc -> {
                        boolean isUpLayer = yOffset < upwardHeight && Math.abs(loc.getY() - currentYUp) < 0.1;
                        boolean isDownLayer = yOffset > 0 && yOffset <= downwardHeight && Math.abs(loc.getY() - currentYDown) < 0.1;

                        if (isUpLayer || isDownLayer) {
                            Material type = loc.getBlock().getType();
                            if (type == Material.BUBBLE_COLUMN) {
                                loc.getBlock().setType(Material.AIR, false);
                            }
                            return true;
                        }
                        return false;
                    });
                    yOffset++;
                }
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, 1L); // 1틱마다 한 층씩 제거
    }
}