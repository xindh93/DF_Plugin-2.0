package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.PassiveAbsorptionAbility;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ChestplateProfile implements IUpgradeableProfile {
    private static final ISpecialAbility PASSIVE_ABSORPTION_ABILITY = new PassiveAbsorptionAbility();
    private static final UUID ARMOR_MODIFIER_UUID = UUID.fromString("9F3D476D-C118-4544-8365-64846904B48E");
    private static final UUID ARMOR_TOUGHNESS_MODIFIER_UUID = UUID.fromString("D8499B04-0E66-4726-AB29-64469D734E0D");
    private static final UUID KNOCKBACK_RESISTANCE_MODIFIER_UUID = UUID.fromString("556E1665-8B10-40C8-8F9D-CF9B1667F295");
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("a6107045-134f-4c14-a645-5e53f63434e3");

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 1. 기존 속성을 모두 초기화하여 중첩을 방지합니다.
        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR);
        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        meta.removeAttributeModifier(Attribute.GENERIC_MAX_HEALTH);

        // 2. 아이템의 기본 방어 관련 속성들을 다시 적용합니다.
        applyBaseArmorAttributes(item.getType(), meta);

        // 3. 강화 레벨에 따른 추가 체력을 적용합니다.
        double healthBonus = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.chestplate.health-per-level", 2.0) * level;
        if (healthBonus > 0) {
            // UUID를 고정하여 중첩을 방지합니다.
            AttributeModifier healthModifier = new AttributeModifier(HEALTH_MODIFIER_UUID, "upgrade_health", healthBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.CHEST);
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
        lore.removeIf(line -> line.contains("방어") || line.contains("방어 강도") || line.contains("밀치기 저항") || line.contains("최대 체력") || line.contains("몸에 있을 때:"));

        // 7. 바닐라 스타일로 속성 정보를 로어에 직접 추가합니다.
        lore.add("§7몸에 있을 때:");

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
            case LEATHER_CHESTPLATE -> armor = 3;
            case CHAINMAIL_CHESTPLATE, GOLDEN_CHESTPLATE -> armor = 5;
            case IRON_CHESTPLATE -> armor = 6;
            case DIAMOND_CHESTPLATE -> { armor = 8; toughness = 2; }
            case NETHERITE_CHESTPLATE -> { armor = 8; toughness = 3; knockbackResistance = 0.1; }
        }

        if (armor > 0) {
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(ARMOR_MODIFIER_UUID, "generic.armor", armor, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.CHEST));
        }
        if (toughness > 0) {
            meta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, new AttributeModifier(ARMOR_TOUGHNESS_MODIFIER_UUID, "generic.armorToughness", toughness, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.CHEST));
        }
        if (knockbackResistance > 0) {
            meta.addAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE, new AttributeModifier(KNOCKBACK_RESISTANCE_MODIFIER_UUID, "generic.knockbackResistance", knockbackResistance, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.CHEST));
        }
    }

    private double getBaseArmorAttribute(Material material, String type) {
        return switch (material) {
            case LEATHER_CHESTPLATE -> "armor".equals(type) ? 3 : 0;
            case CHAINMAIL_CHESTPLATE, GOLDEN_CHESTPLATE -> "armor".equals(type) ? 5 : 0;
            case IRON_CHESTPLATE -> "armor".equals(type) ? 6 : 0;
            case DIAMOND_CHESTPLATE -> switch (type) {
                case "armor" -> 8; case "toughness" -> 2; default -> 0;
            };
            case NETHERITE_CHESTPLATE -> switch (type) {
                case "armor" -> 8; case "toughness" -> 3; case "knockbackResistance" -> 0.1; default -> 0;
            };
            default -> 0;
        };
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(PASSIVE_ABSORPTION_ABILITY);
    }
}