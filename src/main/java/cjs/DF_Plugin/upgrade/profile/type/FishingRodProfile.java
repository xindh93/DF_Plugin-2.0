// C:/Users/CJS/IdeaProjects/DF_Plugin-2.0/src/main/java/cjs/DF_Plugin/upgrade/profile/type/FishingRodProfile.java
package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.GrabAbility;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FishingRodProfile implements IUpgradeableProfile {

    private static final ISpecialAbility GRAB_ABILITY = new GrabAbility();

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 내구성, 미끼, 바다의 행운을 번갈아 올림
        Map<Enchantment, Double> enchantBonuses = new LinkedHashMap<>();
        enchantBonuses.put(Enchantment.UNBREAKING, 1.0/3.0);
        enchantBonuses.put(Enchantment.LURE, 1.0/3.0);
        enchantBonuses.put(Enchantment.LUCK_OF_THE_SEA, 1.0/3.0);
        UpgradeManager.applyCyclingEnchantments(meta, level, enchantBonuses);
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(GRAB_ABILITY);
    }
}