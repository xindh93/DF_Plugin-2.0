package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.SwordDanceAbility;
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

public class SwordProfile implements IUpgradeableProfile {

    private static final ISpecialAbility SWORD_DANCE_ABILITY = new SwordDanceAbility();
    private static final UUID ATTACK_DAMAGE_MODIFIER_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID BASE_ATTACK_SPEED_MODIFIER_UUID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA2");

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 1. 기존 공격 관련 속성을 모두 제거합니다.
        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);

        // --- 실제 속성 적용 (내부적으로만) ---
        // 2. 공격 피해 속성을 적용합니다.
        double damageModifierValue = getBaseDamageModifier(item.getType());
        if (damageModifierValue > 0) {
            AttributeModifier damageModifier = new AttributeModifier(ATTACK_DAMAGE_MODIFIER_UUID, "weapon.damage", damageModifierValue, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
            meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, damageModifier);
        }

        // 3. 공격 속도 속성을 적용합니다.
        final double baseAttackSpeedAttribute = -2.4; // 4.0 (base) - 2.4 = 1.6
        double speedBonusPerLevel = DF_Main.getInstance().getGameConfigManager().getConfig()
                .getDouble("upgrade.generic-bonuses.sword.attack-speed-per-level", 0.3);
        double totalBonus = speedBonusPerLevel * level;
        double finalAttackSpeedModifierValue = baseAttackSpeedAttribute + totalBonus;

        AttributeModifier speedModifier = new AttributeModifier(BASE_ATTACK_SPEED_MODIFIER_UUID, "weapon.attack_speed", finalAttackSpeedModifierValue, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND);
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, speedModifier);

        // --- 로어 표시 수정 ---
        // 4. 기본 속성 표시(녹색 줄)를 숨깁니다.
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // 5. 패시브 능력 로어 업데이트 (공격 속도 보너스)
        lore.removeIf(line -> line.contains("추가 공격속도:"));
        if (level > 0) {
            lore.add("");
            lore.add("§b추가 공격속도: +" + String.format("%.1f", totalBonus));
        }

        // 6. 바닐라 스타일로 속성 정보를 로어에 직접 추가합니다.
        // 기존에 있을 수 있는 수동 로어를 먼저 제거합니다.
        lore.removeIf(line -> line.contains("공격 피해") || line.contains("공격 속도") || line.contains("주로 사용하는 손에 있을 때:"));

        lore.add("§7주로 사용하는 손에 있을 때:");

        // 최종 공격 피해 계산 및 추가
        double finalDamage = 1.0 + damageModifierValue;
        lore.add("§2 " + String.format("%.1f", finalDamage) + " 공격 피해");

        // 최종 공격 속도 계산 및 추가
        double finalSpeed = 4.0 + finalAttackSpeedModifierValue; // 4.0 + (-2.4 + bonus) = 1.6 + bonus
        lore.add("§2 " + String.format("%.1f", finalSpeed) + " 공격 속도");
    }

    private double getBaseDamageModifier(Material material) {
        return switch (material) {
            case WOODEN_SWORD, GOLDEN_SWORD -> 3.0;
            case STONE_SWORD -> 4.0;
            case IRON_SWORD -> 5.0;
            case DIAMOND_SWORD -> 6.0;
            case NETHERITE_SWORD -> 7.0;
            default -> 0.0;
        };
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(SWORD_DANCE_ABILITY);
    }
}