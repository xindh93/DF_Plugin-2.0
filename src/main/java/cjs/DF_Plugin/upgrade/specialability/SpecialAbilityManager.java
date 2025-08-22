package cjs.DF_Plugin.upgrade.specialability;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.data.PlayerDataManager;
import cjs.DF_Plugin.upgrade.specialability.impl.TotemAbility;
import org.bukkit.event.Listener;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpecialAbilityManager {

    public static final NamespacedKey SPECIAL_ABILITY_KEY = new NamespacedKey(DF_Main.getInstance(), "special_ability");
    public static final NamespacedKey ITEM_UUID_KEY = new NamespacedKey(DF_Main.getInstance(), "item_uuid");

    private final DF_Main plugin;

    private final Map<UUID, Map<String, CooldownInfo>> playerCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, ChargeInfo>> playerCharges = new ConcurrentHashMap<>();
    // 플레이어 UUID -> (능력 이름 -> 능력을 제공하는 아이템)
    private final Map<UUID, Map<String, ItemStack>> lastActiveAbilities = new ConcurrentHashMap<>();
    private final Map<String, ISpecialAbility> registeredAbilities = new HashMap<>();

    // --- Constants for Mode Switching ---
    public static final String MODE_SWITCH_COOLDOWN_KEY = "internal_mode_switch";
    public static final double MODE_SWITCH_COOLDOWN_SECONDS = 45.0;

    // Record to hold cooldown information for the action bar
    public record CooldownInfo(long endTime, String displayName) {}
    // Record to hold charge information for the action bar
    public record ChargeInfo(int current, int max, String displayName, boolean visible, ISpecialAbility.ChargeDisplayType displayType) {}

    public SpecialAbilityManager(DF_Main plugin) {
        this.plugin = plugin;
        loadAllData();
        startPassiveTicker();
    }

    public void loadAllData() {
        // 데이터 로딩은 각 하위 메서드에서 처리합니다.
        loadCooldowns();
        loadCharges();
    }

    public void saveAllData() {
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        FileConfiguration config = pdm.getConfig();

        // 기존 데이터를 지우기 위해 players 섹션을 순회하며 abilities 관련 데이터만 제거
        if (config.isConfigurationSection("players")) {
            for (String uuidStr : config.getConfigurationSection("players").getKeys(false)) {
                config.set("players." + uuidStr + ".abilities", null);
            }
        }

        playerCooldowns.forEach((uuid, cooldownMap) -> {
            cooldownMap.forEach((key, info) -> {
                if (System.currentTimeMillis() < info.endTime()) {
                    String path = "players." + uuid + ".abilities.cooldowns." + key;
                    config.set(path + ".endTime", info.endTime());
                    config.set(path + ".displayName", info.displayName());
                }
            });
        });

        playerCharges.forEach((uuid, chargeMap) -> {
            chargeMap.forEach((key, info) -> {
                String path = "players." + uuid + ".abilities.charges." + key;
                config.set(path + ".current", info.current());
                config.set(path + ".max", info.max());
                config.set(path + ".displayName", info.displayName());
                config.set(path + ".visible", info.visible());
            });
        });

        int cooldownCount = (int) playerCooldowns.values().stream().mapToLong(Map::size).sum();
        int chargeCount = (int) playerCharges.values().stream().mapToLong(Map::size).sum();
        plugin.getLogger().info("[능력 관리] " + cooldownCount + "개의 쿨다운 정보와 " + chargeCount + "개의 충전 정보를 저장했습니다.");
        // PlayerDataManager가 파일 저장을 담당하므로 여기서는 호출하지 않음
        // DF_Main의 onDisable에서 한 번에 저장됩니다.
    }

    private void loadCooldowns() {
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        ConfigurationSection playersSection = pdm.getConfig().getConfigurationSection("players");
        if (playersSection == null) return;

        for (String uuidStr : playersSection.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            Map<String, CooldownInfo> cooldowns = new ConcurrentHashMap<>();
            ConfigurationSection cooldownsSection = playersSection.getConfigurationSection(uuidStr + ".abilities.cooldowns");
            if (cooldownsSection == null) continue;

            for (String key : cooldownsSection.getKeys(false)) {
                long endTime = cooldownsSection.getLong(key + ".endTime");
                if (System.currentTimeMillis() < endTime) {
                    String displayName = cooldownsSection.getString(key + ".displayName", "Ability");
                    cooldowns.put(key, new CooldownInfo(endTime, displayName));
                }
            }
            if (!cooldowns.isEmpty()) {
                playerCooldowns.put(uuid, cooldowns);
            }
        }
    }

    private void loadCharges() {
        playerCharges.clear();
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        ConfigurationSection playersSection = pdm.getConfig().getConfigurationSection("players");
        if (playersSection == null) return;

        for (String uuidStr : playersSection.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            Map<String, ChargeInfo> charges = new ConcurrentHashMap<>();
            ConfigurationSection chargesSection = playersSection.getConfigurationSection(uuidStr + ".abilities.charges");
            if (chargesSection == null) continue;

            for (String key : chargesSection.getKeys(false)) {
                int current = chargesSection.getInt(key + ".current");
                int max = chargesSection.getInt(key + ".max");
                String displayName = chargesSection.getString(key + ".displayName", "Ability");
                boolean visible = chargesSection.getBoolean(key + ".visible", false);
                ISpecialAbility ability = getRegisteredAbility(key);
                ISpecialAbility.ChargeDisplayType displayType = (ability != null) ? ability.getChargeDisplayType() : ISpecialAbility.ChargeDisplayType.DOTS;
                charges.put(key, new ChargeInfo(current, max, displayName, visible, displayType));
            }
            if (!charges.isEmpty()) {
                playerCharges.put(uuid, charges);
            }
        }
    }

    public void registerAbilities() {
        registeredAbilities.clear();
        // 모든 프로필과 수동 등록을 통해 능력들을 수집합니다.
        plugin.getUpgradeManager().getProfileRegistry().getAllProfiles()
                .forEach(profile -> {
                    profile.getSpecialAbility().ifPresent(ability ->
                            registeredAbilities.put(ability.getInternalName(), ability));

                    profile.getAdditionalAbilities().forEach(ability ->
                            registeredAbilities.put(ability.getInternalName(), ability));
                });

        // Manually register abilities that don't come from an item profile
        ISpecialAbility totem = new TotemAbility();
        registeredAbilities.put(totem.getInternalName(), totem);

        // Listener를 구현하는 능력들을 등록합니다.
        ISpecialAbility passiveAbsorption = new cjs.DF_Plugin.upgrade.specialability.impl.PassiveAbsorptionAbility();
        registeredAbilities.put(passiveAbsorption.getInternalName(), passiveAbsorption);

        // 등록된 모든 능력을 확인하고, Listener를 구현하는 경우 이벤트 리스너로 등록합니다.
        for (ISpecialAbility ability : registeredAbilities.values()) {
            if (ability instanceof Listener) {
                plugin.getServer().getPluginManager().registerEvents((Listener) ability, plugin);
            }
        }
    }

    /**
     * 플레이어의 상태를 정리합니다. (로그아웃, 사망 시 호출)
     * @param player 정리할 플레이어
     */
    public void cleanupPlayer(Player player) {
        // 플레이어가 나갈 때, 활성화된 모든 능력에 대해 정리 작업을 수행합니다.
        Map<String, ItemStack> lastAbilities = lastActiveAbilities.remove(player.getUniqueId());
        if (lastAbilities != null) {
            lastAbilities.forEach((abilityName, item) -> {
                Optional.ofNullable(getRegisteredAbility(abilityName)).ifPresent(ability -> ability.onCleanup(player));
            });
        }
    }

    /**
     * 서버 종료 시, 온라인 상태인 모든 플레이어의 활성화된 능력을 정리합니다.
     * 이는 onCleanup을 호출하여 액션바, 공전 삼지창 등을 올바르게 제거하고,
     * 이 상태가 파일에 저장되어 재접속 시 원치 않는 효과가 나타나는 문제를 방지합니다.
     */
    public void cleanupAllActiveAbilities() {
        // lastActiveAbilities 맵을 순회하며 온라인 플레이어에 대한 정리 작업을 수행합니다.
        // ConcurrentModificationException을 방지하기 위해 키셋의 복사본을 사용합니다.
        new HashSet<>(lastActiveAbilities.keySet()).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            // 플레이어가 온라인 상태일 때만 정리 작업을 수행합니다.
            // 오프라인 플레이어는 onPlayerQuit에서 이미 정리되었습니다.
            if (player != null && player.isOnline()) {
                cleanupPlayer(player);
            }
        });
    }

    /**
     * 특정 아이템에 대한 능력을 강제로 활성화하고, 이전 능력은 비활성화(정리)합니다.
     * 모드 변경처럼 즉각적인 반응이 필요할 때 사용됩니다.
     * @param player 대상 플레이어
     * @param newItem 새로 장착한 아이템
     */
    public void forceEquipAndCleanup(Player player, ItemStack newItem) {
        // 이 메서드는 이제 주기적으로 실행되는 Ticker와 동일한 로직을 즉시 실행합니다.
        checkPlayerEquipment(player);
    }

    private void startPassiveTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerEquipment(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 10L); // 1초 후 시작, 0.5초마다 반복
    }

    /**
     * 플레이어의 장비를 확인하여 능력의 장착/해제 상태를 갱신합니다.
     * @param player 확인할 플레이어
     */
    private void checkPlayerEquipment(Player player) {
        // 1. 현재 장착된 모든 아이템(갑옷+손)에서 능력 목록을 가져옵니다.
        Map<String, ItemStack> currentAbilities = new HashMap<>();
        List<ItemStack> itemsToCheck = new ArrayList<>(Arrays.asList(player.getInventory().getArmorContents()));
        itemsToCheck.add(player.getInventory().getItemInMainHand());
        itemsToCheck.add(player.getInventory().getItemInOffHand());

        for (ItemStack item : itemsToCheck) {
            if (item != null) {
                // 10강 이상인 아이템의 능력만 활성화 대상으로 간주합니다.
                if (plugin.getUpgradeManager().getUpgradeLevel(item) >= cjs.DF_Plugin.upgrade.UpgradeManager.MAX_UPGRADE_LEVEL) {
                    getAbilityFromItem(item).ifPresent(ability -> currentAbilities.put(ability.getInternalName(), item));
                }
            }
        }

        // 2. 이전 상태와 비교하여 추가/제거된 능력을 확인합니다.
        Map<String, ItemStack> lastAbilities = lastActiveAbilities.getOrDefault(player.getUniqueId(), Collections.emptyMap());

        Set<String> addedAbilityNames = new HashSet<>(currentAbilities.keySet());
        addedAbilityNames.removeAll(lastAbilities.keySet());

        Set<String> removedAbilityNames = new HashSet<>(lastAbilities.keySet());
        removedAbilityNames.removeAll(currentAbilities.keySet());

        // 4. 최종적으로 결정된 변경점에 따라 onEquip 및 onCleanup을 호출합니다.
        addedAbilityNames.forEach(abilityName -> Optional.ofNullable(getRegisteredAbility(abilityName)).ifPresent(ability -> ability.onEquip(player, currentAbilities.get(abilityName))));
        removedAbilityNames.forEach(abilityName -> Optional.ofNullable(getRegisteredAbility(abilityName)).ifPresent(ability -> ability.onCleanup(player)));

        // 5. 마지막 상태를 현재 상태로 업데이트합니다.
        lastActiveAbilities.put(player.getUniqueId(), currentAbilities);
    }

    /**
     * 플레이어가 특정 능력을 현재 활성화(장착)하고 있는지 확인합니다.
     * @param player 확인할 플레이어
     * @param abilityName 확인할 능력의 internal name
     * @return 능력이 활성화 상태이면 true
     */
    public boolean isAbilityActive(Player player, String abilityName) {
        Map<String, ItemStack> activeAbilities = lastActiveAbilities.get(player.getUniqueId());
        return activeAbilities != null && activeAbilities.containsKey(abilityName);
    }

    public ISpecialAbility getRegisteredAbility(String internalName) {
        return registeredAbilities.get(internalName);
    }

    /**
     * 능력이 쿨다운 상태인지 확인합니다. (사용자에게 메시지를 보내지 않음)
     * @return 쿨다운 상태이면 true
     */
    public boolean isOnCooldown(Player player, ISpecialAbility ability, ItemStack item) {
        return getRemainingCooldown(player, ability, item) > 0;
    }

    /**
     * 능력이 쿨다운 상태인지 확인합니다. (아이템과 무관)
     * @return 쿨다운 상태이면 true
     */
    public boolean isOnCooldown(Player player, ISpecialAbility ability) {
        return getRemainingCooldown(player, ability, null) > 0;
    }

    /**
     * 액티브 능력의 사용을 시도합니다.
     * 쿨다운과 충전 횟수를 모두 관리하고 액션바에 상태를 표시합니다.
     * @return 능력을 사용할 수 있으면 true
     */
    public boolean tryUseAbility(Player player, ISpecialAbility ability, ItemStack item) {
        long remainingMillis = getRemainingCooldown(player, ability, item);
        if (remainingMillis > 0) {
            // 쿨다운 중일 때는 액션바 매니저가 주기적으로 상태를 표시하므로,
            // 여기서는 별도의 메시지를 보내지 않습니다.
            return false;
        }

        if (ability.getMaxCharges() > 1) {
            String chargeKey = getChargeKey(ability);
            Map<String, ChargeInfo> charges = playerCharges.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
            int maxCharges = ability.getMaxCharges();
            ChargeInfo info = charges.getOrDefault(chargeKey, new ChargeInfo(maxCharges, maxCharges, ability.getDisplayName(), true, ability.getChargeDisplayType()));

            if (info.current() <= 0) {
                // 이 경우는 보통 쿨다운이 막 시작되었을 때 발생합니다. 쿨다운 메시지를 다시 표시해줍니다.
                setCooldown(player, ability, item, ability.getCooldown());
                return false;
            }

            int newCount = info.current() - 1;
            charges.put(chargeKey, new ChargeInfo(newCount, maxCharges, ability.getDisplayName(), true, ability.getChargeDisplayType()));

            if (newCount <= 0) {
                setCooldown(player, ability, item, ability.getCooldown());
                removeChargeInfo(player, ability);
            }
            return true;
        } else {
            setCooldown(player, ability, item, ability.getCooldown());
            return true;
        }
    }

    /**
     * Sets a cooldown for a specific ability with a custom duration.
     * @param player The player to set the cooldown for.
     * @param ability The ability to set the cooldown for.
     * @param item The item associated with the ability.
     * @param cooldownSeconds The duration of the cooldown in seconds.
     */
    public void setCooldown(Player player, ISpecialAbility ability, ItemStack item, double cooldownSeconds) {
        if (cooldownSeconds <= 0) return;
        long newEndTime = System.currentTimeMillis() + (long) (cooldownSeconds * 1000);
        String cooldownKey = getCooldownKey(player, ability, item);
        String displayName = ability.getDisplayName();

        Map<String, CooldownInfo> cooldowns = playerCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        if (ability.alwaysOverwriteCooldown()) { // 예: 더블점프 피격시 쿨타임
            cooldowns.put(cooldownKey, new CooldownInfo(newEndTime, displayName));
            return;
        }

        // 기존 쿨다운이 존재하고, 그 남은 시간이 새로 적용할 쿨다운보다 길다면, 기존 쿨다운을 유지합니다.
        // 이렇게 하면 짧은 쿨다운이 긴 쿨다운을 덮어쓰는 것을 방지합니다.
        CooldownInfo existingCooldown = cooldowns.get(cooldownKey);
        if (existingCooldown != null && existingCooldown.endTime() > newEndTime) {
            return; // 기존 쿨다운이 더 길므로 아무것도 하지 않음
        }

        // 새 쿨다운을 적용합니다.
        cooldowns.put(cooldownKey, new CooldownInfo(newEndTime, displayName));
    }

    /**
     * Sets a cooldown for a specific ability with a custom duration. (아이템과 무관)
     * @param player The player to set the cooldown for.
     * @param ability The ability to set the cooldown for.
     * @param cooldownSeconds The duration of the cooldown in seconds.
     */
    public void setCooldown(Player player, ISpecialAbility ability, double cooldownSeconds) {
        setCooldown(player, ability, null, cooldownSeconds);
    }

    public void setCooldown(Player player, String cooldownKey, double cooldownSeconds, String displayName) {
        if (cooldownSeconds <= 0) return;
        long newEndTime = System.currentTimeMillis() + (long) (cooldownSeconds * 1000);
        Map<String, CooldownInfo> cooldowns = playerCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        cooldowns.put(cooldownKey, new CooldownInfo(newEndTime, displayName));
    }

    /**
     * 특정 능력의 쿨다운을 즉시 초기화합니다.
     * @param player 대상 플레이어
     * @param ability 쿨다운을 초기화할 능력
     */
    public void resetCooldown(Player player, ISpecialAbility ability) {
        String cooldownKey = getCooldownKey(player, ability, null);
        Map<String, CooldownInfo> cooldowns = playerCooldowns.get(player.getUniqueId());
        if (cooldowns != null) {
            cooldowns.remove(cooldownKey);
            if (cooldowns.isEmpty()) {
                playerCooldowns.remove(player.getUniqueId());
            }
        }
    }

    /**
     * 특정 능력의 충전 횟수를 1회 되돌려줍니다. (최대치를 넘지 않음)
     * @param player 대상 플레이어
     * @param ability 충전량을 환불할 능력
     */

    public long getRemainingCooldown(Player player, ISpecialAbility ability, ItemStack item) {
        String cooldownKey = getCooldownKey(player, ability, item);
        return getRemainingCooldown(player, cooldownKey);
    }

    public long getRemainingCooldown(Player player, String cooldownKey) {
        Map<String, CooldownInfo> cooldowns = playerCooldowns.get(player.getUniqueId());
        if (cooldowns != null && cooldowns.containsKey(cooldownKey)) {
            long endTime = cooldowns.get(cooldownKey).endTime();
            return Math.max(0, endTime - System.currentTimeMillis());
        }
        return 0;
    }

    public void setChargeInfo(Player player, ISpecialAbility ability, int current, int max) {
        String chargeKey = getChargeKey(ability);
        String displayName = ability.getDisplayName();
        playerCharges.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(chargeKey, new ChargeInfo(current, max, displayName, true, ability.getChargeDisplayType()));
    }

    public void setChargeVisibility(Player player, ISpecialAbility ability, boolean visible) {
        String chargeKey = getChargeKey(ability);
        Map<String, ChargeInfo> charges = playerCharges.get(player.getUniqueId());
        if (charges != null) {
            ChargeInfo oldInfo = charges.get(chargeKey);
            // 정보가 존재하고, 가시성 상태가 변경될 때만 업데이트합니다.
            if (oldInfo != null && oldInfo.visible() != visible) {
                charges.put(chargeKey, new ChargeInfo(oldInfo.current(), oldInfo.max(), oldInfo.displayName(), visible, oldInfo.displayType()));
            }
        }
    }

    public ChargeInfo getChargeInfo(Player player, ISpecialAbility ability) {
        Map<String, ChargeInfo> charges = playerCharges.get(player.getUniqueId());
        if (charges != null) {
            return charges.get(getChargeKey(ability));
        }
        return null;
    }

    public void removeChargeInfo(Player player, ISpecialAbility ability) {
        Map<String, ChargeInfo> charges = playerCharges.get(player.getUniqueId());
        if (charges != null) {
            charges.remove(getChargeKey(ability));
            if (charges.isEmpty()) {
                playerCharges.remove(player.getUniqueId());
            }
        }
    }

    private String getChargeKey(ISpecialAbility ability) {
        return ability.getInternalName();
    }

    private String getCooldownKey(Player player, ISpecialAbility ability, ItemStack item) {
        // 이제 쿨다운은 아이템이 아닌 능력 자체를 기준으로 적용됩니다.
        // 이를 통해 같은 능력을 가진 다른 아이템들이 쿨다운을 공유합니다.
        return ability.getInternalName();
    }

    public Map<String, CooldownInfo> getPlayerCooldowns(UUID playerUUID) {
        return playerCooldowns.get(playerUUID);
    }

    public Map<String, ChargeInfo> getPlayerCharges(UUID playerUUID) {
        return playerCharges.get(playerUUID);
    }

    public Optional<ISpecialAbility> getAbilityFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String abilityKey = item.getItemMeta().getPersistentDataContainer().get(SPECIAL_ABILITY_KEY, PersistentDataType.STRING);
        if (abilityKey == null) {
            return Optional.empty();
        }

        // 등록된 능력 맵에서 해당 키를 가진 능력을 직접 찾아 반환합니다.
        // 이 아이템이 생성될 당시의 프로필이 현재 프로필과 다르더라도,
        // 아이템에 부여된 능력은 유효한 것으로 간주하여 안정성을 높입니다.
        return Optional.ofNullable(registeredAbilities.get(abilityKey));
    }

    public Collection<ISpecialAbility> getAllAbilities() {
        return registeredAbilities.values();
    }
}