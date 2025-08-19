package cjs.DF_Plugin.util;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ActionBarManager {

    private final SpecialAbilityManager specialAbilityManager;

    public ActionBarManager(DF_Main plugin, SpecialAbilityManager specialAbilityManager) {
        this.specialAbilityManager = specialAbilityManager;
        startUpdater(plugin);
    }

    private void startUpdater(DF_Main plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateActionBar(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L); // 0.1초마다 액션바 업데이트
    }

    private void updateActionBar(Player player) {
        UUID playerUUID = player.getUniqueId();
        List<String> parts = new ArrayList<>();

        // 쿨다운 정보 추가
        Map<String, SpecialAbilityManager.CooldownInfo> cooldowns = specialAbilityManager.getPlayerCooldowns(playerUUID);
        if (cooldowns != null) {
            cooldowns.forEach((key, info) -> {
                long remainingMillis = info.endTime() - System.currentTimeMillis();
                if (remainingMillis > 0) {
                    // 이 능력이 액션바에 표시되도록 설정되었는지 확인합니다.
                    ISpecialAbility ability = specialAbilityManager.getRegisteredAbility(key);
                    if (ability != null && !ability.showInActionBar()) {
                        return; // showInActionBar()가 false이면 건너뜁니다.
                    }

                    // 남은 시간을 초 단위 정수로 표시합니다.
                    int remainingSeconds = (int) Math.ceil(remainingMillis / 1000.0);
                    parts.add(String.format("%s: %d초", info.displayName(), remainingSeconds));
                }
            });
        }

        // 충전량 정보 추가
        Map<String, SpecialAbilityManager.ChargeInfo> charges = specialAbilityManager.getPlayerCharges(playerUUID);
        if (charges != null) {
            charges.forEach((key, info) -> {
                if (info.visible()) {
                    parts.add(formatCharges(info));
                }
            });
        }

        if (parts.isEmpty()) {
            return;
        }

        String message;
        if (parts.size() > 5) {
            // 5개 이상이면 두 줄로 나눕니다.
            int midPoint = (parts.size() + 1) / 2;
            String line1 = String.join("   ", parts.subList(0, midPoint));
            String line2 = String.join("   ", parts.subList(midPoint, parts.size()));
            message = line1 + "\n" + line2;
        } else {
            // 4개 이하면 한 줄로 표시합니다.
            message = String.join("   ", parts);
        }

        player.sendActionBar(Component.text(message));
    }

    private String formatCharges(SpecialAbilityManager.ChargeInfo info) {
        // 능력에 설정된 표시 유형에 따라 포맷을 결정합니다.
        if (info.displayType() == ISpecialAbility.ChargeDisplayType.FRACTION) {
            return String.format("%s %d/%d", info.displayName(), info.current(), info.max());
        }

        // 점(dot)으로 표시합니다.
        String displayName = info.displayName();
        String colorCode = "§e"; // 기본값: 노랑

        // 능력의 표시 이름에서 색상 코드를 추출합니다.
        if (displayName.length() >= 2 && displayName.charAt(0) == '§') {
            colorCode = displayName.substring(0, 2);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(displayName).append(" ");
        for (int i = 0; i < info.max(); i++) {
            sb.append(i < info.current() ? colorCode + "●" : "§7○");
        }
        return sb.toString();
    }
}