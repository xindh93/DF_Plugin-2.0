package cjs.DF_Plugin.pylon.beacongui.giftbox;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.clan.ClanManager;
import cjs.DF_Plugin.enchant.MagicStone;
import cjs.DF_Plugin.items.UpgradeItems;
import cjs.DF_Plugin.settings.GameConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 모든 가문의 선물상자를 주기적으로 확인하고 리필하는 클래스.
 */
public class GiftBoxRefillTask extends BukkitRunnable {

    private final ClanManager clanManager;
    private final GameConfigManager configManager;
    private final Random random = new Random();
    private static long nextRefillTime = 0L;

    public GiftBoxRefillTask(DF_Main plugin) {
        this.clanManager = plugin.getClanManager();
        this.configManager = plugin.getGameConfigManager();
        if (nextRefillTime == 0L) {
            // 서버 시작 시 첫 리필 시간을 설정합니다.
            long cooldownMillis = TimeUnit.MINUTES.toMillis(configManager.getConfig().getLong("pylon.giftbox.cooldown-minutes", 5));
            nextRefillTime = System.currentTimeMillis() + cooldownMillis;
        }
    }

    @Override
    public void run() {
        // 다음 리필 시간이 되었는지 확인합니다.
        if (System.currentTimeMillis() < nextRefillTime) {
            return;
        }

        // 모든 가문의 선물상자를 리필합니다.
        for (Clan clan : clanManager.getAllClans()) {
            Inventory giftBox = clanManager.getGiftBoxInventory(clan);
            giftBox.clear();

            // 지급될 아이템 개수를 1~10개 사이에서 무작위로 결정합니다.
            int minItems = configManager.getConfig().getInt("pylon.giftbox.min-reward-items", 1);
            int maxItems = configManager.getConfig().getInt("pylon.giftbox.max-reward-items", 10);
            int totalAmount = (minItems >= maxItems) ? minItems : ThreadLocalRandom.current().nextInt(minItems, maxItems + 1);

            // 선물상자의 비어있는 모든 슬롯을 가져와 무작위로 섞습니다.
            List<Integer> emptySlots = new ArrayList<>();
            for (int i = 0; i < giftBox.getSize(); i++) {
                emptySlots.add(i);
            }
            Collections.shuffle(emptySlots);

            for (int i = 0; i < totalAmount && !emptySlots.isEmpty(); i++) {
                // 각 아이템마다 종류를 새로 결정합니다.
                boolean isMagicStone = random.nextBoolean();
                ItemStack rewardItem = isMagicStone ? MagicStone.createMagicStone(1) : UpgradeItems.createUpgradeStone(1);

                // 무작위로 선택된 빈 슬롯에 아이템을 배치합니다.
                int slot = emptySlots.remove(0);
                giftBox.setItem(slot, rewardItem);
            }
        }

        if (!clanManager.getAllClans().isEmpty()) {
            Bukkit.broadcastMessage("§d[선물상자] §b모든 가문의 선물상자에 새로운 보급품이 도착했습니다. §f(파일런에서 확인)");
        }

        // 다음 리필 시간을 설정합니다.
        long cooldownMillis = TimeUnit.MINUTES.toMillis(configManager.getConfig().getLong("pylon.giftbox.cooldown-minutes", 5));
        nextRefillTime = System.currentTimeMillis() + cooldownMillis;
    }
}