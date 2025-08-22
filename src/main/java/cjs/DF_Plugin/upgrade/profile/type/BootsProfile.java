package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.DoubleJumpAbility;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BootsProfile implements IUpgradeableProfile {

    private static final ISpecialAbility DOUBLE_JUMP_ABILITY = new DoubleJumpAbility();
    private static final UUID ARMOR_MODIFIER_UUID = UUID.fromString("845DB27C-C624-495F-8C9F-6020A9A58B6B");
    private static final UUID ARMOR_TOUGHNESS_MODIFIER_UUID = UUID.fromString("D8499B04-0E66-4726-AB29-64469D734E0D");
    private static final UUID KNOCKBACK_RESISTANCE_MODIFIER_UUID = UUID.fromString("556E1665-8B10-40C8-8F9D-CF9B1667F295");
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("9F3D476D-C118-4544-8365-64846904B48E");

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level) {
        // 1. 아이템의 모든 관련 속성을 초기화합니다.
        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR);
        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        meta.removeAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED);

        // 2. 아이템의 기본 방어 관련 속성들을 다시 적용합니다.
        applyBaseArmorAttributes(item.getType(), meta);

        // 3. 새로운 강화 속성(이동 속도)을 계산하고 적용합니다.
        double speedBonus = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.boots.speed-multiplier-per-level", 0.05) * level;
        if (speedBonus > 0) {
            AttributeModifier speedModifier = new AttributeModifier(SPEED_MODIFIER_UUID, "upgrade.movementSpeed", speedBonus, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlot.FEET);
            meta.addAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, speedModifier);
        }

        // --- 로어 표시 수정 ---
        // 4. 기본 속성 표시(녹색 줄)를 숨깁니다.
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    @Override
    public List<String> getPassiveBonusLore(org.bukkit.inventory.ItemStack item, int level) {
        double speedBonus = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.boots.speed-multiplier-per-level", 0.05) * level;
        if (speedBonus > 0) {
            return List.of("§b추가 이동 속도: +" + String.format("%.0f", speedBonus * 100) + "%");
        }
        return Collections.emptyList();
    }

    @Override
    public List<String> getBaseStatsLore(org.bukkit.inventory.ItemStack item, int level) {
        List<String> baseLore = new ArrayList<>();
        baseLore.add("§7발에 있을 때:");

        double armor = getBaseArmorAttribute(item.getType(), "armor");
        double toughness = getBaseArmorAttribute(item.getType(), "toughness");
        double knockbackResistance = getBaseArmorAttribute(item.getType(), "knockbackResistance");
        double speedBonus = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.boots.speed-multiplier-per-level", 0.05) * level;

        if (armor > 0) baseLore.add("§2 " + String.format("%.0f", armor) + " 방어");
        if (toughness > 0) baseLore.add("§2 " + String.format("%.0f", toughness) + " 방어 강도");
        if (knockbackResistance > 0) baseLore.add("§2 " + String.format("%.1f", knockbackResistance) + " 밀치기 저항");
        if (speedBonus > 0) baseLore.add("§2 +" + String.format("%.0f", speedBonus * 100) + "% 이동 속도");

        return baseLore;
    }

    private void applyBaseArmorAttributes(Material material, ItemMeta meta) {
        double armor = 0, toughness = 0, knockbackResistance = 0;

        switch (material) {
            case LEATHER_BOOTS, CHAINMAIL_BOOTS, GOLDEN_BOOTS -> armor = 1;
            case IRON_BOOTS -> armor = 2;
            case DIAMOND_BOOTS -> { armor = 3; toughness = 2; }
            case NETHERITE_BOOTS -> { armor = 3; toughness = 3; knockbackResistance = 0.1; }
        }

        if (armor > 0) {
            AttributeModifier armorModifier = new AttributeModifier(ARMOR_MODIFIER_UUID, "generic.armor", armor, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.FEET);
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR, armorModifier);
        }
        if (toughness > 0) {
            AttributeModifier toughnessModifier = new AttributeModifier(ARMOR_TOUGHNESS_MODIFIER_UUID, "generic.armorToughness", toughness, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.FEET);
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, toughnessModifier);
        }
        if (knockbackResistance > 0) {
            AttributeModifier knockbackModifier = new AttributeModifier(KNOCKBACK_RESISTANCE_MODIFIER_UUID, "generic.knockbackResistance", knockbackResistance, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.FEET);
            meta.addAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE, knockbackModifier);
        }
    }

    private double getBaseArmorAttribute(Material material, String type) {
        return switch (material) {
            case LEATHER_BOOTS, CHAINMAIL_BOOTS, GOLDEN_BOOTS -> "armor".equals(type) ? 1 : 0;
            case IRON_BOOTS -> "armor".equals(type) ? 2 : 0;
            case DIAMOND_BOOTS -> switch (type) {
                case "armor" -> 3; case "toughness" -> 2; default -> 0;
            };
            case NETHERITE_BOOTS -> switch (type) {
                case "armor" -> 3; case "toughness" -> 3; case "knockbackResistance" -> 0.1; default -> 0;
            };
            default -> 0;
        };
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(DOUBLE_JUMP_ABILITY);
    }
}