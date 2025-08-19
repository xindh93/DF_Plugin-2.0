package cjs.DF_Plugin.upgrade;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.items.ItemNameManager;
import cjs.DF_Plugin.items.UpgradeItems;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.profile.ProfileRegistry;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class UpgradeManager {

    private final DF_Main plugin;
    private final ProfileRegistry profileRegistry;
    private final Random random = new Random();
    public static final int MAX_UPGRADE_LEVEL = 10;

    // 특수 능력 및 아이템 식별을 위한 키
    public static final NamespacedKey ITEM_UUID_KEY = new NamespacedKey(DF_Main.getInstance(), "item_uuid");
    public static final NamespacedKey SPECIAL_ABILITY_KEY = new NamespacedKey(DF_Main.getInstance(), "special_ability");
    public static final NamespacedKey TRIDENT_MODE_KEY = new NamespacedKey(DF_Main.getInstance(), "trident_mode");

    public UpgradeManager(DF_Main plugin) {
        this.plugin = plugin;
        this.profileRegistry = new ProfileRegistry();
    }

    public int getUpgradeLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                // 별이 포함된 라인을 찾습니다.
                if (line.contains("★") || line.contains("☆")) {
                    int level = 0;
                    // 색상 코드를 제거하고 채워진 별의 개수를 셉니다.
                    for (char c : ChatColor.stripColor(line).toCharArray()) {
                        if (c == '★') {
                            level++;
                        }
                    }
                    return level;
                }
            }
        }
        return 0;
    }

    public void attemptUpgrade(Player player, ItemStack item) {
        // 1. 강화 대상 분석
        IUpgradeableProfile profile = this.profileRegistry.getProfile(item.getType());
        if (profile == null) {
            player.sendMessage("§c이 아이템은 강화할 수 없습니다.");
            return;
        }

        // 3. 강화 정보 불러오기 (레벨)
        final int currentLevel = getUpgradeLevel(item);

        // 2. 강화 비용 확인
        int requiredStones = currentLevel + 1;
        if (!hasEnoughStones(player, requiredStones)) {
            player.sendMessage(ChatColor.RED + "강화석이 부족합니다! (필요: " + requiredStones + "개)");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.8f);
            return;
        }

        if (currentLevel >= MAX_UPGRADE_LEVEL) {
            player.sendMessage("§c최대 강화 레벨에 도달했습니다.");
            return;
        }

        // 4. 강화 실행
        consumeStones(player, requiredStones); // 강화석 소모
        FileConfiguration config = plugin.getGameConfigManager().getConfig();
        String path = "upgrade.level-settings." + currentLevel;

        if (!config.isConfigurationSection(path)) {
            player.sendMessage("§c다음 강화 레벨에 대한 설정이 없습니다. (레벨: " + currentLevel + ")");
            // 설정이 없으면 강화석 환불
            player.getInventory().addItem(UpgradeItems.createUpgradeStone(requiredStones));
            return;
        }

        double successChance = config.getDouble(path + ".success", 0.0);
        double failureChance = config.getDouble(path + ".failure", 0.0);
        double downgradeChance = config.getDouble(path + ".downgrade", 0.0);
        // 파괴 확률을 명시적으로 읽어와 부동소수점 오류 및 설정 오류 가능성을 줄입니다.
        double destroyChance = config.getDouble(path + ".destroy", 0.0);

        // 확률의 총합을 기준으로 굴림을 정규화하여, 합계가 1이 아니더라도 의도대로 작동하게 합니다.
        double totalChance = successChance + failureChance + downgradeChance + destroyChance;
        if (totalChance <= 0) {
            // 확률 설정이 잘못된 경우, 안전하게 실패(유지) 처리합니다.
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            return;
        }

        double roll = random.nextDouble() * totalChance;

        if (roll < successChance) {
            // 성공
            int newLevel = currentLevel + 1;
            setUpgradeLevel(item, newLevel);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);
            // 10강 달성 시 전설 알림
            if (newLevel == MAX_UPGRADE_LEVEL) {
                handleLegendaryUpgrade(player, item);
            }
        } else if (roll < successChance + failureChance) {
            // 실패 (변화 없음)
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
        } else if (roll < successChance + failureChance + downgradeChance) { // This now corresponds to the destroyChance part of the roll
            // 등급 하락
            int newLevel = Math.max(0, currentLevel - 1);
            setUpgradeLevel(item, newLevel);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.5f);
        } else {
            // 파괴
            String itemName = item.getItemMeta() != null && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name();
            Bukkit.broadcastMessage("§c[!] §e" + player.getName() + "§7님이 §f" + itemName + " §c(+" + currentLevel + ")§7 강화에 실패하여 아이템이 파괴되었습니다.");
            item.setAmount(0);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.05);
        }
    }

    public void switchTridentMode(Player player, ItemStack trident) {
        if (trident == null || trident.getType() != Material.TRIDENT) return;

        // 모드 변환 쿨다운을 확인합니다.
        SpecialAbilityManager abilityManager = plugin.getSpecialAbilityManager();
        if (abilityManager.getRemainingCooldown(player, SpecialAbilityManager.MODE_SWITCH_COOLDOWN_KEY) > 0) {
            // 쿨다운 중에는 아무런 동작도, 소리도 없이 조용히 막습니다.
            return;
        }

        ItemMeta originalMeta = trident.getItemMeta();
        if (originalMeta == null) return;

        // 1. 기존 삼지창의 모든 정보를 추출합니다.
        final int level = getUpgradeLevel(trident);
        final String customName = originalMeta.getPersistentDataContainer().get(ItemNameManager.CUSTOM_NAME_KEY, PersistentDataType.STRING);
        final String customColorChar = originalMeta.getPersistentDataContainer().get(ItemNameManager.CUSTOM_COLOR_KEY, PersistentDataType.STRING);
        final String itemUUID = originalMeta.getPersistentDataContainer().get(ITEM_UUID_KEY, PersistentDataType.STRING);
        final String currentMode = originalMeta.getPersistentDataContainer().getOrDefault(TRIDENT_MODE_KEY, PersistentDataType.STRING, "backflow");

        // 이전 모드의 능력 효과를 정리하고 쿨다운을 초기화합니다.
        ISpecialAbility oldAbility = abilityManager.getRegisteredAbility(currentMode);
        if (oldAbility != null) {
            abilityManager.resetCooldown(player, oldAbility);
        }

        // 2. 새로운 모드를 결정합니다.
        final String newMode = currentMode.equals("backflow") ? "lightning_spear" : "backflow";

        // 3. 새로운 아이템을 생성하고, 추출한 정보를 복사합니다.
        ItemStack newTrident = new ItemStack(Material.TRIDENT);
        ItemMeta newMeta = newTrident.getItemMeta();

        // 커스텀 이름/색상/UUID 정보 복사
        if (customName != null) newMeta.getPersistentDataContainer().set(ItemNameManager.CUSTOM_NAME_KEY, PersistentDataType.STRING, customName);
        if (customColorChar != null) newMeta.getPersistentDataContainer().set(ItemNameManager.CUSTOM_COLOR_KEY, PersistentDataType.STRING, customColorChar);
        if (itemUUID != null) newMeta.getPersistentDataContainer().set(ITEM_UUID_KEY, PersistentDataType.STRING, itemUUID);

        // 새로운 모드 정보 설정
        newMeta.getPersistentDataContainer().set(TRIDENT_MODE_KEY, PersistentDataType.STRING, newMode);
        newTrident.setItemMeta(newMeta);

        // 4. setUpgradeLevel을 호출하여 이름, 로어, 모드별 인챈트 등 모든 속성을 최종적으로 설정합니다.
        setUpgradeLevel(newTrident, level);

        // 5. 플레이어 인벤토리의 아이템을 새로운 아이템으로 교체합니다.
        if (trident.isSimilar(player.getInventory().getItemInMainHand())) {
            player.getInventory().setItemInMainHand(newTrident);
        } else if (trident.isSimilar(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(newTrident);
        } else {
            // 혹시 모를 경우를 대비해, 인벤토리에서 직접 찾아 교체합니다.
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                if (trident.isSimilar(player.getInventory().getItem(i))) {
                    player.getInventory().setItem(i, newTrident);
                    break;
                }
            }
        }

        // 6. 모드 변경 후 즉시 패시브 효과(공전)가 적용되도록 강제로 능력을 업데이트합니다.
        abilityManager.forceEquipAndCleanup(player, newTrident);

        // 7. 쿨다운을 설정하고 피드백 메시지 및 사운드를 재생합니다.
        abilityManager.setCooldown(player, SpecialAbilityManager.MODE_SWITCH_COOLDOWN_KEY, SpecialAbilityManager.MODE_SWITCH_COOLDOWN_SECONDS, "모드 변환");
        String modeName = newMode.equals("backflow") ? "§3역류" : "§b뇌창";
        player.sendMessage(ChatColor.GREEN + "삼지창 모드를 " + modeName + "§a(으)로 변경했습니다.");
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.2f);
    }


    public void setUpgradeLevel(ItemStack item, int newLevel) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        IUpgradeableProfile profile = profileRegistry.getProfile(item.getType());
        if (profile == null) {
            // 강화 불가능한 아이템에 대해선 로직을 실행하지 않음.
            return;
        }

        // 레벨에 따라 아이템 이름 처리 (전설의 접두사)
        // PDC에서 커스텀 이름과 색상을 가져옵니다.
        String customName = meta.getPersistentDataContainer().get(ItemNameManager.CUSTOM_NAME_KEY, PersistentDataType.STRING);
        String customColorChar = meta.getPersistentDataContainer().get(ItemNameManager.CUSTOM_COLOR_KEY, PersistentDataType.STRING);

        // 2. 기본 이름(접두사 제외) 결정
        String baseName;
        if (customName != null && !customName.isEmpty()) {
            // 커스텀 이름이 있으면 최우선으로 사용
            baseName = customName;
        } else if (meta.hasDisplayName()) {
            // 커스텀 이름은 없지만 기존 이름이 있는 경우 (강화된 아이템)
            String currentName = ChatColor.stripColor(meta.getDisplayName());
            // "전설의" 접두사가 있다면 제거
            if (currentName.startsWith("전설의 ")) {
                currentName = currentName.substring("전설의 ".length()).trim();
            }
            // " [역류]" or " [뇌창]" 같은 모드 표시가 있다면 제거하여 순수 이름만 추출
            int modeIndex = currentName.lastIndexOf(" [");
            if (modeIndex != -1) {
                currentName = currentName.substring(0, modeIndex).trim();
            }
            baseName = currentName;
        } else {
            // 커스텀 이름도, 기존 이름도 없는 순수 바닐라 아이템
            baseName = null;
        }

        // 3. 최종 이름 설정
        if (newLevel >= MAX_UPGRADE_LEVEL) {
            String colorCode = (customColorChar != null) ? "§" + customColorChar : "§6"; // 커스텀 색상 또는 기본 금색
            // 10강에서는 이름이 반드시 필요하므로, baseName이 없으면 영문명이라도 사용
            String nameToUse = (baseName != null) ? baseName : item.getType().name().toLowerCase().replace('_', ' ');
            String finalName = colorCode + "전설의 " + nameToUse;

            meta.setDisplayName(finalName);
        } else if (baseName != null) {
            // 0-9강이고, 기본 이름이 있는 경우 (커스텀 또는 강화 다운그레이드)
            meta.setDisplayName("§f" + baseName);
        } else {
            // 0-9강이고, 순수 바닐라 아이템인 경우 -> 이름 미설정으로 클라이언트 번역 유지
            meta.setDisplayName(null);
        }

        List<String> lore = new ArrayList<>();

        // 1. 새로운 별 표시 추가
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < newLevel; i++) {
            stars.append(ChatColor.GOLD).append("★");
        }
        for (int i = newLevel; i < MAX_UPGRADE_LEVEL; i++) {
            stars.append(ChatColor.GRAY).append("☆");
        }
        lore.add(stars.toString().trim());
        lore.add(""); // 간격을 위한 빈 줄

        // 2. 다음 레벨의 확률 정보 추가
        FileConfiguration config = plugin.getGameConfigManager().getConfig();
        if (!config.getBoolean("upgrade.show-success-chance", true)) {
            // 설정이 꺼져있으면 아무것도 하지 않음
        } else if (newLevel >= MAX_UPGRADE_LEVEL) {
            // 아이템이 이미 최대 레벨에 도달한 경우
            lore.add(ChatColor.GOLD + "최대 강화 레벨에 도달했습니다!");
        } else {
            // 다음 강화 레벨에 대한 정보를 yml에서 찾습니다.
            String path = "upgrade.level-settings." + newLevel;
            if (config.isConfigurationSection(path)) {
                double success = config.getDouble(path + ".success", 0.0) * 100;
                double failure = config.getDouble(path + ".failure", 0.0) * 100;
                double downgrade = config.getDouble(path + ".downgrade", 0.0) * 100;
                double destroy = 100 - success - failure - downgrade;

                lore.add(ChatColor.GREEN + "성공 확률: " + String.format("%.1f", success) + "%");
                lore.add(ChatColor.YELLOW + "실패(유지) 확률: " + String.format("%.1f", failure) + "%");
                lore.add(ChatColor.RED + "하락 확률: " + String.format("%.1f", downgrade) + "%");
                lore.add(ChatColor.DARK_RED + "파괴 확률: " + String.format("%.1f", Math.max(0, destroy)) + "%");
            } else {
                lore.add(ChatColor.GRAY + "다음 강화 정보가 없습니다.");
            }
        }

        // 3. 프로필별 속성 적용 (스탯, 특수능력 로어, 인챈트 등)
        profile.applyAttributes(item, meta, newLevel, lore);

        // 4. 비-삼지창 아이템의 특수 능력 처리 (기존 로직 유지)
        if (item.getType() != Material.TRIDENT && profile.getSpecialAbility().isPresent()) {
            profile.getSpecialAbility().ifPresent(ability -> {
                meta.getPersistentDataContainer().set(SPECIAL_ABILITY_KEY, PersistentDataType.STRING, ability.getInternalName());
                if (newLevel >= MAX_UPGRADE_LEVEL) {
                    lore.add(""); // 간격
                    String abilityName = ChatColor.stripColor(ability.getDisplayName());
                    lore.add("§f[§b특수능력§f] : §b" + abilityName);
                    lore.add(ability.getDescription());
                    if (!meta.getPersistentDataContainer().has(ITEM_UUID_KEY, PersistentDataType.STRING)) {
                        meta.getPersistentDataContainer().set(ITEM_UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
                    }
                }
            });
        }

        // 5. 최종 적용
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private void handleLegendaryUpgrade(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String legendaryName = meta.getDisplayName();

        // 모든 플레이어에게 메시지 전송 및 소리 재생
        String message = "§6[!] " + player.getName() + "님에 의해 " + legendaryName + "§6이(가) 탄생했습니다!";
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }

    private boolean hasEnoughStones(Player player, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && UpgradeItems.isUpgradeStone(item)) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    private void consumeStones(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (remaining <= 0) break;
            ItemStack item = contents[i];
            if (item != null && UpgradeItems.isUpgradeStone(item)) {
                int toTake = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - toTake);
                remaining -= toTake;
                if (item.getAmount() <= 0) {
                    player.getInventory().setItem(i, null);
                }
            }
        }
    }

    public static void applyCyclingEnchantments(ItemMeta meta, int level, Map<Enchantment, Double> enchantBonuses) {
        // 사용자의 요청에 따라, 강화 레벨에 따른 모든 자동 인챈트 부여 로직을 비활성화합니다.
        // 이 메서드는 이제 아무 작업도 수행하지 않습니다.
        return;
    }

    /**
     * 아이템에 강화 레벨과 특정 인챈트를 동시에 설정합니다.
     * @param item 대상 아이템
     * @param level 설정할 레벨
     * @param enchantment 부여할 인챈트
     * @param enchantLevel 인챈트 레벨
     * @return 수정된 아이템
     */
    public ItemStack setItemLevel(ItemStack item, int level, Enchantment enchantment, int enchantLevel) {
        IUpgradeableProfile profile = profileRegistry.getProfile(item.getType());
        if (profile != null) {
            // 프로필이 존재하면, 레벨 설정 로직을 통해 이름, 로어, 기본 속성을 적용합니다.
            setUpgradeLevel(item, level);
        }

        // 프로필 존재 여부와 관계없이 요청된 특정 인챈트를 추가로 부여합니다.
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(enchantment, enchantLevel, true);
        item.setItemMeta(meta);
        return item;
    }


    public ProfileRegistry getProfileRegistry() {
        return profileRegistry;
    }
}