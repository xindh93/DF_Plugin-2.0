// C:/Users/CJS/IdeaProjects/DF_Plugin-2.0/src/main/java/cjs/DF_Plugin/upgrade/profile/type/AxeProfile.java
package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.CleansingAbility;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AxeProfile implements IUpgradeableProfile {

    private static final ISpecialAbility CLEANSING_ABILITY = new CleansingAbility();

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 1. 기존 공격 관련 속성을 초기화합니다.
        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);

        // 2. 아이템의 기본 공격 속성을 다시 적용합니다.
        applyBaseAttackAttributes(item.getType(), meta);

        // 3. 기존 인챈트 로직을 적용합니다.
        Map<Enchantment, Double> enchantBonuses = new LinkedHashMap<>();
        enchantBonuses.put(Enchantment.EFFICIENCY, 0.5);
        enchantBonuses.put(Enchantment.UNBREAKING, 0.5);
        UpgradeManager.applyCyclingEnchantments(meta, level, enchantBonuses);
    }

    private void applyBaseAttackAttributes(Material material, ItemMeta meta) {
        double attackDamage = 0;
        double attackSpeed = -3.2; // 기본값

        // 재질에 따른 기본 공격력 및 속도 설정 (기본 공격력 1을 제외한 값)
        switch (material) {
            case WOODEN_AXE, GOLDEN_AXE -> { attackDamage = 6.0; attackSpeed = -3.2; }
            case STONE_AXE, IRON_AXE, DIAMOND_AXE -> { attackDamage = 8.0; attackSpeed = -3.1; } // Iron/Diamond speed is slightly faster
            case NETHERITE_AXE -> { attackDamage = 9.0; attackSpeed = -3.0; }
        }

        if (attackDamage > 0) {
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "weapon_damage"), attackDamage, AttributeModifier.Operation.ADD_NUMBER));
        }
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "weapon_speed"), attackSpeed, AttributeModifier.Operation.ADD_NUMBER));
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(CLEANSING_ABILITY);
    }
}