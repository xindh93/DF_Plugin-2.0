package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AbsorptionShieldAbility implements ISpecialAbility {

    private static final double MAX_ABSORPTION_AMOUNT = 20.0; // 1줄 (10하트)

    private final Map<UUID, Long> lastDamagedTime = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> shieldTasks = new ConcurrentHashMap<>();

    @Override
    public String getInternalName() {
        return "absorption_shield";
    }

    @Override
    public String getDisplayName() {
        return "§e보호막";
    }

    @Override
    public String getDescription() {
        return "§730초간 피격되지 않으면 10초마다 보호막(흡수)을 얻습니다.";
    }

    @Override
    public double getCooldown() {
        return 0; // 패시브 능력
    }

    @Override
    public boolean showInActionBar() {
        return false; // 액션바에 표시하지 않음
    }

    @Override
    public void onEquip(Player player, ItemStack item) {
        if (shieldTasks.containsKey(player.getUniqueId())) {
            return;
        }

        // 장착 시점을 마지막 피격 시간으로 기록하여,
        // 반드시 30초의 대기시간을 거치도록 합니다.
        lastDamagedTime.put(player.getUniqueId(), System.currentTimeMillis());

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    onCleanup(player);
                    return;
                }

                long timeSinceLastDamage = System.currentTimeMillis() - lastDamagedTime.getOrDefault(player.getUniqueId(), 0L);
                if (timeSinceLastDamage > 30000) {
                    double currentAbsorption = player.getAbsorptionAmount();
                    if (currentAbsorption < MAX_ABSORPTION_AMOUNT) {
                        player.setAbsorptionAmount(Math.min(MAX_ABSORPTION_AMOUNT, currentAbsorption + 2.0));
                    }
                }
            }
        }.runTaskTimer(DF_Main.getInstance(), 200L, 200L); // 10초마다 실행

        shieldTasks.put(player.getUniqueId(), task);
    }

    @Override
    public void onCleanup(Player player) {
        BukkitTask task = shieldTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        lastDamagedTime.remove(player.getUniqueId());
    }

    @Override
    public void onEntityDamage(EntityDamageEvent event, Player player, ItemStack item) {
        lastDamagedTime.put(player.getUniqueId(), System.currentTimeMillis());
    }
}