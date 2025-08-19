package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.SuperJumpAbility;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LeggingsProfile implements IUpgradeableProfile {

    private static final ISpecialAbility SUPER_JUMP_ABILITY = new SuperJumpAbility();
    private static final UUID ARMOR_MODIFIER_UUID = UUID.fromString("5D6F0BA2-1186-46AC-B896-C674AE6CE383");
    private static final UUID ARMOR_TOUGHNESS_MODIFIER_UUID = UUID.fromString("845DB27C-C624-495F-8C9F-6020A9A58B6B");
    private static final UUID KNOCKBACK_RESISTANCE_MODIFIER_UUID = UUID.fromString("2AD3F246-FEE1-4E67-B886-69FD380BB150");
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("b326b504-0544-449d-a31a-545243b85390");

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level, List<String> lore) {


        // 1. 아이템의 모든 관련 속성을 초기화합니다.
        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR);
        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        meta.removeAttributeModifier(Attribute.GENERIC_MAX_HEALTH);

        // 2. 아이템의 기본 방어 관련 속성들을 다시 적용합니다.
        applyBaseArmorAttributes(item.getType(), meta);

        // 3. 강화 레벨에 따른 추가 체력 보너스를 적용합니다.
        double healthBonus = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.leggings.health-per-level", 0.0) * level;
        if (healthBonus > 0) {
            AttributeModifier healthModifier = new AttributeModifier(HEALTH_MODIFIER_UUID, "upgrade.maxHealth", healthBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.LEGS);
            meta.addAttributeModifier(Attribute.GENERIC_MAX_HEALTH, healthModifier);
        }

        // --- 로어 표시 수정 ---
        // 4. 기본 속성 표시(녹색 줄)를 숨깁니다.
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // 5. 강화 보너스 로어를 추가합니다.
        lore.removeIf(line -> line.contains("추가 체력:"));
        if (healthBonus > 0) {
            lore.add("");
            lore.add("§b추가 체력: +" + String.format("%.0f", healthBonus));
        }

        // 6. 기존에 있을 수 있는 바닐라 스타일 로어를 먼저 제거합니다.
        lore.removeIf(line -> line.contains("방어") || line.contains("방어 강도") || line.contains("밀치기 저항") || line.contains("최대 체력") || line.contains("다리에 있을 때:"));

        // 7. 바닐라 스타일로 속성 정보를 로어에 직접 추가합니다.
        lore.add("§7다리에 있을 때:");

        // 로어에 표시할 값을 다시 계산합니다.
        double armor = getBaseArmorAttribute(item.getType(), "armor");
        double toughness = getBaseArmorAttribute(item.getType(), "toughness");
        double knockbackResistance = getBaseArmorAttribute(item.getType(), "knockbackResistance");

        if (armor > 0) lore.add("§2 " + String.format("%.0f", armor) + " 방어");
        if (toughness > 0) lore.add("§2 " + String.format("%.0f", toughness) + " 방어 강도");
        if (knockbackResistance > 0) lore.add("§2 " + String.format("%.1f", knockbackResistance) + " 밀치기 저항");
        if (healthBonus > 0) lore.add("§2 +" + String.format("%.0f", healthBonus) + " 최대 체력");
    }

    private void applyBaseArmorAttributes(Material material, ItemMeta meta) {
        double armor = 0, toughness = 0, knockbackResistance = 0;

        switch (material) {
            case LEATHER_LEGGINGS -> armor = 2;
            case CHAINMAIL_LEGGINGS -> armor = 4;
            case GOLDEN_LEGGINGS -> armor = 3;
            case IRON_LEGGINGS -> armor = 5;
            case DIAMOND_LEGGINGS -> { armor = 6; toughness = 2; }
            case NETHERITE_LEGGINGS -> { armor = 6; toughness = 3; knockbackResistance = 0.1; }
        }

        if (armor > 0) {
            AttributeModifier armorModifier = new AttributeModifier(ARMOR_MODIFIER_UUID, "generic.armor", armor, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.LEGS);
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR, armorModifier);
        }
        if (toughness > 0) {
            AttributeModifier toughnessModifier = new AttributeModifier(ARMOR_TOUGHNESS_MODIFIER_UUID, "generic.armorToughness", toughness, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.LEGS);
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, toughnessModifier);
        }
        if (knockbackResistance > 0) {
            AttributeModifier knockbackModifier = new AttributeModifier(KNOCKBACK_RESISTANCE_MODIFIER_UUID, "generic.knockbackResistance", knockbackResistance, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.LEGS);
            meta.addAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE, knockbackModifier);
        }
    }

    private double getBaseArmorAttribute(Material material, String type) {
        return switch (material) {
            case LEATHER_LEGGINGS -> "armor".equals(type) ? 2 : 0;
            case CHAINMAIL_LEGGINGS -> "armor".equals(type) ? 4 : 0;
            case GOLDEN_LEGGINGS -> "armor".equals(type) ? 3 : 0;
            case IRON_LEGGINGS -> "armor".equals(type) ? 5 : 0;
            case DIAMOND_LEGGINGS -> switch (type) { case "armor" -> 6; case "toughness" -> 2; default -> 0; };
            case NETHERITE_LEGGINGS -> switch (type) { case "armor" -> 6; case "toughness" -> 3; case "knockbackResistance" -> 0.1; default -> 0; };
            default -> 0;
        };
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(SUPER_JUMP_ABILITY);
    }
}