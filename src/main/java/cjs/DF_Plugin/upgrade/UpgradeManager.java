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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.scheduler.BukkitRunnable;

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
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.5f);
            // 10강 달성 시 전설 알림
            if (newLevel == MAX_UPGRADE_LEVEL) {
                handleLegendaryUpgrade(player, item);
            }
        } else if (roll < successChance + failureChance) {
            // 실패 (변화 없음)
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.0f);
        } else if (roll < successChance + failureChance + downgradeChance) { // This now corresponds to the destroyChance part of the roll
            // 등급 하락
            int newLevel = Math.max(0, currentLevel - 1);
            setUpgradeLevel(item, newLevel);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.5f);
        } else {
            // 파괴
            ItemStack destroyedItem = item.clone(); // Make a copy for the message
            String itemName = destroyedItem.getItemMeta() != null && destroyedItem.getItemMeta().hasDisplayName() ? destroyedItem.getItemMeta().getDisplayName() : destroyedItem.getType().name();

            // Adventure API를 사용하여 호버 가능한 메시지 생성
            Component hoverableItemName = LegacyComponentSerializer.legacySection().deserialize(itemName)
                    .hoverEvent(destroyedItem.asHoverEvent());

            Component broadcastMessage = Component.text()
                    .append(Component.text("[!] ", NamedTextColor.DARK_RED))
                    .append(Component.text("한 ", NamedTextColor.GRAY))
                    .append(hoverableItemName)
                    .append(Component.text(" (+" + currentLevel + ")", NamedTextColor.DARK_RED))
                    .append(Component.text(" 아이템이 강화에 실패하여 파괴되었습니다.", NamedTextColor.GRAY))
                    .build();
            Bukkit.broadcast(broadcastMessage);
            item.setAmount(0);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.0f);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.05);
        }
    }

    public ItemStack switchTridentMode(Player player, ItemStack originalTrident) {
        if (originalTrident == null || originalTrident.getType() != Material.TRIDENT) return originalTrident;

        // 모드 변환 쿨다운을 확인합니다.
        SpecialAbilityManager abilityManager = plugin.getSpecialAbilityManager();
        if (abilityManager.getRemainingCooldown(player, SpecialAbilityManager.MODE_SWITCH_COOLDOWN_KEY) > 0) {
            // 쿨다운 중에는 아무런 동작도, 소리도 없이 조용히 막습니다.
            return originalTrident;
        }

        // 원본 아이템을 복제하여 수정합니다.
        ItemStack trident = originalTrident.clone();
        ItemMeta meta = trident.getItemMeta();
        if (meta == null) return originalTrident;

        // 1. 강화 레벨과 현재 모드를 가져옵니다.
        final int level = getUpgradeLevel(trident);
        final String currentMode = meta.getPersistentDataContainer().getOrDefault(TRIDENT_MODE_KEY, PersistentDataType.STRING, "backflow");

        // 2. 새로운 모드를 결정합니다.
        final String newMode = currentMode.equals("backflow") ? "lightning_spear" : "backflow";

        // 3. 아이템의 메타데이터에 새로운 모드를 설정합니다.
        //    setUpgradeLevel이 이 값을 읽어 모든 속성을 재설정할 것입니다.
        meta.getPersistentDataContainer().set(TRIDENT_MODE_KEY, PersistentDataType.STRING, newMode);
        trident.setItemMeta(meta); // 변경된 모드 정보를 아이템에 즉시 적용합니다.

        // 4. setUpgradeLevel을 호출하여 아이템의 모든 속성(이름, 로어, 인챈트 등)을 새로운 모드에 맞게 재구성합니다.
        //    이것이 "생성 로직을 호출하는" 부분에 해당합니다.
        setUpgradeLevel(trident, level);

        // 5. 모드 변경 후 즉시 패시브 효과(공전)가 적용되도록 강제로 능력을 업데이트합니다.
        abilityManager.forceEquipAndCleanup(player, trident);

        // 6. 쿨다운을 설정하고 피드백 메시지 및 사운드를 재생합니다.
        abilityManager.setCooldown(player, SpecialAbilityManager.MODE_SWITCH_COOLDOWN_KEY, SpecialAbilityManager.MODE_SWITCH_COOLDOWN_SECONDS, "§7모드 변환");
        String modeName;
        if (newMode.equals("backflow")) {
            modeName = "§3역류";
        } else { // lightning_spear
            modeName = "§b뇌창";
        }
        // 모드 전환 시 특이한 천둥 소리(번개가 내리치는 소리)로 설정합니다.
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.0f);

        // 7. 모드 변경 파티클 효과 재생
        // [버그 수정] 파티클 색상이 반대로 적용되던 문제를 수정합니다. (뇌창: 노란색, 역류: 푸른색)
        final Color particleColor = newMode.equals("lightning_spear") ? Color.YELLOW : Color.BLUE;
        new BukkitRunnable() {
            private double angle = 0;
            private double height = 0;
            private final double radius = 0.7;
            private final int duration = 30; // 1.5초 (틱 단위)
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= duration || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                height = (double) ticks / duration * 2.2; // 2.2 블록 높이까지 상승
                angle += 15; // 매 틱 15도씩 회전

                for (int i = 0; i < 5; i++) { // 5갈래 파티클
                    double strandAngle = Math.toRadians(angle + (i * 72)); // 360 / 5 = 72도 간격
                    double x = Math.cos(strandAngle) * radius;
                    double z = Math.sin(strandAngle) * radius;

                    Location particleLoc = player.getLocation().add(x, height, z);
                    Particle.DustOptions dustOptions = new Particle.DustOptions(particleColor, 1.0f);
                    player.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, dustOptions);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return trident;
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
        String finalName;
        if (newLevel >= MAX_UPGRADE_LEVEL) {
            String colorCode = (customColorChar != null) ? "§" + customColorChar : "§6"; // 커스텀 색상 또는 기본 금색
            // 10강에서는 이름이 반드시 필요하므로, baseName이 없으면 영문명이라도 사용
            String nameToUse = (baseName != null) ? baseName : item.getType().name().toLowerCase().replace('_', ' ');
            finalName = colorCode + "전설의 " + nameToUse;

        } else if (baseName != null) {
            // 0-9강이고, 기본 이름이 있는 경우 (커스텀 또는 강화 다운그레이드)
            finalName = "§f" + baseName;
        } else {
            // 0-9강이고, 순수 바닐라 아이템인 경우 -> 이름 미설정으로 클라이언트 번역 유지
            finalName = null;
        }

        meta.setDisplayName(finalName);

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

        // 3. 프로필별 속성 및 로어를 먼저 적용합니다.
        // 이렇게 하면 보너스 스탯 로어와 기본 스탯 로어가 모두 추가됩니다.
        profile.applyAttributes(item, meta, newLevel, lore);

        // 4. 특수 능력 로어를 보너스 스탯과 기본 스탯 사이에 삽입합니다.
        if (newLevel >= MAX_UPGRADE_LEVEL && item.getType() != Material.TRIDENT) {
            int insertionPoint = -1;
            // "주로 사용하는 손에 있을 때:" 와 같은 라인을 찾아 그 앞에 삽입합니다.
            for (int i = 0; i < lore.size(); i++) {
                if (ChatColor.stripColor(lore.get(i)).endsWith("에 있을 때:")) {
                    insertionPoint = i;
                    break;
                }
            }

            Optional<ISpecialAbility> specialAbilityOpt = profile.getSpecialAbility();
            if (specialAbilityOpt.isPresent()) {
                ISpecialAbility ability = specialAbilityOpt.get();
                List<String> specialAbilityLore = new ArrayList<>();
                specialAbilityLore.add(""); // 간격
                specialAbilityLore.add("§5[특수능력]§f : " + ability.getDisplayName());
                specialAbilityLore.add(ability.getDescription());
                specialAbilityLore.add(""); // 간격

                if (insertionPoint != -1) {
                    lore.addAll(insertionPoint, specialAbilityLore);
                } else {
                    lore.addAll(specialAbilityLore); // 적절한 위치를 못 찾으면 맨 뒤에 추가
                }
            }
        }

        // 5. 특수 능력 키를 아이템에 저장합니다.
        // 삼지창의 이름과 로어는 TridentProfile에서 직접 처리하므로, 여기서는 다른 아이템만 처리합니다.
        if (item.getType() == Material.TRIDENT) {
            // 삼지창의 경우에도 능력 키는 저장해 주어야 정상적으로 작동합니다.
            String currentMode = meta.getPersistentDataContainer().getOrDefault(TRIDENT_MODE_KEY, PersistentDataType.STRING, "backflow");
            Optional.ofNullable(plugin.getSpecialAbilityManager().getRegisteredAbility(currentMode))
                    .ifPresent(ability -> meta.getPersistentDataContainer().set(SPECIAL_ABILITY_KEY, PersistentDataType.STRING, ability.getInternalName()));
        } else {
            // 다른 아이템은 프로필에 정의된 기본 능력을 따릅니다.
            Optional<ISpecialAbility> specialAbilityOpt = profile.getSpecialAbility();
            specialAbilityOpt.ifPresent(ability -> {
                // 아이템에 현재 활성화된 능력의 키를 저장합니다.
                meta.getPersistentDataContainer().set(SPECIAL_ABILITY_KEY, PersistentDataType.STRING, ability.getInternalName());
                // 10강일 때 고유 UUID를 부여합니다.
                if (newLevel >= MAX_UPGRADE_LEVEL) {
                    if (!meta.getPersistentDataContainer().has(ITEM_UUID_KEY, PersistentDataType.STRING)) {
                        meta.getPersistentDataContainer().set(ITEM_UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
                    }
                }
            });
        }

        // 6. 최종 적용
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private void handleLegendaryUpgrade(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String legendaryName = meta.getDisplayName();
        
        // Adventure API를 사용하여 호버 가능한 메시지 생성
        Component hoverableItemName = LegacyComponentSerializer.legacySection().deserialize(legendaryName)
                .hoverEvent(item.asHoverEvent());

        Component broadcastMessage = Component.text()
                .color(NamedTextColor.GOLD)
                .append(Component.text("[!] "))
                .append(hoverableItemName)
                .append(Component.text("이(가) 탄생했습니다!"))
                .build();
        // 모든 플레이어에게 메시지 전송 및 소리 재생
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(broadcastMessage);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
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
        // 먼저, 이 프로필에서 관리하는 모든 인챈트를 제거하여, 강화 등급이 변경될 때마다 레벨을 새로 계산합니다.
        for (Enchantment enchant : enchantBonuses.keySet()) {
            if (meta.hasEnchant(enchant)) {
                meta.removeEnchant(enchant);
            }
        }

        if (level <= 0) {
            return; // 강화 레벨이 0 이하면 아무 인챈트도 적용하지 않음
        }

        List<Enchantment> enchants = new ArrayList<>(enchantBonuses.keySet());
        int numEnchants = enchants.size();
        if (numEnchants == 0) {
            return;
        }

        // 특별 규칙: 3개의 인챈트가 있고 10강일 때, 모두 4레벨로 설정
        if (numEnchants == 3 && level == 10) {
            for (Enchantment enchant : enchants) {
                meta.addEnchant(enchant, 4, true);
            }
            return;
        }

        // 일반적인 번갈아 오르는 규칙 (Round-robin)
        int baseLevel = level / numEnchants;
        int extraLevels = level % numEnchants;

        for (int i = 0; i < numEnchants; i++) {
            Enchantment currentEnchant = enchants.get(i);
            int enchantLevel = baseLevel + (i < extraLevels ? 1 : 0);
            if (enchantLevel > 0) {
                meta.addEnchant(currentEnchant, enchantLevel, true);
            }
        }
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