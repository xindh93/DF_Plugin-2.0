package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.settings.GameConfigManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.SuperchargeAbility;
import org.bukkit.ChatColor;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Optional;

public class BowProfile implements IUpgradeableProfile {

    private static final ISpecialAbility SUPERCHARGE_ABILITY = new SuperchargeAbility();

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 과거 코드에 따라, 활은 강화 레벨에 비례한 추가 피해를 로어에 직접 기록합니다.
        // 기존 로어 라인 제거
        lore.removeIf(line -> line.contains("적 체력에 비례한 추가 피해"));

        // 새로운 로어 라인 추가
        if (level > 0) {
            GameConfigManager configManager = DF_Main.getInstance().getGameConfigManager();
            double percentPerLevel = configManager.getConfig().getDouble("upgrade.ability-attributes.supercharge.passive-max-health-damage-percent-per-level", 1.5);
            double maxPercent = configManager.getConfig().getDouble("upgrade.ability-attributes.supercharge.passive-max-health-damage-max-percent", 15.0);

            double healthPercentage = Math.min(percentPerLevel * level, maxPercent);
            lore.add(ChatColor.BLUE + "적 체력에 비례한 추가 피해: " + String.format("%.1f", healthPercentage) + "%");
        }
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(SUPERCHARGE_ABILITY);
    }
}