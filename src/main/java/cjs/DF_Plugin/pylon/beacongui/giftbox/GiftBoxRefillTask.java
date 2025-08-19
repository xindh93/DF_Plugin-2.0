package cjs.DF_Plugin.pylon.beacongui.giftbox;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.clan.Clan;
import cjs.DF_Plugin.clan.ClanManager;
import cjs.DF_Plugin.enchant.MagicStone;
import cjs.DF_Plugin.items.UpgradeItems;
import cjs.DF_Plugin.settings.GameConfigManager;
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

    public GiftBoxRefillTask(DF_Main plugin) {
        this.clanManager = plugin.getClanManager();
        this.configManager = plugin.getGameConfigManager();
    }

    @Override
    public void run() {
        long giftCooldownMillis = TimeUnit.HOURS.toMillis(configManager.getConfig().getInt("pylon.giftbox.cooldown-hours", 4));

        for (Clan clan : clanManager.getAllClans()) {
            long timeSinceLastGift = System.currentTimeMillis() - clan.getLastGiftBoxTime();

            if (timeSinceLastGift >= giftCooldownMillis) {
                // 리필 시간이 되면 인벤토리를 초기화하고 새 아이템을 채웁니다.
                Inventory giftBox = clanManager.getGiftBoxInventory(clan);
                giftBox.clear();

                // 최소/최대 세트 수를 기반으로 총 아이템 개수의 범위를 설정합니다.
                int minTotalAmount = configManager.getConfig().getInt("pylon.giftbox.min-reward-sets", 4) * 64;
                int maxTotalAmount = configManager.getConfig().getInt("pylon.giftbox.max-reward-sets", 8) * 64;
                int totalAmount = ThreadLocalRandom.current().nextInt(minTotalAmount, maxTotalAmount + 1);

                // 선물상자의 비어있는 모든 슬롯을 가져와 무작위로 섞습니다.
                List<Integer> emptySlots = new ArrayList<>();
                for (int i = 0; i < giftBox.getSize(); i++) {
                    emptySlots.add(i);
                }
                Collections.shuffle(emptySlots);

                int amountLeft = totalAmount;
                while (amountLeft > 0 && !emptySlots.isEmpty()) {
                    // 각 아이템 묶음(스택)마다 종류를 새로 결정합니다.
                    boolean isMagicStone = random.nextBoolean();
                    ItemStack rewardItemType = isMagicStone ? MagicStone.createMagicStone(1) : UpgradeItems.createUpgradeStone(1);

                    int stackSize = Math.min(amountLeft, rewardItemType.getMaxStackSize());
                    ItemStack stack = rewardItemType.clone();
                    stack.setAmount(stackSize);

                    // 무작위로 선택된 빈 슬롯에 아이템을 배치합니다.
                    int slot = emptySlots.remove(0);
                    giftBox.setItem(slot, stack);

                    amountLeft -= stackSize;
                }
                clan.setLastGiftBoxTime(System.currentTimeMillis());
                clan.broadcastMessage("§d[선물상자] §b가문 선물상자에 새로운 보급품이 도착했습니다. §f(파일런에서 확인)");
            }
        }
    }
}