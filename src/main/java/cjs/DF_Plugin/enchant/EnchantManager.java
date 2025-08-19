package cjs.DF_Plugin.enchant;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.settings.GameConfigManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class EnchantManager {

    private final DF_Main plugin;

    public EnchantManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    public void attemptEnchant(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (hasUpgradeStars(meta)) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.8f);
            return;
        }

        if (!hasMagicStone(player)) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.8f);
            return;
        }

        consumeMagicStone(player);
        enchantItem(item);

        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.0f);
    }

    private void enchantItem(ItemStack item) {
        GameConfigManager configManager = plugin.getGameConfigManager();
        double extraEnchantChance = configManager.getConfig().getDouble("enchanting.random-enchanting.extra-enchant-chance", 0.10);
        double curseChance = configManager.getConfig().getDouble("enchanting.random-enchanting.curse-chance", 0.01);
        int maxEnchantments = configManager.getConfig().getInt("enchanting.random-enchanting.max-enchantments", 8);

        Random random = new Random();

        // 제외할 인챈트 목록
        List<Enchantment> excludedEnchants = new ArrayList<>();
        // 저주는 기본적으로 제외
        excludedEnchants.add(Enchantment.BINDING_CURSE);
        excludedEnchants.add(Enchantment.VANISHING_CURSE);

        // 설정에 따라 OP 인챈트 제외
        if (configManager.isOpEnchantThornsDisabled()) {
            excludedEnchants.add(Enchantment.THORNS);
        }
        if (configManager.isOpEnchantBreachDisabled()) {
            excludedEnchants.add(Enchantment.BREACH);
        }

        // 1. 기존 인챈트와 저주 분리
        Map<Enchantment, Integer> existingEnchantments = new HashMap<>();
        Map<Enchantment, Integer> existingCurses = new HashMap<>();

        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            if (entry.getKey().isCursed()) {
                existingCurses.put(entry.getKey(), entry.getValue());
            } else {
                existingEnchantments.put(entry.getKey(), entry.getValue());
            }
        }

        // 2. 기존 인챈트 모두 제거 (저주는 유지)
        for (Enchantment enchantment : existingEnchantments.keySet()) {
            item.removeEnchantment(enchantment);
        }

        // 3. 새로운 인챈트 목록 준비 (기존 저주 포함)
        Map<Enchantment, Integer> newEnchantments = new HashMap<>(existingCurses);
        int baseEnchantmentCount = existingEnchantments.size();
        int currentEnchantmentCount;

        // 4. 기존 인챈트 개수만큼 새로운 인챈트 부여 (또는 최소 1개 보장)
        if (baseEnchantmentCount == 0) {
            addRandomEnchant(item, newEnchantments, excludedEnchants, random);
            currentEnchantmentCount = 1;
        } else {
            for (int i = 0; i < baseEnchantmentCount; i++) {
                addRandomEnchant(item, newEnchantments, excludedEnchants, random);
            }
            currentEnchantmentCount = baseEnchantmentCount;
        }

        // 5. 추가 인챈트 부여
        while (currentEnchantmentCount < maxEnchantments && random.nextDouble() < extraEnchantChance) {
            addRandomEnchant(item, newEnchantments, excludedEnchants, random);
            currentEnchantmentCount++;
        }

        // 6. 저주 추가 (기존에 없던 저주만)
        if (random.nextDouble() < curseChance) {
            newEnchantments.putIfAbsent(Enchantment.BINDING_CURSE, 1);
        }
        if (random.nextDouble() < curseChance) {
            newEnchantments.putIfAbsent(Enchantment.VANISHING_CURSE, 1);
        }

        // 7. 아이템에 최종 적용
        item.addUnsafeEnchantments(newEnchantments);
    }

    private void addRandomEnchant(ItemStack item, Map<Enchantment, Integer> currentEnchants, List<Enchantment> excluded, Random random) {
        Enchantment randomEnchant;
        int attempts = 0; // 무한 루프 방지
        do {
            randomEnchant = Enchantment.values()[random.nextInt(Enchantment.values().length)];
            attempts++;
            if (attempts > 1000) { // 1000번 시도 후에도 못찾으면 중단
                plugin.getLogger().warning("[EnchantManager] Could not find a compatible enchantment for " + item.getType() + " after 1000 attempts.");
                return;
            }
        } while (excluded.contains(randomEnchant) || currentEnchants.containsKey(randomEnchant));

        int level = random.nextInt(randomEnchant.getMaxLevel()) + 1;
        currentEnchants.put(randomEnchant, level);
    }

    private boolean hasMagicStone(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (MagicStone.isMagicStone(item)) {
                return true;
            }
        }
        return false;
    }

    private void consumeMagicStone(Player player) {
        for (ItemStack item : player.getInventory()) {
            if (MagicStone.isMagicStone(item)) {
                item.setAmount(item.getAmount() - 1);
                return;
            }
        }
    }

    private boolean hasUpgradeStars(ItemMeta meta) {
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null) {
                for (String line : lore) {
                    if (line.contains("§6★") || line.contains("§7☆")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}