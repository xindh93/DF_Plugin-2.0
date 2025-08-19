package cjs.DF_Plugin.util;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ActionBarManager {

    private final DF_Main plugin;
    private final SpecialAbilityManager specialAbilityManager;

    public ActionBarManager(DF_Main plugin, SpecialAbilityManager specialAbilityManager) {
        this.plugin = plugin;
        this.specialAbilityManager = specialAbilityManager;
        startUpdater();
    }

    private void startUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateActionBar(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L); // 0.25초마다 업데이트
    }

    private void updateActionBar(Player player) {
        List<String> parts = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        // 충전량이 있는 능력을 먼저 표시
        Map<String, SpecialAbilityManager.ChargeInfo> charges = specialAbilityManager.getPlayerCharges(player.getUniqueId());
        if (charges != null) {
            for (Map.Entry<String, SpecialAbilityManager.ChargeInfo> entry : charges.entrySet()) {
                String abilityKey = entry.getKey();
                SpecialAbilityManager.ChargeInfo info = entry.getValue();

                // 액션바에 표시하지 않도록 설정되었거나, 현재 보이지 않는 상태의 능력은 건너뜁니다.
                ISpecialAbility ability = specialAbilityManager.getRegisteredAbility(abilityKey);
                if (ability == null || !ability.showInActionBar() || !info.visible()) {
                    continue;
                }

                // 점(dot) 형태로 충전량 시각화
                StringBuilder chargeDisplay = new StringBuilder();
                chargeDisplay.append("§a"); // 사용 가능한 횟수는 녹색
                for (int i = 0; i < info.current(); i++) {
                    chargeDisplay.append("●");
                }
                chargeDisplay.append("§7"); // 사용한 횟수는 회색
                for (int i = 0; i < info.max() - info.current(); i++) {
                    chargeDisplay.append("○");
                }

                parts.add(String.format("%s %s", info.displayName(), chargeDisplay.toString()));
            }
        }

        // 전체 쿨다운 중인 능력을 나중에 표시
        Map<String, SpecialAbilityManager.CooldownInfo> cooldowns = specialAbilityManager.getPlayerCooldowns(player.getUniqueId());
        if (cooldowns != null) {
            cooldowns.entrySet().stream()
                    .filter(entry -> entry.getValue().endTime() > currentTime) // 만료된 쿨다운은 제외
                    .forEach(entry -> {
                        String abilityKey = entry.getKey();
                        SpecialAbilityManager.CooldownInfo info = entry.getValue();

                        // 모드 변환 쿨다운이거나, 액션바에 표시하도록 설정된 능력일 경우에만 표시합니다.
                        boolean isModeSwitch = abilityKey.equals(SpecialAbilityManager.MODE_SWITCH_COOLDOWN_KEY);
                        ISpecialAbility ability = specialAbilityManager.getRegisteredAbility(abilityKey);
                        boolean isVisibleAbility = ability != null && ability.showInActionBar();

                        if (isModeSwitch || isVisibleAbility) {
                            long secondsLeft = (info.endTime() - currentTime + 999) / 1000;
                            parts.add(String.format("%s §7%d초", info.displayName(), secondsLeft));
                        }
                    });
        }

        if (!parts.isEmpty()) {
            String message = String.join("  §7|  ", parts);
            sendActionBar(player, message);
        }
    }

    public static void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }
}