package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LightningSpearAbility implements ISpecialAbility {

    @Override
    public String getInternalName() {
        return "lightning_spear";
    }

    @Override
    public String getDisplayName() {
        return "§b뇌창";
    }

    @Override
    public String getDescription() {
        return "§7[좌클릭]으로 공전하는 뇌창을 발사합니다. 5회 충전. 적중 시 스택이 쌓이며, 5스택 시 번개가 내리칩니다.";
    }

    @Override
    public double getCooldown() {
        // 이 능력은 자체 충전 시스템을 사용하므로, SpecialAbilityManager의 쿨다운은 사용하지 않습니다.
        return 0;
    }

    @Override
    public int getMaxCharges() {
        return DF_Main.getInstance().getGameConfigManager().getConfig().getInt("upgrade.special-abilities.lightning_spear.max-charges", 5);
    }

    @Override
    public boolean showInActionBar() {
        // 충전량을 액션바에 표시합니다.
        return true;
    }

    // --- State Records ---
    private record StackKey(UUID targetId, UUID attackerId) {}
    private record StackInfo(int count, BukkitTask expiryTask) {}

    // --- State Maps ---
    private final Map<UUID, List<Trident>> floatingTridents = new ConcurrentHashMap<>();
    private final Map<StackKey, StackInfo> embeddedStacks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> animationTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Double> animationAngles = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Trident>> activeProjectiles = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> nextFireIndex = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> projectileReturnTasks = new ConcurrentHashMap<>();

    // --- Constants ---
    private static final String PROJECTILE_META_KEY = "df_lightning_spear_projectile";
    private static final String FLOATING_TRIDENT_META_KEY = "df_floating_trident";

    @Override
    public void onEquip(Player player, ItemStack item) {
        // 액션바에 충전량을 표시하도록 가시성을 활성화합니다.
        getManager().setChargeVisibility(player, this, true);
        initialize(player);
    }

    @Override
    public void onCleanup(Player player) {
        UUID uuid = player.getUniqueId();
        clearFloatingTridents(player);

        // 아이템을 바꿀 때, 해당 플레이어의 모든 활성 발사체와 관련 작업을 정리합니다.
        Set<Trident> projectiles = activeProjectiles.remove(uuid);
        if (projectiles != null) {
            for (Trident trident : projectiles) {
                if (trident != null && trident.isValid()) {
                    // 이 발사체와 연결된 귀환/타임아웃 작업을 취소하고 맵에서 제거합니다.
                    BukkitTask returnTask = projectileReturnTasks.remove(trident.getUniqueId());
                    if (returnTask != null) {
                        returnTask.cancel();
                    }
                    trident.remove();
                }
            }
        }

        BukkitTask animationTask = animationTasks.remove(uuid);
        if (animationTask != null) animationTask.cancel();

        // 사용했던 충돌 방지 팀을 정리합니다.
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "df_ls_" + player.getUniqueId().toString().substring(0, 10);
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }

        nextFireIndex.remove(uuid);
        animationAngles.remove(uuid);

        // 이 플레이어가 다른 엔티티에 적용한 모든 스택을 정리합니다.
        embeddedStacks.entrySet().removeIf(entry -> {
            if (entry.getKey().attackerId().equals(uuid)) {
                entry.getValue().expiryTask().cancel();
                return true;
            }
            return false;
        });

        // 액션바에서 충전량 표시를 숨깁니다.
        getManager().setChargeVisibility(player, this, false);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event, Player player, ItemStack item) {
        // 좌클릭은 능력 발동으로 사용하므로, 기본 이벤트를 취소합니다.
        if (event.getAction().isLeftClick()) {
            event.setCancelled(true);
            fireTrident(player);
        }
        // 우클릭은 onProjectileLaunch에서 바닐라 투척을 막도록 처리합니다.
        // 여기서 이벤트를 취소하지 않아야 onProjectileLaunch가 호출됩니다.
    }

    @Override
    public void onProjectileLaunch(ProjectileLaunchEvent event, Player player, ItemStack item) {
        // '뇌창' 모드에서는 플레이어가 직접 삼지창을 던질 수 있습니다.
        // onEquip 및 onCleanup 로직이 아이템을 다시 손에 들었을 때 공전 창을 재생성합니다.
        // 이벤트를 취소하지 않아 바닐라 투척이 가능하도록 합니다.
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event, Player player, ItemStack item) {
        if (!(event.getDamager() instanceof Trident trident) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        // 이 능력으로 생성된 발사체인지, 그리고 자기 자신을 공격하는지 확인합니다.
        if (!trident.hasMetadata(PROJECTILE_META_KEY)) {
            return;
        }

        Object metadataValue = trident.getMetadata(PROJECTILE_META_KEY).get(0).value();
        if (!(metadataValue instanceof UUID)) {
            return;
        }

        UUID ownerId = (UUID) metadataValue;
        if (target.getUniqueId().equals(ownerId)) {
            event.setCancelled(true);
            return;
        }

        // 1. 데미지 처리
        // 기본 삼지창 데미지는 그대로 적용하고, 추가 물리 피해를 줍니다.
        double extraDamage = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.extra-physical-damage", 10.0);
        event.setDamage(event.getDamage() + extraDamage);

        // 2. 스택 처리
        StackKey stackKey = new StackKey(target.getUniqueId(), player.getUniqueId());
        StackInfo oldInfo = embeddedStacks.get(stackKey);
        if (oldInfo != null) {
            oldInfo.expiryTask().cancel(); // 이전 만료 타이머 취소
        }
        int newStackCount = 1;

        if (oldInfo != null) {
            newStackCount = oldInfo.count() + 1;
        }

        long stackDurationTicks = (long) (DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.stack-duration-seconds", 30.0) * 20);
        final BukkitTask expiryTask = new BukkitRunnable() {
            @Override
            public void run() {
                StackInfo latestInfo = embeddedStacks.get(stackKey);
                if (latestInfo != null && latestInfo.expiryTask().getTaskId() == this.getTaskId()) {
                    embeddedStacks.remove(stackKey);
                    target.getWorld().spawnParticle(Particle.LARGE_SMOKE, target.getEyeLocation(), 5, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }.runTaskLater(DF_Main.getInstance(), stackDurationTicks);

        embeddedStacks.put(stackKey, new StackInfo(newStackCount, expiryTask));

        // 3. 스택 시각화 (노란색 파티클)
        float particleSize = 0.6f + (newStackCount * 0.2f);
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.YELLOW, particleSize);
        target.getWorld().spawnParticle(Particle.DUST, target.getEyeLocation().add(0, 0.5, 0), 1, dustOptions);

        if (newStackCount >= getMaxCharges()) {
            // 5스택 달성: 최종 타격 및 초기화
            StackInfo info = embeddedStacks.remove(stackKey);
            if (info != null) {
                info.expiryTask().cancel();
            }

            double finalDamage = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.lightning-damage", 40.0);
            target.getWorld().strikeLightningEffect(target.getLocation());
            target.damage(finalDamage, player); // 물리 피해를 입힙니다.

            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.4f);
        } else {
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ARROW_HIT, 0.8f, 1.2f);
        }
    }

    private void initialize(Player player) {
        UUID uuid = player.getUniqueId();

        // 모든 플레이어 및 삼지창과의 충돌을 막기 위해 글로벌 팀에 플레이어를 추가합니다.
        Team team = getOrCreatePlayerCollisionTeam(player);
        if (!team.hasEntry(player.getUniqueId().toString())) {
            team.addEntry(player.getUniqueId().toString());
        }

        // 이미 부유 삼지창이 있다면, 중복 실행을 방지합니다.
        if (floatingTridents.containsKey(uuid) && floatingTridents.get(uuid).stream().anyMatch(Objects::nonNull)) {
            return;
        }

        setCharges(player, getMaxCharges());

        // 발사 순서 초기화
        nextFireIndex.put(uuid, 0);

        spawnFloatingTridents(player);
        startAnimationTask(player);
    }

    private void fireTrident(Player player) {
        int charges = getCharges(player);
        if (charges <= 0) {
            return;
        }

        UUID uuid = player.getUniqueId();
        List<Trident> tridents = floatingTridents.get(uuid);

        // 1. 발사할 삼지창을 순차적으로 찾습니다.
        int startIndex = nextFireIndex.getOrDefault(uuid, 0);
        Trident tridentToFire = null;
        int fireIndex = -1;

        if (tridents != null) {
            for (int i = 0; i < tridents.size(); i++) {
                int currentIndex = (startIndex + i) % tridents.size();
                Trident currentTrident = tridents.get(currentIndex);
                if (currentTrident != null && currentTrident.isValid()) {
                    tridentToFire = currentTrident;
                    fireIndex = currentIndex;
                    break;
                }
            }
        }

        if (tridentToFire == null) {
            // 발사할 수 있는 유효한 삼지창이 없습니다.
            return;
        }

        // 2. 상태를 업데이트하고 관상용 삼지창을 '활성화'합니다.
        setCharges(player, charges - 1);
        nextFireIndex.put(uuid, (fireIndex + 1) % getMaxCharges());
        tridents.set(fireIndex, null); // 공전 목록에서 제거

        // 관상용 삼지창을 실제 투사체로 전환
        activateTrident(tridentToFire, player);

        // 3. 발사 효과 및 귀환 처리
        player.getWorld().playSound(tridentToFire.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 1.5f);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, tridentToFire.getLocation(), 8, 0.1, 0.1, 0.1, 0.05);

        trackAndHandleReturn(tridentToFire, player);
    }

    /**
     * 관상용 삼지창을 실제 투사체로 '활성화'합니다.
     * @param trident 활성화할 관상용 삼지창
     * @param player 발사자
     */
    private void activateTrident(Trident trident, Player player) {

        // 2. 메타데이터 및 물리 속성 변경
        trident.removeMetadata(FLOATING_TRIDENT_META_KEY, DF_Main.getInstance());
        trident.setMetadata(PROJECTILE_META_KEY, new FixedMetadataValue(DF_Main.getInstance(), player.getUniqueId()));
        trident.setGravity(true);
        trident.setShooter(player);
        trident.setPierceLevel((byte) 0); // 관통 효과 제거
        trident.setLoyaltyLevel(3); // 충성 인챈트 효과 부여
        trident.setPickupStatus(Trident.PickupStatus.DISALLOWED); // [핵심 수정] 돌아온 삼지창을 획득하지 못하도록 설정

        // 3. 발사
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();
        // 발사 위치를 플레이어의 눈 앞에서 1.5블록 떨어진 곳으로 조정하여,
        // 발사 직후 플레이어 자신 또는 다른 공전 삼지창과 충돌하는 현상을 방지합니다.
        Location spawnLoc = eyeLoc.clone().add(direction.clone().multiply(1.5));
        trident.teleport(spawnLoc);
        trident.setVelocity(direction.multiply(3.0)); // 순간이동 후 속도 적용

        // 4. 추적 목록에 추가
        activeProjectiles.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(trident);
    }


    private void trackAndHandleReturn(Trident trident, Player player) {
        BukkitTask returnTask = new BukkitRunnable() {
            int ticksLived = 0;

            private void cleanupAndCancel() {
                projectileReturnTasks.remove(trident.getUniqueId());
                Set<Trident> projectiles = activeProjectiles.get(player.getUniqueId());
                if (projectiles != null) {
                    projectiles.remove(trident);
                }
                this.cancel();
            }

            @Override
            public void run() {
                // 플레이어가 오프라인이거나 사망한 경우, 추적을 중단하고 삼지창을 제거합니다.
                // 이 경우, onCleanup에서 모든 발사체가 정리되므로 충전량은 돌려주지 않습니다.
                if (!player.isOnline() || player.isDead()) {
                    if (trident.isValid()) trident.remove();
                    cleanupAndCancel();
                    return;
                }

                // 삼지창이 어떤 이유로든 사라졌다면 (예: 선인장, 용암), 작업만 종료합니다.
                if (!trident.isValid()) {
                    // 이 경우, 충전량은 자연적으로 회복되지 않습니다. 발사체가 소멸된 것입니다.
                    cleanupAndCancel();
                    return;
                }

                // 충성 인챈트로 돌아온 삼지창이 플레이어에게 완전히 가까워졌을 때 공전 궤도에 합류시킵니다.
                // 발사 직후 바로 회수되는 것을 방지하기 위해 약간의 유예 시간(10틱)을 줍니다.
                if (ticksLived++ > 10 && trident.getLocation().distanceSquared(player.getEyeLocation()) < 2.0 * 2.0) {
                    assimilateReturningTrident(trident, player);
                    cleanupAndCancel();
                }
            }
        }.runTaskTimer(DF_Main.getInstance(), 1L, 1L);

        // 나중에 onProjectileHit에서 취소할 수 있도록 작업을 맵에 저장합니다.
        projectileReturnTasks.put(trident.getUniqueId(), returnTask);
    }

    private void assimilateReturningTrident(Trident returningTrident, Player player) {
        int current = getCharges(player);
        int max = getMaxCharges();

        if (current >= max) {
            if (returningTrident.isValid()) returningTrident.remove();
            return;
        }

        // 돌아온 삼지창을 즉시 제거합니다.
        if (returningTrident.isValid()) {
            returningTrident.remove();
        }

        // 충전량을 1 늘립니다.
        setCharges(player, current + 1);

        // 비어있는 슬롯을 찾아 새로운 공전 삼지창을 생성합니다.
        List<Trident> tridents = floatingTridents.get(player.getUniqueId());
        if (tridents != null) {
            int emptySlot = tridents.indexOf(null);
            if (emptySlot != -1) {
                // spawnSingleFloatingTrident가 새 삼지창을 생성하고 팀에 추가합니다.
                tridents.set(emptySlot, spawnSingleFloatingTrident(player, emptySlot));
            }
        } else {
            // 리스트가 없는 비정상적인 경우, 전체 초기화를 다시 실행합니다.
            initialize(player);
        }
    }

    private void rechargeOneCharge(Player player) {
        if (!player.isOnline()) return;
        int current = getCharges(player);
        int max = getMaxCharges();
        if (current >= max) return;

        setCharges(player, current + 1);

        List<Trident> tridents = floatingTridents.get(player.getUniqueId());
        if (tridents != null) {
            int emptySlot = tridents.indexOf(null);
            if (emptySlot != -1) {
                tridents.set(emptySlot, spawnSingleFloatingTrident(player, emptySlot));
            }
        } else {
            initialize(player);
        }
    }

    private void checkAndRegenerateTridents(Player player) {
        int charges = getCharges(player);
        List<Trident> tridents = floatingTridents.get(player.getUniqueId());

        // 1. 유효하지 않은 삼지창을 목록에서 제거하여 빈 슬롯으로 만듭니다.
        if (tridents != null) {
            for (int i = 0; i < tridents.size(); i++) {
                Trident t = tridents.get(i);
                if (t != null && !t.isValid()) {
                    tridents.set(i, null);
                }
            }
        } else {
            return;
        }

        // 유효한 삼지창의 개수를 셉니다.
        long actualTridentCount = tridents.stream().filter(Objects::nonNull).count();

        // 만약 충전량보다 실제 삼지창 개수가 적으면, 하나를 소환하여 합류시킵니다.
        if (charges > actualTridentCount) {
            // 한 번에 하나씩만 재생성하여 자연스러운 합류 효과를 줍니다.
            spawnAndAssimilateOneTrident(player);
        }
    }

    private void spawnAndAssimilateOneTrident(Player player) {
        List<Trident> tridents = floatingTridents.get(player.getUniqueId());
        if (tridents == null) {
            return;
        }

        int emptySlot = tridents.indexOf(null);
        if (emptySlot == -1) {
            return;
        }

        // 비어있는 슬롯에, 일반 궤도보다 더 바깥쪽에 삼지창을 생성합니다.
        // 주 애니메이션 루프가 이 삼지창을 올바른 궤도로 자연스럽게 끌어당겨, 합류하는 듯한 효과를 냅니다.
        Trident newTrident = spawnSingleFloatingTrident(player, emptySlot, true);
        if (newTrident != null) {
            tridents.set(emptySlot, newTrident);
        }
    }

    private void startAnimationTask(Player player) {
        UUID uuid = player.getUniqueId();
        animationTasks.computeIfAbsent(uuid, k -> new BukkitRunnable() {
            private int tickCounter = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    onCleanup(player); // The task will be cancelled inside onCleanup
                    return;
                }

                // 매초마다 충전량과 실제 삼지창 개수를 비교하여, 누락된 삼지창을 자동으로 복구합니다.
                if (tickCounter++ % 20 == 0) {
                    checkAndRegenerateTridents(player);
                }

                List<Trident> tridents = floatingTridents.get(uuid);
                if (tridents == null) return;
                updateIdleAnimation(player, tridents);
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, 1L));
    }

    private void updateIdleAnimation(Player player, List<Trident> tridents) {
        UUID uuid = player.getUniqueId();

        // --- Collision Safety Check ---
        Team team = getOrCreatePlayerCollisionTeam(player);
        if (!team.hasEntry(player.getUniqueId().toString())) {
            team.addEntry(player.getUniqueId().toString());
        }

        double currentAngleDegrees = animationAngles.getOrDefault(uuid, 0.0);
        animationAngles.put(uuid, (currentAngleDegrees + 3.0) % 360); // 3 degrees per tick

        Location playerLoc = player.getLocation();
        float playerYaw = player.getLocation().getYaw();
        double radius = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.orbit-radius", 1.9);
        double sprintRadius = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.sprint-orbit-radius", 1.5);
        int maxCharges = getMaxCharges();
        boolean isSprinting = player.isSprinting();

        for (int i = 0; i < tridents.size(); i++) {
            Trident trident = tridents.get(i);
            if (trident == null || !trident.isValid()) continue;

            if (!trident.getWorld().equals(player.getWorld())) {
                trident.remove();
                tridents.set(i, null);
                rechargeOneCharge(player);
                continue;
            }

            if (!team.hasEntry(trident.getUniqueId().toString())) {
                team.addEntry(trident.getUniqueId().toString());
            }

            double targetAngle = Math.toRadians(currentAngleDegrees + (360.0 / maxCharges) * i);
            double currentRadius = isSprinting ? sprintRadius : radius;
            double yOffset = isSprinting ? 2.5 : 1.2;

            // [복원] 달릴 때는 기본 궤도를 유지한 채 위로 올라가도록 수정합니다.
            Vector pos = new Vector(Math.cos(targetAngle) * currentRadius, 0, Math.sin(targetAngle) * currentRadius);
            pos.setY(Math.sin(targetAngle * 2) * 0.25);
            pos.rotateAroundY(Math.toRadians(-playerYaw));

            Location targetLoc = playerLoc.clone().add(pos).add(0, yOffset, 0);

            // --- 하이브리드 이동 로직 (블록 관통 효과) ---
            Vector velocity = targetLoc.toVector().subtract(trident.getLocation().toVector());
            Location nextTickLocation = trident.getLocation().add(velocity.clone().multiply(0.4));

            // 다음 위치가 블록 내부인지 확인
            if (nextTickLocation.getBlock().getType().isSolid()) {
                // 충돌이 예상되면 순간이동으로 위치를 보정하여 관통 효과를 냅니다.
                trident.teleport(targetLoc);
                trident.setVelocity(new Vector(0, 0, 0)); // 순간이동 후 속도 초기화
            } else {
                // 충돌이 없으면 setVelocity로 부드럽게 이동
                double correctionStrength = 0.4;
                Vector finalVelocity = velocity.multiply(correctionStrength);

                if (finalVelocity.lengthSquared() > 4.0) {
                    finalVelocity.normalize().multiply(2.0);
                }
                trident.setVelocity(finalVelocity);
            }
        }
    }

    private void spawnFloatingTridents(Player player) {
        clearFloatingTridents(player);
        int maxCharges = getMaxCharges();

        List<Trident> tridents = new ArrayList<>(maxCharges);
        for (int i = 0; i < maxCharges; i++) {
            tridents.add(spawnSingleFloatingTrident(player, i));
        }
        floatingTridents.put(player.getUniqueId(), tridents);
    }

    private Trident spawnSingleFloatingTrident(Player player, int orbitIndex) {
        return spawnSingleFloatingTrident(player, orbitIndex, false);
    }

    private Trident spawnSingleFloatingTrident(Player player, int orbitIndex, boolean fromAfar) {
        UUID uuid = player.getUniqueId();
        double currentAngleDegrees = animationAngles.getOrDefault(uuid, 0.0);
        int maxCharges = getMaxCharges();
        double radius = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.orbit-radius", 1.9);
        double sprintRadius = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.lightning_spear.details.sprint-orbit-radius", 1.5);
        double targetAngle = Math.toRadians(currentAngleDegrees + (360.0 / maxCharges) * orbitIndex);

        Location playerLoc = player.getLocation();
        float playerYaw = player.getLocation().getYaw();
        boolean isSprinting = player.isSprinting();

        // [복원] 달릴 때는 기본 궤도를 유지한 채 위로 올라가도록 수정합니다.
        double baseRadius = isSprinting ? sprintRadius : radius;
        double spawnRadius = fromAfar ? baseRadius + 4.0 : baseRadius;
        double yOffset = isSprinting ? 2.5 : 1.2;

        Vector pos = new Vector(Math.cos(targetAngle) * spawnRadius, 0, Math.sin(targetAngle) * spawnRadius);
        pos.setY(Math.sin(targetAngle * 2) * 0.25);
        pos.rotateAroundY(Math.toRadians(-playerYaw));

        Location spawnLoc = playerLoc.clone().add(pos).add(0, yOffset, 0);

        Trident trident = (Trident) player.getWorld().spawnEntity(spawnLoc, EntityType.TRIDENT);
        trident.setGravity(false);
        trident.setInvulnerable(false);
        trident.setPickupStatus(Trident.PickupStatus.DISALLOWED);
        trident.setMetadata(FLOATING_TRIDENT_META_KEY, new FixedMetadataValue(DF_Main.getInstance(), player.getUniqueId()));
        trident.setPierceLevel((byte) 127);

        Team team = getOrCreatePlayerCollisionTeam(player);
        if (!team.hasEntry(trident.getUniqueId().toString())) {
            team.addEntry(trident.getUniqueId().toString());
        }
        return trident;
    }

    private void clearFloatingTridents(Player player) {
        List<Trident> tridents = floatingTridents.remove(player.getUniqueId());
        if (tridents != null) {
            tridents.stream().filter(Objects::nonNull).filter(Trident::isValid).forEach(Trident::remove);
        }
    }

    private Team getOrCreatePlayerCollisionTeam(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        // 팀 이름은 16자 제한이 있으므로 UUID의 일부를 사용합니다.
        String teamName = "df_ls_" + player.getUniqueId().toString().substring(0, 10);
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            team.setAllowFriendlyFire(false); // 팀원 간의 모든 피해 방지
        }
        return team;
    }

    private SpecialAbilityManager getManager() {
        return DF_Main.getInstance().getSpecialAbilityManager();
    }

    private int getCharges(Player player) {
        SpecialAbilityManager.ChargeInfo info = getManager().getChargeInfo(player, this);
        return (info != null) ? info.current() : getMaxCharges();
    }

    private void setCharges(Player player, int amount) {
        getManager().setChargeInfo(player, this, amount, getMaxCharges());
    }

    /**
     * 서버 종료 시 호출되어 월드에 남아있는 모든 '뇌창' 관련 삼지창을 제거합니다.
     * DF_Main의 onDisable()에서 호출해야 합니다.
     */
    public static void cleanupAllLingeringTridents() {
        for (World world : Bukkit.getServer().getWorlds()) {
            for (Trident trident : world.getEntitiesByClass(Trident.class)) {
                // 공전 중인 삼지창 또는 발사된 삼지창인지 메타데이터로 확인
                if (trident.hasMetadata(FLOATING_TRIDENT_META_KEY) || trident.hasMetadata(PROJECTILE_META_KEY)) {
                    trident.remove();
                }
            }
        }
    }
}