package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class SwordDanceAbility implements ISpecialAbility {

    @Override
    public String getInternalName() {
        return "sword_dance";
    }

    @Override
    public String getDisplayName() {
        return "§b검무";
    }

    @Override
    public String getDescription() {
        return "§7모든 공격이 상대의 무적 시간을 무시합니다.";
    }

    @Override
    public double getCooldown() {
        return 0; // 패시브 능력이므로 쿨다운이 없습니다.
    }

    @Override
    public boolean showInActionBar() {
        // 패시브 능력이므로 액션바에 쿨다운을 표시할 필요가 없습니다.
        return false;
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event, org.bukkit.entity.Player player, ItemStack item) {
        if (event.getEntity() instanceof LivingEntity target) {
            // 다음 틱에 무적 시간을 0으로 설정하여, 연속적인 피해가 들어가도록 합니다.
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (target.isValid() && !target.isDead()) {
                        target.setNoDamageTicks(0);
                    }
                }
            }.runTaskLater(DF_Main.getInstance(), 1L);
        }
    }
}