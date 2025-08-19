package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.IgnoreInvulnerabilityAbility;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SwordProfile implements IUpgradeableProfile {

    private static final ISpecialAbility IGNORE_INVULNERABILITY_ABILITY = new IgnoreInvulnerabilityAbility();

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 기존의 모든 공격 관련 속성을 제거하여 초기화합니다.
        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);

        // 아이템의 기본 공격력을 다시 설정합니다.
        double baseAttackDamage = getBaseAttackDamage(item.getType());
        AttributeModifier damageModifier = new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "generic_attack_damage"), baseAttackDamage, AttributeModifier.Operation.ADD_NUMBER);
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, damageModifier);

        // 강화 레벨에 따른 공격 속도 보너스를 추가합니다.
        double attackSpeedBonus = level * 0.3; // 레벨당 0.3 증가
        if (attackSpeedBonus > 0) {
            AttributeModifier speedModifier = new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "upgrade_attack_speed"), attackSpeedBonus, AttributeModifier.Operation.ADD_NUMBER);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, speedModifier);
        }
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(IGNORE_INVULNERABILITY_ABILITY);
    }

    private double getBaseAttackDamage(Material material) {
        return switch (material) {
            case WOODEN_SWORD, GOLDEN_SWORD -> 4.0;
            case STONE_SWORD -> 5.0;
            case IRON_SWORD -> 6.0;
            case DIAMOND_SWORD -> 7.0;
            case NETHERITE_SWORD -> 8.0;
            default -> 0.0;
        };
    }
}