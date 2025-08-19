package cjs.DF_Plugin.pylon.beacongui.giftbox;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.GameStartManager;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

public class GiftBoxRefillTask extends BukkitRunnable {

    private final DF_Main plugin;
    private final GameStartManager gameStartManager;

    public GiftBoxRefillTask(DF_Main plugin) {
        this.plugin = plugin;
        this.gameStartManager = plugin.getGameStartManager();
    }

    @Override
    public void run() {
        long currentTime = System.currentTimeMillis();

        // 1. 모든 가문의 선물상자를 리필하고, 리필 시간을 업데이트합니다.
        plugin.getClanManager().refillAllGiftBoxes(currentTime);

        // 2. 다음 리필 시간을 계산하고 저장합니다.
        long cooldownMinutes = plugin.getGameConfigManager().getConfig().getLong("pylon.giftbox.cooldown-minutes", 5);
        long cooldownMillis = cooldownMinutes * 60 * 1000;
        // 모든 가문의 타이머와 일관성을 유지하기 위해 동일한 currentTime을 사용합니다.
        gameStartManager.setNextGiftBoxRefillTime(currentTime + cooldownMillis);
        gameStartManager.saveState();
    }
}