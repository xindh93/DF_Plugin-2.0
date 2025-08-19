// C:/Users/CJS/IdeaProjects/DF_Plugin-2.0/src/main/java/cjs/DF_Plugin/upgrade/profile/type/HoeProfile.java
package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.VampirismAbility;
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

public class HoeProfile implements IUpgradeableProfile {
    private static final ISpecialAbility VAMPIRISM_ABILITY = new VampirismAbility();
    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level, List<String> lore) {

        // 4. 기존 인챈트 로직을 적용합니다.
        Map<Enchantment, Double> enchantBonuses = new LinkedHashMap<>();
        enchantBonuses.put(Enchantment.EFFICIENCY, 0.5);
        enchantBonuses.put(Enchantment.UNBREAKING, 0.5);
        UpgradeManager.applyCyclingEnchantments(meta, level, enchantBonuses);
    }



    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(VAMPIRISM_ABILITY);
    }
}