package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.settings.GameConfigManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.SuperchargeAbility;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BowProfile implements IUpgradeableProfile {

    private static final ISpecialAbility SUPERCHARGE_ABILITY = new SuperchargeAbility();

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 1. 패시브 능력 로어 (체력 비례 데미지)
        lore.removeIf(line -> line.contains("적 체력에 비례한 추가 피해"));
        if (level > 0) {
            GameConfigManager configManager = DF_Main.getInstance().getGameConfigManager();
            // 올바른 config 경로 사용
            double percentPerLevel = configManager.getConfig().getDouble("upgrade.generic-bonuses.bow.passive-max-health-damage-percent-per-level", 1.5);
            double maxPercent = configManager.getConfig().getDouble("upgrade.generic-bonuses.bow.passive-max-health-damage-max-percent", 15.0);

            double healthPercentage = Math.min(percentPerLevel * level, maxPercent);
            lore.add("");
            lore.add("§b적 체력에 비례한 추가 피해: " + String.format("%.1f", healthPercentage) + "%");
        }
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(SUPERCHARGE_ABILITY);
    }
}