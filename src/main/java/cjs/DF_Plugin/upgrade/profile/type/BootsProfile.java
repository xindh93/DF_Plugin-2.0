package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.DoubleJumpAbility;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BootsProfile implements IUpgradeableProfile {

    private static final ISpecialAbility DOUBLE_JUMP_ABILITY = new DoubleJumpAbility();

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level, List<String> lore) {
        final String ATTRIBUTE_NAME = "upgrade.movement_speed";

        // 1. 아이템의 모든 관련 속성을 초기화합니다.
        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR);
        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        meta.removeAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED);

        // 2. 아이템의 기본 방어 관련 속성들을 다시 적용합니다.
        applyBaseArmorAttributes(item.getType(), meta);

        // 3. 새로운 강화 속성(이동 속도)을 계산하고 적용합니다.
        double speedBonusPerLevel = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.boots.speed-multiplier-per-level", 0.05);
        double totalValue = speedBonusPerLevel * level;

        if (totalValue > 0) {
            AttributeModifier mod = new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "upgrade_movement_speed"), totalValue, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            meta.addAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, mod);
        }
    }

    private void applyBaseArmorAttributes(Material material, ItemMeta meta) {
        double armor = 0, toughness = 0, knockbackResistance = 0;

        switch (material) {
            case LEATHER_BOOTS, CHAINMAIL_BOOTS, GOLDEN_BOOTS -> armor = 1;
            case IRON_BOOTS -> armor = 2;
            case DIAMOND_BOOTS -> { armor = 3; toughness = 2; }
            case NETHERITE_BOOTS -> { armor = 3; toughness = 3; knockbackResistance = 0.1; }
        }

        if (armor > 0) meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "generic_armor"), armor, AttributeModifier.Operation.ADD_NUMBER));
        if (toughness > 0) meta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "generic_armor_toughness"), toughness, AttributeModifier.Operation.ADD_NUMBER));
        if (knockbackResistance > 0) meta.addAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE, new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "generic_knockback_resistance"), knockbackResistance, AttributeModifier.Operation.ADD_NUMBER));
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(DOUBLE_JUMP_ABILITY);
    }
}