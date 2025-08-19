// C:/Users/CJS/IdeaProjects/DF_Plugin-2.0/src/main/java/cjs/DF_Plugin/upgrade/profile/type/FishingRodProfile.java
package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.GrabAbility;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;

public class FishingRodProfile implements IUpgradeableProfile {

    private static final ISpecialAbility GRAB_ABILITY = new GrabAbility();

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 1. 패시브 능력 로어 (낚시찌 속도 증가)
        lore.removeIf(line -> line.contains("추가 낚시찌 속도:"));
        if (level > 0) {
            double velocityBonusPerLevel = DF_Main.getInstance().getGameConfigManager().getConfig()
                    .getDouble("upgrade.generic-bonuses.fishing_rod.velocity-bonus-per-level", 0.4);
            double totalBonus = velocityBonusPerLevel * level * 100; // 퍼센트로 표시

            DecimalFormat df = new DecimalFormat("#.#");
            lore.add("");
            lore.add("§b추가 낚시찌 속도: +" + df.format(totalBonus) + "%");
        }
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(GRAB_ABILITY);
    }
}