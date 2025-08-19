package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.LaserShotAbility;
import org.bukkit.ChatColor;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;

public class CrossbowProfile implements IUpgradeableProfile {

    private static final ISpecialAbility LASER_SHOT_ABILITY = new LaserShotAbility();

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 쇠뇌는 강화 레벨에 비례한 추가 피해를 로어에 직접 기록합니다.
        lore.removeIf(line -> line.contains("추가 고정 피해:"));

        if (level > 0) {
            double damagePerLevel = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.crossbow.damage-per-level", 0.5);
            double totalBonusDamage = damagePerLevel * level;
            lore.add("");
            lore.add("§b추가 고정 피해: +" + String.format("%.1f", totalBonusDamage));
        }
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(LASER_SHOT_ABILITY);
    }
}