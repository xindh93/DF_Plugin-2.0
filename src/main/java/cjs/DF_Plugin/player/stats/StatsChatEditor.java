package cjs.DF_Plugin.player.stats;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class StatsChatEditor {

    public static void sendEditor(Player editor, OfflinePlayer target, PlayerStats stats, String progress) {
        String targetName = target.getName() != null ? target.getName() : "알 수 없는 플레이어";

        editor.sendMessage(" ");
        editor.sendMessage(Component.text("§8§m                                                                                "));
        editor.sendMessage(Component.text("  §b§l스탯 평가 §8- §f" + targetName + " §7" + progress));
        editor.sendMessage(" ");

        for (StatType type : StatType.values()) {
            TextComponent.Builder builder = Component.text();
            builder.append(Component.text("  §6» §7" + type.getDisplayName() + ": "));
            for (int i = 1; i <= 5; i++) {
                final int value = i;
                boolean isSet = i <= stats.getStat(type);

                TextComponent star = Component.text(isSet ? "★" : "☆")
                        .color(isSet ? NamedTextColor.GOLD : NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.runCommand("/df admin setstat " + type.name().toLowerCase() + " " + value))
                        .hoverEvent(HoverEvent.showText(Component.text("§6" + value + "점§7으로 설정")));
                builder.append(star);
            }
            // Add a reset button
            builder.append(Component.space())
                    .append(Component.text("[초기화]")
                            .color(NamedTextColor.RED)
                            .clickEvent(ClickEvent.runCommand("/df admin setstat " + type.name().toLowerCase() + " 0"))
                            .hoverEvent(HoverEvent.showText(Component.text("§c점수를 0점으로 초기화합니다."))));

            editor.sendMessage(builder.build());
        }

        editor.sendMessage(" ");

        TextComponent confirmButton = Component.text("  §a§l[ ✓ 확인 및 다음 ]")
                .clickEvent(ClickEvent.runCommand("/df admin confirmstat"))
                .hoverEvent(HoverEvent.showText(Component.text("§a현재 평가를 저장하고 다음 대상으로 넘어갑니다.")));

        TextComponent cancelButton = Component.text("  §c§l[ × 평가 취소 ]")
                .clickEvent(ClickEvent.runCommand("/df admin cancelstat"))
                .hoverEvent(HoverEvent.showText(Component.text("§c진행 중인 모든 평가를 취소합니다.")));

        editor.sendMessage(confirmButton);
        editor.sendMessage(cancelButton);
        editor.sendMessage(Component.text("§8§m                                                                                "));
        editor.sendMessage(" ");
    }
}