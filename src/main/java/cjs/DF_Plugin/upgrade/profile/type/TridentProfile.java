package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import cjs.DF_Plugin.upgrade.specialability.impl.*;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TridentProfile implements IUpgradeableProfile {

    private static final ISpecialAbility BACKFLOW_ABILITY = new BackflowAbility();

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level, List<String> lore) {
        // 1. 강화 레벨에 따른 추가 투사체 로어
        if (level > 0) {
            lore.add(""); // 간격
            lore.add("§b추가 투사체: +" + level + "개");
        }

        // 2. 모드에 따른 능력, 이름, 인챈트 설정
        SpecialAbilityManager abilityManager = DF_Main.getInstance().getSpecialAbilityManager();
        String mode = meta.getPersistentDataContainer().getOrDefault(UpgradeManager.TRIDENT_MODE_KEY, PersistentDataType.STRING, "backflow");
        ISpecialAbility currentAbility = abilityManager.getRegisteredAbility(mode);

        if (currentAbility != null) {
            // 모드에 따른 인챈트 설정
            // 먼저 기존의 충성/급류 인챈트를 제거합니다.
            meta.removeEnchant(Enchantment.LOYALTY);
            meta.removeEnchant(Enchantment.RIPTIDE);

            // 새로운 모드에 맞는 인챈트를 부여합니다.
            if (mode.equals("backflow")) {
                meta.addEnchant(Enchantment.RIPTIDE, 3, true);
            } else if (mode.equals("lightning_spear")) {
                meta.addEnchant(Enchantment.LOYALTY, 3, true);
                meta.addEnchant(Enchantment.CHANNELING, 1, true);
            }

            // 10강 이상일 때만 특수 능력 설정 및 로어 추가
            if (level >= UpgradeManager.MAX_UPGRADE_LEVEL) {
                // 능력 키 설정
                meta.getPersistentDataContainer().set(UpgradeManager.SPECIAL_ABILITY_KEY, PersistentDataType.STRING, currentAbility.getInternalName());

                if (!meta.getPersistentDataContainer().has(UpgradeManager.ITEM_UUID_KEY, PersistentDataType.STRING)) {
                    meta.getPersistentDataContainer().set(UpgradeManager.ITEM_UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
                }

                // 특수 능력 로어 추가
                lore.add(""); // 간격
                // 삼지창의 [특수능력] 로어는 보라색으로 고정합니다.
                lore.add("§5[특수능력]§f : " + currentAbility.getDisplayName());
                lore.add(currentAbility.getDescription());
            } else {
                // 10강 미만일 때 특수 능력이 없도록 PDC를 제거합니다.
                meta.getPersistentDataContainer().remove(UpgradeManager.SPECIAL_ABILITY_KEY);
            }
            // 이름에 모드 접미사 추가
            if (meta.hasDisplayName()) {
                String currentName = meta.getDisplayName();
                // " [역류]" or " [뇌창]" 같은 모드 표시가 있다면 제거하여 순수 이름만 추출
                int modeIndex = currentName.lastIndexOf(" [");
                if (modeIndex != -1) {
                    currentName = currentName.substring(0, modeIndex).trim();
                }

                String displayName = currentAbility.getDisplayName(); // e.g., "§3역류"
                String colorCode = "§7"; // 기본 회색
                if (displayName.length() >= 2 && displayName.charAt(0) == '§') {
                    colorCode = displayName.substring(0, 2);
                }
                String abilityName = ChatColor.stripColor(displayName); // "역류"
                meta.setDisplayName(currentName + " " + colorCode + "[" + abilityName + "]");
            }
        }
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        // 기본 능력은 폭류로 설정. 실제 능력은 applyAttributes에서 모드에 따라 결정됨.
        return Optional.of(BACKFLOW_ABILITY);
    }

    @Override
    public Collection<ISpecialAbility> getAdditionalAbilities() {
        // 두 능력을 모두 등록해야 SpecialAbilityManager에서 찾을 수 있음.
        return List.of(new LightningSpearAbility());
    }
}