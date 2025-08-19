package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.AbsorptionShieldAbility;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ChestplateProfile implements IUpgradeableProfile {
    private static final String ATTRIBUTE_NAME = "upgrade.health.chestplate";

    private static final ISpecialAbility ABSORPTION_SHIELD_ABILITY = new AbsorptionShieldAbility();

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 1. 아이템의 모든 관련 속성을 초기화합니다.
        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR);
        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        meta.removeAttributeModifier(Attribute.GENERIC_MAX_HEALTH);

        // 2. 아이템의 기본 방어 관련 속성들을 다시 적용합니다.
        applyBaseArmorAttributes(item.getType(), meta);

        // 3. 새로운 강화 속성(체력)을 계산하고 적용합니다.
        double valuePerLevel = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.chestplate.health-per-level", 0.0);
        double totalValue = valuePerLevel * level;
        if (totalValue > 0) {
            AttributeModifier mod = new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), ATTRIBUTE_NAME), totalValue, AttributeModifier.Operation.ADD_NUMBER);
            meta.addAttributeModifier(Attribute.GENERIC_MAX_HEALTH, mod);
        }
    }

    private void applyBaseArmorAttributes(Material material, ItemMeta meta) {
        double armor = 0, toughness = 0, knockbackResistance = 0;

        switch (material) {
            case LEATHER_CHESTPLATE -> armor = 3;
            case CHAINMAIL_CHESTPLATE, GOLDEN_CHESTPLATE -> armor = 5;
            case IRON_CHESTPLATE -> armor = 6;
            case DIAMOND_CHESTPLATE -> { armor = 8; toughness = 2; }
            case NETHERITE_CHESTPLATE -> { armor = 8; toughness = 3; knockbackResistance = 0.1; }
        }

        if (armor > 0) meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "generic_armor"), armor, AttributeModifier.Operation.ADD_NUMBER));
        if (toughness > 0) meta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "generic_armor_toughness"), toughness, AttributeModifier.Operation.ADD_NUMBER));
        if (knockbackResistance > 0) meta.addAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE, new AttributeModifier(new NamespacedKey(DF_Main.getInstance(), "generic_knockback_resistance"), knockbackResistance, AttributeModifier.Operation.ADD_NUMBER));
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(ABSORPTION_SHIELD_ABILITY);
    }
}