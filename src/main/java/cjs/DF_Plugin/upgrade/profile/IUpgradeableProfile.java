package cjs.DF_Plugin.upgrade.profile;

import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface IUpgradeableProfile {
    void applyAttributes(ItemStack item, ItemMeta meta, int level, List<String> lore);

    default Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.empty();
    }

    default Collection<ISpecialAbility> getAdditionalAbilities() {
        return Collections.emptyList();
    }
}