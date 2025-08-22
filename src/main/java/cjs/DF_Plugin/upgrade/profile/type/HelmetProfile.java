package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.RegenerationAbility;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class HelmetProfile implements IUpgradeableProfile {

    private static final ISpecialAbility REGENERATION_ABILITY = new RegenerationAbility();
    private static final UUID ARMOR_MODIFIER_UUID = UUID.fromString("7E52A8B8-C811-4A55-9902-6310954568A5");
    private static final UUID TOUGHNESS_MODIFIER_UUID = UUID.fromString("9F1E52E2-1E3B-4C2A-8B6C-2D6C6C6067D5");
    private static final UUID KNOCKBACK_RESISTANCE_MODIFIER_UUID = UUID.fromString("A5E64A4F-B64D-4C3B-A9E5-6A1362B8A23E");

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        // 1. 아이템의 모든 관련 속성을 초기화합니다.
        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR);
        meta.removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE);

        // 2. 아이템의 기본 방어 관련 속성들을 다시 적용합니다.
        applyBaseArmorAttributes(item.getType(), meta);

        // 3. 강화 레벨에 따른 방어 인챈트 적용
        double fireProtBonus = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.helmet.fire-protection-per-level", 0.4);
        double blastProtBonus = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.helmet.blast-protection-per-level", 0.4);
        double projProtBonus = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.helmet.projectile-protection-per-level", 0.4);

        // 순서 보장을 위해 LinkedHashMap 사용
        Map<Enchantment, Double> enchantBonuses = new LinkedHashMap<>();
        enchantBonuses.put(Enchantment.FIRE_PROTECTION, fireProtBonus);
        enchantBonuses.put(Enchantment.BLAST_PROTECTION, blastProtBonus);
        enchantBonuses.put(Enchantment.PROJECTILE_PROTECTION, projProtBonus);

        UpgradeManager.applyCyclingEnchantments(meta, level, enchantBonuses);

        // 4. 기본 속성 표시(녹색 줄)를 숨깁니다.
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    @Override
    public List<String> getBaseStatsLore(ItemStack item, int level) {
        List<String> baseLore = new ArrayList<>();
        baseLore.add("§7머리에 있을 때:");

        double armor = getBaseArmorAttribute(item.getType(), "armor");
        double toughness = getBaseArmorAttribute(item.getType(), "toughness");
        double knockbackResistance = getBaseArmorAttribute(item.getType(), "knockbackResistance");

        if (armor > 0) baseLore.add("§2 " + String.format("%.0f", armor) + " 방어");
        if (toughness > 0) baseLore.add("§2 " + String.format("%.0f", toughness) + " 방어 강도");
        if (knockbackResistance > 0) baseLore.add("§2 " + String.format("%.1f", knockbackResistance) + " 밀치기 저항");
        return baseLore;
    }

    private void applyBaseArmorAttributes(Material material, ItemMeta meta) {
        double armor = getBaseArmorAttribute(material, "armor");
        double toughness = getBaseArmorAttribute(material, "toughness");
        double knockbackResistance = getBaseArmorAttribute(material, "knockbackResistance");

        if (armor > 0) meta.addAttributeModifier(Attribute.GENERIC_ARMOR, new AttributeModifier(ARMOR_MODIFIER_UUID, "generic_armor", armor, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HEAD));
        if (toughness > 0) meta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, new AttributeModifier(TOUGHNESS_MODIFIER_UUID, "generic_armor_toughness", toughness, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HEAD));
        if (knockbackResistance > 0) meta.addAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE, new AttributeModifier(KNOCKBACK_RESISTANCE_MODIFIER_UUID, "generic_knockback_resistance", knockbackResistance, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HEAD));
    }

    private double getBaseArmorAttribute(Material material, String type) {
        return switch (material) {
            case LEATHER_HELMET -> "armor".equals(type) ? 1 : 0;
            case CHAINMAIL_HELMET, GOLDEN_HELMET, TURTLE_HELMET -> "armor".equals(type) ? 2 : 0;
            case IRON_HELMET -> "armor".equals(type) ? 2 : 0;
            case DIAMOND_HELMET -> switch (type) {
                case "armor" -> 3; case "toughness" -> 2; default -> 0;
            };
            case NETHERITE_HELMET -> switch (type) {
                case "armor" -> 3; case "toughness" -> 3; case "knockbackResistance" -> 0.1; default -> 0;
            };
            default -> 0;
        };
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(REGENERATION_ABILITY);
    }
}