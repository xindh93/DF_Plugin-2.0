package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SuperJumpAbility implements ISpecialAbility {

    private final Map<UUID, BukkitTask> chargeTasks = new HashMap<>();
    private final Map<UUID, Boolean> isSuperJumpCharged = new HashMap<>();
    private final Map<UUID, Boolean> isSuperJumpState = new HashMap<>();

    @Override
    public String getInternalName() {
        return "super_jump";
    }

    @Override
    public String getDisplayName() { return "§6슈퍼 점프"; }

    @Override
    public String getDescription() { return "§7웅크려 충전 후 높이 도약합니다. "; }

    @Override
    public double getCooldown() {
        return DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.super_jump.cooldown", 10.0);
    }

    @Override
    public boolean showInActionBar() {
        return true;
    }



    @Override
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event, Player player, ItemStack item) {
        UUID playerUUID = player.getUniqueId();
        if (DF_Main.getInstance().getSpecialAbilityManager().isOnCooldown(player, this, item)) {
            return;
        }

        if (event.isSneaking()) {
            // 지면에서 웅크리기: 슈퍼 점프 충전 시작
            if (player.isOnGround()) {
                // 이전 작업이 있다면 취소
                cleanupChargeState(playerUUID);

                isSuperJumpCharged.put(playerUUID, false);
                isSuperJumpState.put(playerUUID, false);

                final double chargeSeconds = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.super_jump.details.charge-time-seconds", 2.0);
                final long chargeTicks = (long) (chargeSeconds * 20L);

                // 충전 상태를 지속적으로 확인하는 반복 작업
                BukkitTask chargeTask = new BukkitRunnable() {
                    private long startTick = Bukkit.getServer().getCurrentTick();

                    @Override
                    public void run() {
                        // 플레이어가 충전 조건을 벗어났는지 확인 (점프, 이동, 웅크리기 해제 등)
                        if (!player.isOnline() || !player.isSneaking() || !player.isOnGround()) {
                            cleanupChargeState(playerUUID); // 상태 초기화
                            this.cancel();
                            return;
                        }

                        long elapsedTicks = Bukkit.getServer().getCurrentTick() - startTick;

                        if (elapsedTicks >= chargeTicks) {
                            // 충전 완료
                            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
                            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 30, 0.5, 0.1, 0.5, 0.05);
                            isSuperJumpCharged.put(playerUUID, true);
                            this.cancel(); // 충전이 완료되었으므로 타이머 종료
                        }
                    }
                }.runTaskTimer(DF_Main.getInstance(), 0L, 1L); // 매 틱마다 확인하여 정확도 높임
                chargeTasks.put(playerUUID, chargeTask);

            }
        } else {
            // 웅크리기 해제 시
            // 슈퍼 점프가 완전히 충전되었다면 점프를 실행합니다.
            // 여기서 is_on_ground 체크를 제거하여, 점프와 동시에 웅크리기를 해제해도 발동되도록 합니다.
            if (isSuperJumpCharged.getOrDefault(playerUUID, false)) {
                performSuperJump(player, item);
            }
            cleanupChargeState(playerUUID);
        }
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event, Player player, ItemStack item) {
        UUID playerUUID = player.getUniqueId();

        // 지면에 닿았을 때 슈퍼 점프 관련 상태 초기화
        if (player.isOnGround() && isSuperJumpState.getOrDefault(playerUUID, false)) {
            isSuperJumpState.put(playerUUID, false);
            isSuperJumpCharged.put(playerUUID, false); // 충전 상태도 초기화

        }
    }

    @Override
    public void onCleanup(Player player) {
        UUID playerUUID = player.getUniqueId();
        cleanupChargeState(playerUUID);
        isSuperJumpState.remove(playerUUID);

    }

    private void cleanupChargeState(UUID playerUUID) {
        isSuperJumpCharged.remove(playerUUID);
        if (chargeTasks.containsKey(playerUUID)) {
            chargeTasks.get(playerUUID).cancel();
            chargeTasks.remove(playerUUID);
        }
    }

    private void cancelCharging(Player player) {
        cleanupChargeState(player.getUniqueId());
        player.sendMessage("§c점프하여 슈퍼 점프 충전이 취소되었습니다.");
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.5f);
    }

    private void performSuperJump(Player player, ItemStack item) {
        double jumpVelocity = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.super_jump.details.jump-velocity", 2.0);

        // 효과음과 파티클은 즉시 재생합니다.
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 1);

        // 1틱 늦게 속도를 적용하여 서버 물리엔진과의 충돌을 방지합니다.
        // 점프 상태(isSuperJumpState)도 이때 함께 설정하여, onPlayerMove 이벤트가 상태를 즉시 초기화하는 것을 방지합니다.
        Bukkit.getScheduler().runTask(DF_Main.getInstance(), () -> {
            player.setVelocity(player.getVelocity().add(new Vector(0, jumpVelocity, 0)));
            isSuperJumpState.put(player.getUniqueId(), true);
        });

        DF_Main.getInstance().getSpecialAbilityManager().setCooldown(player, this, item, getCooldown());
    }


    @Override
    public void onEntityDamage(EntityDamageEvent event, Player player, ItemStack item) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            UUID playerUUID = player.getUniqueId();
            // 슈퍼 점프 상태(공중에 있는 상태)에서만 낙하 데미지를 무시합니다.
            if (isSuperJumpState.getOrDefault(playerUUID, false)) {
                event.setCancelled(true);
                // 지면에 닿으면 onPlayerMove에서 isSuperJumpState가 false로 바뀌므로,
                // 다음 일반적인 낙하에서는 데미지를 정상적으로 받게 됩니다.
            }
        }
    }
    @Override
    public void onPlayerJump(PlayerJumpEvent event, Player player, ItemStack item) {
        // 충전 중일 때 점프하면 충전 취소
        if (chargeTasks.containsKey(player.getUniqueId())) {
            cancelCharging(player);
        }
        // 충전이 완료된 상태에서 점프하면 준비 상태 취소
        else if (isSuperJumpCharged.getOrDefault(player.getUniqueId(), false)) {
            isSuperJumpCharged.remove(player.getUniqueId());
            player.sendMessage("§c점프하여 슈퍼 점프 준비가 취소되었습니다.");
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
        }
    }
}