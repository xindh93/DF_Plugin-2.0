// C:/Users/CJS/IdeaProjects/DF_Plugin-2.0/src/main/java/cjs/DF_Plugin/upgrade/profile/type/PickaxeProfile.java
package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.GrapplingHookAbility;
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

public class PickaxeProfile implements IUpgradeableProfile {
    private static final ISpecialAbility GRAPPLING_HOOK_ABILITY = new GrapplingHookAbility();
    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 1. 기존 공격 관련 속성을 초기화합니다.
        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);

        // 2. 아이템의 기본 공격 속성을 다시 적용합니다.
        applyBaseAttackAttributes(item.getType(), meta);

        // 3. 강화 레벨에 따른 추가 공격력을 적용합니다.
        double damagePerLevel = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.attribute-bonuses.pickaxe.damage-per-level", 0.5);
        double bonusDamage = level * damagePerLevel;

        if (bonusDamage > 0) {
            AttributeModifier damageMod = new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "upgrade_damage"), bonusDamage, AttributeModifier.Operation.ADD_NUMBER);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, damageMod);
        }

        // 4. 기존 인챈트 로직을 적용합니다.
        // 레벨에 따라 효율과 내구성을 번갈아 올림
        Map<Enchantment, Double> enchantBonuses = new LinkedHashMap<>();
        enchantBonuses.put(Enchantment.EFFICIENCY, 0.5); // 2레벨당 1
        enchantBonuses.put(Enchantment.UNBREAKING, 0.5); // 2레벨당 1
        UpgradeManager.applyCyclingEnchantments(meta, level, enchantBonuses);
    }

    private void applyBaseAttackAttributes(Material material, ItemMeta meta) {
        double attackDamage = 0;
        double attackSpeed = -2.8; // 곡괭이는 모두 동일

        switch (material) {
            case WOODEN_PICKAXE, GOLDEN_PICKAXE -> attackDamage = 1.0;
            case STONE_PICKAXE -> attackDamage = 2.0;
            case IRON_PICKAXE -> attackDamage = 3.0;
            case DIAMOND_PICKAXE -> attackDamage = 4.0;
            case NETHERITE_PICKAXE -> attackDamage = 5.0;
        }

        if (attackDamage > 0) {
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "weapon_damage"), attackDamage, AttributeModifier.Operation.ADD_NUMBER));
        }
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "weapon_speed"), attackSpeed, AttributeModifier.Operation.ADD_NUMBER));
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(GRAPPLING_HOOK_ABILITY);
    }
}