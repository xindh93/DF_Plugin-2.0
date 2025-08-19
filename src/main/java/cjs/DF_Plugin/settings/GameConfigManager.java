package cjs.DF_Plugin.settings;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Difficulty;
import org.bukkit.Material;

import java.io.File;

public class GameConfigManager {
    private final DF_Main plugin;
    private FileConfiguration config;

    public GameConfigManager(DF_Main plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // 기본값 설정 및 파일에 없는 항목 추가
        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void save() {
        plugin.saveConfig();
    }

    public void resetToDefaults() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (configFile.exists()) {
            if (!configFile.delete()) {
                plugin.getLogger().warning("config.yml 파일을 삭제할 수 없습니다. 수동으로 삭제 후 재시작해주세요.");
                return;
            }
        }
        plugin.saveDefaultConfig();
        reloadConfig();
    }

    // --- Pylon Getters ---
    public int getPylonMaxPerClan() { return config.getInt("pylon.max-pylons-per-clan", 1); }
    public boolean isPylonRequireBelowSeaLevel() { return config.getBoolean("pylon.installation.require-below-sea-level", true); }

    public Material getMainCoreBaseMaterial() {
        String materialName = config.getString("pylon.installation.main-core-base-material", "DIAMOND_BLOCK").toUpperCase();
        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material name for 'pylon.installation.main-core-base-material': " + materialName + ". Defaulting to DIAMOND_BLOCK.");
            return Material.DIAMOND_BLOCK;
        }
    }

    public Material getAuxiliaryCoreBaseMaterial() {
        String materialName = config.getString("pylon.installation.auxiliary-core-base-material", "IRON_BLOCK").toUpperCase();
        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material name for 'pylon.installation.auxiliary-core-base-material': " + materialName + ". Defaulting to IRON_BLOCK.");
            return Material.IRON_BLOCK;
        }
    }
    public boolean isPylonAllyBuffEnabled() { return config.getBoolean("pylon.area-effects.ally-buff-enabled", true); }
    public boolean isPylonEnemyDebuffEnabled() { return config.getBoolean("pylon.area-effects.enemy-debuff-enabled", true); }
    public String getPylonRecruitmentMode() { return config.getString("pylon.recruitment.mode", "select"); }
    public int getPylonRecruitCostPerMember() { return config.getInt("pylon.recruitment.cost-per-member", 64); }
    public boolean isPylonDeathBanEnabled() { return config.getBoolean("pylon.death-ban.enabled", true); }
    public int getPylonDeathBanDurationMinutes() {return getConfig().getInt("pylon.death-ban.duration-minutes", 60);}    public int getPylonResurrectionCostPerMinute() { return config.getInt("pylon.death-ban.resurrection-cost-per-minute", 1); }
    public int getPylonRetrievalCooldownHours() { return config.getInt("pylon.retrieval.cooldown-hours", 24); }
    public int getPylonReinstallDurationHours() { return config.getInt("pylon.retrieval.reinstall-duration-hours", 2); }
    public boolean isPylonReconEnabled() { return config.getBoolean("pylon.recon-firework.enabled", true); }
    public int getPylonReconCooldownHours() { return config.getInt("pylon.recon-firework.cooldown-hours", 12); }
    public int getPylonReconReturnMinutes() { return config.getInt("pylon.recon-firework.return-duration-minutes", 1); }
    public int getPylonGiftboxCooldownMinutes() { return config.getInt("pylon.giftbox.cooldown-minutes", 5); }
    public int getPylonGiftboxMinItems() { return config.getInt("pylon.giftbox.min-reward-items", 1); }
    public int getPylonGiftboxMaxItems() { return config.getInt("pylon.giftbox.max-reward-items", 10); }
    public int getReturnScrollCastTime() { return config.getInt("pylon.return-scroll.cast-time-seconds", 5); }
    public boolean isReturnScrollAllowedInNether() { return config.getBoolean("pylon.return-scroll.allow-in-nether", true); }
    public boolean isReturnScrollAllowedInEnd() { return config.getBoolean("pylon.return-scroll.allow-in-end", true); }
    public int getPylonAreaEffectRadius() { return config.getInt("pylon.area-effects.radius", 50); }

    // --- Clan Getters ---
    public int getClanMaxMembers() { return config.getInt("pylon.recruitment.max-members", 4); }

    // --- Death & Respawn Getters ---
    public boolean isDeathTimerEnabled() { return config.getBoolean("death-timer.enabled", true); }
    public int getDeathTimerDurationMinutes() { return config.getInt("death-timer.time", 5); }

    // --- Upgrade System Getters ---
    public boolean isUpgradeShowSuccessChance() { return config.getBoolean("upgrade.show-success-chance", true); }
    public ConfigurationSection getUpgradeLevelSettings() { return config.getConfigurationSection("upgrade.level-settings"); }

    // --- System Toggles ---
    public boolean isPylonSystemEnabled() { return config.getBoolean("system-toggles.pylon", true); }
    public boolean isUpgradeSystemEnabled() { return config.getBoolean("system-toggles.upgrade", true); }
    public boolean isEventSystemEnabled() { return config.getBoolean("system-toggles.events", true); }


    // --- World & Game Rules Getters ---
    public boolean isWorldRuleKeepInventory() { return config.getBoolean("world.rules.keep-inventory", true); }
    public boolean isWorldRuleLocationInfoDisabled() { return config.getBoolean("world.rules.location-info-disabled", true); }
    public boolean isWorldRulePhantomDisabled() { return config.getBoolean("world.rules.phantom-disabled", true); }
    public boolean isWorldRuleTotemDisabled() { return config.getBoolean("world.rules.totem-disabled", true); }
    public boolean isWorldRuleEnderChestDisabled() { return config.getBoolean("world.rules.enderchest-disabled", true); }

    /**
     * 월드 규칙: 플레이어 위치 좌표 막대 비활성화 여부를 반환합니다.
     * @return 위치 좌표 막대가 비활성화 상태이면 true
     */
    public boolean isWorldRuleLocatorBarDisabled() { return config.getBoolean("world.rules.locator-bar-disabled", true); }
    public Difficulty getWorldDifficulty() {
        String difficultyName = config.getString("world.rules.difficulty", "HARD").toUpperCase();
        try {
            return Difficulty.valueOf(difficultyName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid difficulty '" + difficultyName + "' in config.yml. Defaulting to HARD.");
            return Difficulty.HARD;
        }
    }

    public double getWorldBorderOverworldSize() { return config.getDouble("world.border.overworld-size", 20000); }
    public boolean isWorldBorderEndEnabled() { return config.getBoolean("world.border.end-enabled", true); }
    public double getWorldBorderEndSize() { return config.getDouble("world.border.end-size", 1000); }


    // --- Item & Recipe Getters ---
    public boolean isOpEnchantBreachDisabled() { return config.getBoolean("items.op-enchant.breach-disabled", true); }
    public boolean isOpEnchantThornsDisabled() { return config.getBoolean("items.op-enchant.thorns-disabled", true); }

    // --- Utility Getters ---
    public boolean isClanChatPrefixEnabled() { return config.getBoolean("utility.clan-chat-prefix-enabled", true); }
    public boolean isKillLogEnabled() { return config.getBoolean("utility.kill-log-enabled", true); }
    public boolean isChatDisabled() { return config.getBoolean("utility.chat-disabled", false); }
}