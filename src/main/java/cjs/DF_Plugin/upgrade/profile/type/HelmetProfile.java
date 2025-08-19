package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.RegenerationAbility;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HelmetProfile implements IUpgradeableProfile {

    private static final ISpecialAbility REGENERATION_ABILITY = new RegenerationAbility();

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 10강에서 목표 레벨 4를 만들기 위한 값 (10 * 0.4 = 4)
        // 이 값은 10강에서만 사용되며, 1-9강 사이클링 로직에는 영향을 주지 않습니다.
        final double bonusForLevel10 = 0.4;
        // 순서 보장을 위해 LinkedHashMap 사용
        Map<Enchantment, Double> enchantBonuses = new LinkedHashMap<>();
        enchantBonuses.put(Enchantment.FIRE_PROTECTION, bonusForLevel10);
        enchantBonuses.put(Enchantment.BLAST_PROTECTION, bonusForLevel10);
        enchantBonuses.put(Enchantment.PROJECTILE_PROTECTION, bonusForLevel10);

        UpgradeManager.applyCyclingEnchantments(meta, level, enchantBonuses);
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(REGENERATION_ABILITY);
    }
}