// C:/Users/CJS/IdeaProjects/DF_Plugin-2.0/src/main/java/cjs/DF_Plugin/upgrade/profile/type/FishingRodProfile.java
package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.GrabAbility;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;

public class FishingRodProfile implements IUpgradeableProfile {

    private static final ISpecialAbility GRAB_ABILITY = new GrabAbility();

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 1. 패시브 능력 로어 (낚시찌 사거리 증가)
        lore.removeIf(line -> line.contains("낚시찌 사거리"));
        if (level > 0) {
            double rangeBonusPerLevel = DF_Main.getInstance().getGameConfigManager().getConfig()
                    .getDouble("upgrade.generic-bonuses.fishing_rod.range-bonus-per-level", 0.05);
            double totalBonus = rangeBonusPerLevel * level * 100; // 퍼센트로 표시

            DecimalFormat df = new DecimalFormat("#.#");
            lore.add("");
            lore.add("§b낚시찌 사거리: +" + df.format(totalBonus) + "%");
        }
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(GRAB_ABILITY);
    }
}