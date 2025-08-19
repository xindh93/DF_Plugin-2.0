package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.IgnoreInvulnerabilityAbility;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;

public class SwordProfile implements IUpgradeableProfile {

    private static final ISpecialAbility IGNORE_INVULNERABILITY_ABILITY = new IgnoreInvulnerabilityAbility();

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 1. 패시브 능력 로어 (공격 속도)
        lore.removeIf(line -> line.contains("공격 속도"));
        if (level > 0) {
            double speedBonusPerLevel = DF_Main.getInstance().getGameConfigManager().getConfig()
                    .getDouble("upgrade.generic-bonuses.sword.attack-speed-per-level", 0.3);
            double totalBonus = speedBonusPerLevel * level;
            lore.add("");
            lore.add("§b공격 속도: +" + String.format("%.1f", totalBonus));
        }

        // 2. 10강 이상일 때 특수 능력 로어 추가
        if (level >= UpgradeManager.MAX_UPGRADE_LEVEL) {
            meta.getPersistentDataContainer().set(UpgradeManager.SPECIAL_ABILITY_KEY, PersistentDataType.STRING, IGNORE_INVULNERABILITY_ABILITY.getInternalName());
            lore.add(""); // 간격
            lore.add("§f[§b특수능력§f] : " + IGNORE_INVULNERABILITY_ABILITY.getDisplayName());
            lore.add(IGNORE_INVULNERABILITY_ABILITY.getDescription());
        }
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(IGNORE_INVULNERABILITY_ABILITY);
    }
}