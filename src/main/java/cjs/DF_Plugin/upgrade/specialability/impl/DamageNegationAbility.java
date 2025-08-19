package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.util.ActionBarManager;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class DamageNegationAbility implements ISpecialAbility {
    @Override
    public String getInternalName() {
        return "damage_negation";
    }

    @Override
    public String getDisplayName() {
        return "§c피해 무효화";
    }

    @Override
    public String getDescription() {
        return "§7공격받았을 때, 일정 확률로 피해를 무효화합니다.";
    }

    @Override
    public double getCooldown() {
        // 성공적으로 발동한 후 적용될 쿨다운입니다.
        return DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.damage_negation.cooldown", 30.0);
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event, Player player, ItemStack item) {
        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();

        // 1. 능력이 이미 쿨다운 상태인지 확인합니다.
        if (manager.isOnCooldown(player, this, item)) {
            return;
        }

        // 2. 쿨다운이 아니라면, 발동 확률을 계산합니다.
        double chance = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.damage_negation.details.chance", 0.07);
        if (Math.random() < chance) {
            // 3. 발동 성공 시, 피해를 무효화하고 효과를 재생합니다.
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.2f);
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

            // 4. 플레이어에게 능력 발동을 알리고, 쿨다운을 설정합니다.
            ActionBarManager.sendActionBar(player, "§b" + getDisplayName() + "§b 발동! §7(피해 무효화)");
            manager.setCooldown(player, this, item, getCooldown());
        }
    }
}