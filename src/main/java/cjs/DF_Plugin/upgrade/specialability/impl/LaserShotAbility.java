package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.settings.GameConfigManager;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.metadata.FixedMetadataValue;

public class LaserShotAbility implements ISpecialAbility {

    // 레이저 샷으로 인한 속도 증가량을 화살에 기록하기 위한 키
    private static final String LASER_SHOT_VELOCITY_KEY = "laser_shot_velocity_multiplier";

    @Override
    public String getInternalName() {
        return "laser_shot";
    }

    @Override
    public String getDisplayName() {
        return "§c레이저 샷";
    }

    @Override
    public String getDescription() {
        return "§7쇠뇌에서 레이저를 발사합니다.";
    }

    @Override
    public double getCooldown() {
        return 0; // 이 능력은 10강 전용 패시브이므로, 별도의 쿨다운이 없습니다.
    }

    @Override
    public void onEntityShootBow(EntityShootBowEvent event, Player player, ItemStack item) {
        if (!(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }

        UpgradeManager upgradeManager = DF_Main.getInstance().getUpgradeManager();
        GameConfigManager configManager = DF_Main.getInstance().getGameConfigManager();
        int level = upgradeManager.getUpgradeLevel(item);
        int requiredLevel = configManager.getConfig().getInt("upgrade.special-abilities.laser_shot.details.required-level", 10);

        if (level >= requiredLevel) {
            double velocityMultiplier = configManager.getConfig().getDouble("upgrade.special-abilities.laser_shot.details.velocity-multiplier", 3.0);
            arrow.setVelocity(arrow.getVelocity().multiply(velocityMultiplier));
            // 속도 증가량을 메타데이터에 저장하여, 피격 시 대미지를 보정하는 데 사용합니다.
            arrow.setMetadata(LASER_SHOT_VELOCITY_KEY, new FixedMetadataValue(DF_Main.getInstance(), velocityMultiplier));
        }
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event, Player player, ItemStack item) {
        // 이 능력은 공격자(player)의 무기에서 발동됩니다.
        if (!(event.getDamager() instanceof Arrow arrow) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        // 이 화살이 '레이저 샷'으로 발사된 것인지 확인합니다.
        if (!arrow.hasMetadata(LASER_SHOT_VELOCITY_KEY)) {
            return;
        }

        // 1. 속도 증가로 인해 비정상적으로 증폭된 기본 대미지를 원래대로 보정합니다.
        double velocityMultiplier = arrow.getMetadata(LASER_SHOT_VELOCITY_KEY).get(0).asDouble();
        double originalBaseDamage = event.getDamage() / velocityMultiplier;
        event.setDamage(originalBaseDamage);

        // 2. 10강 전용 발광 효과를 부여합니다.
        GameConfigManager configManager = DF_Main.getInstance().getGameConfigManager();
        int glowDuration = configManager.getConfig().getInt("upgrade.special-abilities.laser_shot.details.glow-duration-seconds", 10);
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowDuration * 20, 0, true, false));
    }
}